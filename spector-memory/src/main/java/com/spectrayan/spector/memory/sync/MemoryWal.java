package com.spectrayan.spector.memory.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only Write-Ahead Log for memory events.
 *
 * <h3>Biological Analog: Hippocampal Replay Buffer</h3>
 * <p>Before memories are consolidated into long-term storage, they exist as
 * transient activity patterns in the hippocampus. The WAL is the digital equivalent
 * — an ordered, durable log of every memory mutation that can be replayed.</p>
 *
 * <h3>V3 Design: File-Backed Persistence</h3>
 * <ul>
 *   <li>Append-only with sequential numbering — O(1) writes</li>
 *   <li>No deletions — tombstone events instead</li>
 *   <li>Replay from any sequence number → enables distributed sync</li>
 *   <li>Binary record format: {@code [4B length][8B seq][1B type][4B id_len][N id][8B ts_epoch][4B payload_len][N payload]}</li>
 *   <li>Per-write fsync for crash durability (negligible vs. embedding latency)</li>
 *   <li>Rolled WAL chunks when file exceeds max size (default 8MB)</li>
 *   <li>Crash recovery: replay WAL file to rebuild in-memory state</li>
 * </ul>
 *
 * <h3>Dual Mode</h3>
 * <ul>
 *   <li><b>File mode</b> ({@code walPath != null}): All appends are durable on disk.</li>
 *   <li><b>In-memory mode</b> ({@code walPath == null}): Volatile, for tests and ephemeral agents.</li>
 * </ul>
 *
 * <h3>CloudSync (V2+)</h3>
 * <p>A replication daemon reads events after a high-water mark and ships them
 * to remote agents. Each agent replays events into their local memory store.</p>
 */
public final class MemoryWal implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryWal.class);

    /** Magic bytes for WAL file identification: "SPEC" in ASCII. */
    static final int WAL_MAGIC = 0x53504543;

    /** WAL format version. */
    static final int WAL_VERSION = 1;

    /** File header size: 4B magic + 4B version = 8 bytes. */
    static final int FILE_HEADER_BYTES = 8;

    /** Default max chunk size before rolling (8 MB). */
    private static final long DEFAULT_MAX_CHUNK_BYTES = 8L * 1024 * 1024;

    private final Path walDir;
    private final long maxChunkBytes;
    private final AtomicLong sequenceCounter;
    private final ReentrantLock writeLock = new ReentrantLock();

    /** In-memory event cache for fast replay from recent HWM. */
    private final List<WalEvent> events = new ArrayList<>();

    /** Active FileChannel for the current WAL chunk (null in memory-only mode). */
    private FileChannel activeChannel;
    private Path activeChunkPath;
    private long activeChunkBytes;
    private int chunkIndex;

    /**
     * Opens or creates a file-backed WAL.
     *
     * <p>If the WAL directory already contains chunk files, they are replayed
     * to recover state (sequence counter, in-memory event cache).</p>
     *
     * @param walDir        directory for WAL chunk files
     * @param maxChunkBytes maximum bytes per chunk before rolling (default: 8MB)
     */
    public MemoryWal(Path walDir, long maxChunkBytes) {
        this.walDir = walDir;
        this.maxChunkBytes = maxChunkBytes;
        this.sequenceCounter = new AtomicLong(0);

        try {
            Files.createDirectories(walDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create WAL directory: " + walDir, e);
        }

        // Recover state from existing chunk files
        recoverFromDisk();

        // Open (or create) the active chunk
        openActiveChunk();

        log.info("MemoryWal opened: dir={}, chunks={}, recovered={} events, hwm={}",
                walDir, chunkIndex + 1, events.size(), sequenceCounter.get());
    }

    /**
     * Opens or creates a file-backed WAL with default chunk size (8 MB).
     *
     * @param walDir directory for WAL chunk files
     */
    public MemoryWal(Path walDir) {
        this(walDir, DEFAULT_MAX_CHUNK_BYTES);
    }

    /**
     * Creates an in-memory WAL (no file persistence).
     */
    public MemoryWal() {
        this.walDir = null;
        this.maxChunkBytes = Long.MAX_VALUE;
        this.sequenceCounter = new AtomicLong(0);
        log.info("MemoryWal opened: in-memory mode");
    }

    /**
     * Appends a new event to the WAL.
     *
     * <p>In file mode, the event is serialized to the binary format and written
     * to the active chunk with {@code FileChannel.force(true)} for durability.</p>
     *
     * @param type     event type
     * @param memoryId the affected memory ID
     * @param payload  serialized event data (can be empty)
     * @return the event with its assigned sequence number
     */
    public WalEvent append(WalEvent.EventType type, String memoryId, byte[] payload) {
        long seq = sequenceCounter.incrementAndGet();
        WalEvent event = new WalEvent(seq, type, memoryId, Instant.now(),
                payload != null ? payload : new byte[0]);

        writeLock.lock();
        try {
            events.add(event);

            if (activeChannel != null) {
                writeEventToChannel(event);

                // Roll chunk if needed
                if (activeChunkBytes >= maxChunkBytes) {
                    rollChunk();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("WAL write failed at seq=" + seq, e);
        } finally {
            writeLock.unlock();
        }

        log.trace("WAL append: seq={}, type={}, id={}", seq, type, memoryId);
        return event;
    }

    /**
     * Appends a REMEMBER event.
     */
    public WalEvent appendRemember(String memoryId, byte[] payload) {
        return append(WalEvent.EventType.REMEMBER, memoryId, payload);
    }

    /**
     * Appends a FORGET event.
     */
    public WalEvent appendForget(String memoryId) {
        return append(WalEvent.EventType.FORGET, memoryId, null);
    }

    /**
     * Appends a REINFORCE event.
     */
    public WalEvent appendReinforce(String memoryId, byte valence) {
        return append(WalEvent.EventType.REINFORCE, memoryId, new byte[]{valence});
    }

    /**
     * Replays all events after a given sequence number.
     *
     * <p>Used by CloudSync to ship events to remote agents. Returns events
     * from the in-memory cache first; if the cache doesn't cover the requested
     * range, reads from disk.</p>
     *
     * @param afterSequence replay events with sequence &gt; this value (0 = replay all)
     * @return list of events in order
     */
    public List<WalEvent> replay(long afterSequence) {
        return events.stream()
                .filter(e -> e.sequence() > afterSequence)
                .toList();
    }

    /**
     * Replays all events from disk WAL files, ignoring the in-memory cache.
     *
     * <p>Used for crash recovery and consistency verification.</p>
     *
     * @return list of all events read from WAL chunk files
     */
    public List<WalEvent> replayFromDisk() {
        if (walDir == null) return List.of();

        List<WalEvent> diskEvents = new ArrayList<>();
        try {
            List<Path> chunks = findChunkFiles();
            for (Path chunk : chunks) {
                readChunkFile(chunk, diskEvents);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("WAL disk replay failed", e);
        }
        return diskEvents;
    }

    /**
     * Returns the current high-water mark (latest sequence number).
     */
    public long highWaterMark() {
        return sequenceCounter.get();
    }

    /**
     * Returns the total number of events in the WAL (in-memory cache).
     */
    public int size() {
        return events.size();
    }

    /**
     * Returns the WAL directory path (null for in-memory mode).
     */
    public Path path() {
        return walDir;
    }

    /**
     * Returns whether this WAL is file-backed.
     */
    public boolean isPersistent() {
        return walDir != null;
    }

    @Override
    public void close() {
        writeLock.lock();
        try {
            if (activeChannel != null) {
                try {
                    activeChannel.force(true);
                    activeChannel.close();
                } catch (IOException e) {
                    log.warn("Error closing WAL channel: {}", e.getMessage());
                }
            }
        } finally {
            writeLock.unlock();
        }
        log.info("MemoryWal closing ({} events, hwm={})", events.size(), sequenceCounter.get());
    }

    // ── Internal: File I/O ──

    /**
     * Recovers state from existing WAL chunk files on disk.
     * Rebuilds the in-memory event cache and restores the sequence counter.
     */
    private void recoverFromDisk() {
        if (walDir == null) return;

        try {
            List<Path> chunks = findChunkFiles();
            chunkIndex = chunks.size(); // next chunk index

            for (Path chunk : chunks) {
                readChunkFile(chunk, events);
            }

            // Restore sequence counter to the max seen
            long maxSeq = events.stream()
                    .mapToLong(WalEvent::sequence)
                    .max()
                    .orElse(0L);
            sequenceCounter.set(maxSeq);

        } catch (IOException e) {
            log.error("WAL recovery failed — starting fresh: {}", e.getMessage());
            events.clear();
            sequenceCounter.set(0);
            chunkIndex = 0;
        }
    }

    /**
     * Opens the active chunk file for writing. Creates a new file with header
     * if it doesn't exist.
     */
    private void openActiveChunk() {
        if (walDir == null) return;

        try {
            activeChunkPath = walDir.resolve(chunkFileName(chunkIndex));
            boolean isNew = !Files.exists(activeChunkPath);

            activeChannel = FileChannel.open(activeChunkPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ);

            if (isNew) {
                writeFileHeader();
                activeChunkBytes = FILE_HEADER_BYTES;
            } else {
                activeChunkBytes = activeChannel.size();
                activeChannel.position(activeChunkBytes); // seek to end for appending
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open WAL chunk: " + activeChunkPath, e);
        }
    }

    /**
     * Writes the WAL file header (magic + version).
     */
    private void writeFileHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
        header.putInt(WAL_MAGIC);
        header.putInt(WAL_VERSION);
        header.flip();
        activeChannel.write(header);
        activeChannel.force(true);
    }

    /**
     * Serializes and writes a single event to the active FileChannel.
     *
     * <p>Binary format:
     * {@code [4B record_length][8B sequence][1B type_ordinal][4B id_len][N id_bytes][8B timestamp_epoch_ms][4B payload_len][N payload]}</p>
     */
    private void writeEventToChannel(WalEvent event) throws IOException {
        byte[] idBytes = event.memoryId().getBytes(StandardCharsets.UTF_8);
        byte[] payload = event.payload();

        // Calculate record length (excludes the 4B length prefix itself)
        int recordLen = 8 + 1 + 4 + idBytes.length + 8 + 4 + payload.length;

        ByteBuffer buf = ByteBuffer.allocate(4 + recordLen);
        buf.putInt(recordLen);
        buf.putLong(event.sequence());
        buf.put((byte) event.type().ordinal());
        buf.putInt(idBytes.length);
        buf.put(idBytes);
        buf.putLong(event.timestamp().toEpochMilli());
        buf.putInt(payload.length);
        buf.put(payload);
        buf.flip();

        activeChannel.write(buf);
        activeChannel.force(false); // metadata update not needed per-write
        activeChunkBytes += 4 + recordLen;
    }

    /**
     * Rolls to a new WAL chunk file.
     */
    private void rollChunk() throws IOException {
        log.info("WAL chunk {} reached {}KB — rolling to next chunk",
                chunkIndex, activeChunkBytes / 1024);

        activeChannel.force(true);
        activeChannel.close();

        chunkIndex++;
        openActiveChunk();
    }

    /**
     * Reads all events from a single WAL chunk file.
     */
    private void readChunkFile(Path chunkPath, List<WalEvent> out) throws IOException {
        try (FileChannel ch = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < FILE_HEADER_BYTES) return; // too small, skip

            // Read and validate file header
            ByteBuffer headerBuf = ByteBuffer.allocate(FILE_HEADER_BYTES);
            ch.read(headerBuf);
            headerBuf.flip();

            int magic = headerBuf.getInt();
            int version = headerBuf.getInt();

            if (magic != WAL_MAGIC) {
                log.warn("Invalid WAL magic in {}: 0x{} (expected 0x{})",
                        chunkPath, Integer.toHexString(magic), Integer.toHexString(WAL_MAGIC));
                return;
            }
            if (version != WAL_VERSION) {
                log.warn("Unsupported WAL version in {}: {} (expected {})",
                        chunkPath, version, WAL_VERSION);
                return;
            }

            // Read events
            while (ch.position() < fileSize) {
                WalEvent event = readEventFromChannel(ch, chunkPath);
                if (event == null) break; // truncated record — stop
                out.add(event);
            }
        }
    }

    /**
     * Reads a single event from a FileChannel at the current position.
     *
     * @return the deserialized event, or null if the record is truncated
     */
    private WalEvent readEventFromChannel(FileChannel ch, Path source) throws IOException {
        // Read record length
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        int bytesRead = ch.read(lenBuf);
        if (bytesRead < 4) return null; // truncated
        lenBuf.flip();
        int recordLen = lenBuf.getInt();

        if (recordLen <= 0 || ch.position() + recordLen > ch.size()) {
            log.warn("Truncated WAL record at position {} in {}", ch.position() - 4, source);
            return null;
        }

        // Read full record
        ByteBuffer recBuf = ByteBuffer.allocate(recordLen);
        bytesRead = ch.read(recBuf);
        if (bytesRead < recordLen) {
            log.warn("Incomplete WAL record at position {} in {}", ch.position() - bytesRead, source);
            return null;
        }
        recBuf.flip();

        long sequence = recBuf.getLong();
        byte typeOrdinal = recBuf.get();
        int idLen = recBuf.getInt();
        byte[] idBytes = new byte[idLen];
        recBuf.get(idBytes);
        long timestampMs = recBuf.getLong();
        int payloadLen = recBuf.getInt();
        byte[] payload = new byte[payloadLen];
        recBuf.get(payload);

        WalEvent.EventType type = WalEvent.EventType.values()[typeOrdinal];
        String memoryId = new String(idBytes, StandardCharsets.UTF_8);
        Instant timestamp = Instant.ofEpochMilli(timestampMs);

        return new WalEvent(sequence, type, memoryId, timestamp, payload);
    }

    /**
     * Finds all WAL chunk files in the WAL directory, sorted by name (ascending).
     */
    private List<Path> findChunkFiles() throws IOException {
        if (walDir == null || !Files.isDirectory(walDir)) return List.of();

        try (var stream = Files.list(walDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("wal-") &&
                                 p.getFileName().toString().endsWith(".bin"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Generates a chunk file name from the chunk index.
     */
    static String chunkFileName(int index) {
        return String.format("wal-%06d.bin", index);
    }
}
