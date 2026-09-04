/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor.EpisodicRecord;
import com.spectrayan.spector.memory.kernel.layout.EpisodicLogLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.shape.AbstractAppendMemory;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Log-structured, variable-length episodic conversation store.
 *
 * <h3>Record Format</h3>
 * <p>Each record consists of a 64-byte episodic header (reinterpreted from
 * the standard synaptic header) followed by an inline CBOR document body:</p>
 * <pre>
 *   ┌──────────────────────────────────────────────┐
 *   │  64B Episodic Header                         │
 *   │  [version][flags/role][seq][ts][session_id]   │
 *   │  [...body_length at offset 56...]            │
 *   ├──────────────────────────────────────────────┤
 *   │  Variable CBOR Body (body_length bytes)      │
 *   │  raw bytes (serialized by synapse layer)     │
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Differences from {@link EpisodicRecordMemory}</h3>
 * <ul>
 *   <li>Variable-length records (no fixed stride) — extends {@link AbstractAppendMemory}</li>
 *   <li>No quantized vector payload — CBOR body replaces vector bytes</li>
 *   <li>Header fields reinterpreted for conversation semantics via {@link EpisodicFieldAccessor}</li>
 *   <li>No BM25/SPLADE indexing — content stays in-region, not in text.dat</li>
 *   <li>Session-indexed via {@link EpisodicSessionIndex} for O(1) pagination</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>All write operations are serialized by the inherited {@code appendLock} from
 * {@link AbstractAppendMemory}. Reads are lock-free via volatile-publish fencing
 * from the underlying mmap segment.</p>
 *
 * @since 1.3.0
 * @see EpisodicFieldAccessor
 * @see EpisodicSessionIndex
 * @see EpisodicLogLayout
 */
public final class EpisodicLogMemory extends AbstractAppendMemory<EpisodicLogLayout> {

    private static final Logger log = LoggerFactory.getLogger(EpisodicLogMemory.class);

    private final ReentrantLock writeLock = new ReentrantLock();

    // ── Constructors ──

    /**
     * Creates a volatile (heap-backed) episodic log for testing.
     *
     * @param capacityBytes total byte budget for the log region
     */
    public EpisodicLogMemory(long capacityBytes) {
        super(SystemMemoryId.EPISODIC.id(), EpisodicLogLayout.INSTANCE, 0, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic log with a default 16MB buffer.
     */
    public static EpisodicLogMemory heap() {
        return new EpisodicLogMemory(16 * 1024 * 1024L);
    }

    /**
     * Creates a volatile (heap-backed) episodic log with specified capacity.
     */
    public static EpisodicLogMemory heap(long capacityBytes) {
        return new EpisodicLogMemory(capacityBytes);
    }

    /**
     * Creates a bundle-backed episodic log from a pre-sliced region segment.
     *
     * @param arena        the shared arena from the owning bundle
     * @param regionSlice  the memory segment sliced from the bundle's master segment
     * @param bundlePath   the path to the bundle file (for diagnostics)
     * @param isNew        true if the region was just created
     */
    private EpisodicLogMemory(Arena arena, MemorySegment regionSlice,
                               java.nio.file.Path bundlePath, boolean isNew) {
        super(SystemMemoryId.EPISODIC.id(), EpisodicLogLayout.INSTANCE, 0,
              arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0),
              true, bundlePath, null, true);  // bundleManaged=true

        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    EpisodicLogLayout.INSTANCE.recordStride(),
                    EpisodicLogLayout.INSTANCE.layoutId(), now, now);
            log.info("EpisodicLogMemory initialized new bundle region in: {} ({}KB)",
                    bundlePath, regionSlice.byteSize() / 1024);
        } else {
            log.info("EpisodicLogMemory loaded from bundle region in: {} (cursor={}B)",
                    bundlePath, count);
        }
    }

    /**
     * Factory method for creating a bundle-backed episodic log.
     *
     * @param arena        the shared arena from the owning bundle
     * @param regionSlice  the memory segment sliced from the bundle's master segment
     * @param bundlePath   the path to the bundle file (for diagnostics)
     * @param isNew        true if the region was just created
     * @return a new bundle-backed EpisodicLogMemory
     */
    public static EpisodicLogMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicLogMemory(arena, regionSlice, bundlePath, isNew);
    }

    // ── Write path ──

    /**
     * Appends a conversation turn to the episodic log.
     *
     * <p>Writes a 64B episodic header followed by the CBOR body bytes.
     * Returns the byte offset of the header in the region (relative to
     * {@link #dataOffset()}).</p>
     *
     * <p>This method is thread-safe. Concurrent writes are serialized
     * by the internal write lock.</p>
     *
     * @param role        conversation role (USER, ASSISTANT, SYSTEM, etc.)
     * @param sequenceId  monotonic turn counter per session
     * @param timestampMs epoch milliseconds when the turn was created
     * @param sessionId   8B TSID hash identifying the conversation session
     * @param body        raw CBOR body bytes (serialized by the synapse layer)
     * @param modelId     LLM model registry ID
     * @param tokenIn     input token count
     * @param tokenOut    output token count
     * @param latencyMs   response generation latency in milliseconds
     * @param userId      user/tenant 8B TSID hash
     * @param soulVersion agent soul configuration version
     * @param modality    source modality (TEXT, IMAGE, AUDIO, VIDEO)
     * @return the byte offset of the written header (relative to dataOffset)
     * @throws IndexOutOfBoundsException if the region is full
     */
    public long appendTurn(ConversationRole role, int sequenceId,
                            long timestampMs, long sessionId,
                            byte[] body, short modelId,
                            int tokenIn, int tokenOut,
                            int latencyMs, long userId,
                            short soulVersion, SourceModality modality) {
        int bodyLength = (body != null) ? body.length : 0;
        int totalRecordSize = EncodingHeaderFields.HEADER_BYTES + bodyLength;

        writeLock.lock();
        try {
            long writeOffset = dataOffset() + count;

            // Bounds check
            if (writeOffset + totalRecordSize > segment().byteSize()) {
                throw new IndexOutOfBoundsException(
                        "Episodic log full: cursor=" + count + ", record=" + totalRecordSize
                                + ", capacity=" + (segment().byteSize() - dataOffset()));
            }

            // Write 64B episodic header
            EpisodicFieldAccessor.writeHeader(segment(), writeOffset,
                    role, sequenceId, timestampMs, sessionId,
                    bodyLength, modelId, tokenIn, tokenOut,
                    latencyMs, userId, soulVersion, modality);

            // Write CBOR body inline after the header
            if (body != null && body.length > 0) {
                MemorySegment.copy(
                        MemorySegment.ofArray(body), 0,
                        segment(), writeOffset + EncodingHeaderFields.HEADER_BYTES,
                        bodyLength);
            }

            long recordOffset = count;
            count += totalRecordSize;
            persistCount();

            return recordOffset;
        } finally {
            writeLock.unlock();
        }
    }

    // ── Read path ──

    /**
     * Reads an episodic record at the given byte offset (relative to dataOffset).
     *
     * @param offset      byte offset relative to {@link #dataOffset()}
     * @param includeBody if true, also reads the CBOR body bytes
     * @return the decoded episodic record
     */
    public EpisodicRecord readTurn(long offset, boolean includeBody) {
        long absoluteOffset = dataOffset() + offset;
        return EpisodicFieldAccessor.readRecord(segment(), absoluteOffset, includeBody);
    }

    /**
     * Reads multiple episodic records at the given byte offsets.
     *
     * @param offsets     byte offsets relative to {@link #dataOffset()}
     * @param includeBody if true, also reads the CBOR body bytes
     * @return list of decoded episodic records
     */
    public List<EpisodicRecord> readTurns(List<Long> offsets, boolean includeBody) {
        List<EpisodicRecord> records = new ArrayList<>(offsets.size());
        for (long offset : offsets) {
            long absoluteOffset = dataOffset() + offset;
            EpisodicRecord record = EpisodicFieldAccessor.readRecord(segment(), absoluteOffset, includeBody);
            if (!EncodingHeaderFields.isTombstoned(record.flags())) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Tombstones a record at the given byte offset.
     *
     * @param offset byte offset relative to {@link #dataOffset()}
     */
    public void tombstone(long offset) {
        long absoluteOffset = dataOffset() + offset;
        EpisodicFieldAccessor.tombstone(segment(), absoluteOffset);
    }

    /**
     * Marks a record as consolidated (reflected into SEMANTIC tier).
     *
     * @param offset byte offset relative to {@link #dataOffset()}
     */
    public void markConsolidated(long offset) {
        long absoluteOffset = dataOffset() + offset;
        EpisodicFieldAccessor.markConsolidated(segment(), absoluteOffset);
    }

    /**
     * Returns the current write cursor position (bytes consumed in the data area).
     * This is also the exclusive end offset for {@link EpisodicSessionIndex#rebuild}.
     */
    public long writePosition() {
        return count;
    }

    /**
     * Rebuilds the given session index from this store's mmap region.
     *
     * @param sessionIndex the session index to populate
     * @return the number of live records indexed
     */
    public int rebuildSessionIndex(EpisodicSessionIndex sessionIndex) {
        return sessionIndex.rebuild(segment(), dataOffset(), dataOffset() + count);
    }

    /**
     * Scans this episodic log from beginning to current cursor, collecting the relative offsets
     * of all live, non-consolidated turns (#446).
     *
     * @return list of offsets (relative to dataOffset) of unconsolidated turns
     */
    public List<Long> unconsolidatedTurnOffsets() {
        List<Long> offsets = new ArrayList<>();
        long base = dataOffset();
        long limit = base + count;
        long current = base;

        while (current + EncodingHeaderFields.HEADER_BYTES <= limit) {
            byte flags = segment().get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
            int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
            if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                break; // corrupt or incomplete entry
            }

            if (!EncodingHeaderFields.isTombstoned(flags) && !EncodingHeaderFields.isConsolidated(flags)) {
                offsets.add(current - base);
            }

            current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
        }

        return offsets;
    }

    /**
     * Returns the number of bytes available for new records.
     */
    public long remainingBytes() {
        return segment().byteSize() - dataOffset() - count;
    }
}
