package com.spectrayan.spector.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

import com.spectrayan.spector.config.SpectorMode;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.ingestion.FileIngestionService;
import com.spectrayan.spector.ingestion.IngestionResult;
import com.spectrayan.spector.memory.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Mode-aware ingestion service.
 *
 * <p>Handles all ingestion variants — raw text, single file, directory scan —
 * and routes to the engine or cognitive memory based on the global
 * {@link SpectorMode}.</p>
 *
 * <p>Uses {@link FileIngestionService} (from spector-ingestion) for file
 * discovery and chunking. That module stays a pure utility with no
 * dependency on the runtime.</p>
 *
 * <p>Obtained via {@code runtime.ingestion()}. Not instantiated directly.</p>
 */
public final class IngestionHandler {

    private static final Logger log = LoggerFactory.getLogger(IngestionHandler.class);

    private final SpectorEngine engine;
    private final SpectorMemory memory;  // nullable
    private final SpectorMode mode;

    IngestionHandler(SpectorEngine engine, SpectorMemory memory, SpectorMode mode) {
        this.engine = engine;
        this.memory = memory;
        this.mode = mode;
    }

    // ─────────────── Text Ingestion ───────────────

    /**
     * Ingests raw text content. Mode-aware.
     *
     * @param id   document/memory ID
     * @param text content text
     * @param tags optional tags (used in memory mode)
     */
    public void ingest(String id, String text, String... tags) {
        if (mode == SpectorMode.MEMORY && memory != null) {
            memory.remember(id, text, MemoryType.SEMANTIC, tags).join();
        } else {
            engine.ingest(id, text);
        }
    }

    /**
     * Ingests text with title metadata. Mode-aware.
     *
     * @param id    document/memory ID
     * @param title document title
     * @param text  content text
     * @param tags  optional tags (used in memory mode)
     */
    public void ingestWithTitle(String id, String title, String text, String... tags) {
        if (mode == SpectorMode.MEMORY && memory != null) {
            String enriched = title + "\n\n" + text;
            memory.remember(id, enriched, MemoryType.SEMANTIC, tags).join();
        } else {
            engine.ingest(id, title, text);
        }
    }

    /**
     * Ingests a long text by auto-chunking. Mode-aware.
     *
     * @param id      document ID
     * @param content full document content
     * @return number of chunks ingested
     */
    public int ingestChunked(String id, String content) {
        if (mode == SpectorMode.MEMORY && memory != null) {
            return ingestChunkedMemory(id, content);
        }
        return engine.ingestChunkedAuto(id, content);
    }

    // ─────────────── File Ingestion ───────────────

    /**
     * Ingests a single file. Reads, extracts title, chunks if needed. Mode-aware.
     *
     * @param file      path to the file
     * @param chunkSize chunk size in characters
     * @return ingestion result
     */
    public IngestionResult ingest(Path file, int chunkSize) {
        long start = System.currentTimeMillis();
        try {
            String content = Files.readString(file);
            if (content.isBlank()) {
                return IngestionResult.single(file.toString(), 0);
            }

            String id = file.getFileName().toString();
            String title = FileIngestionService.extractTitle(content, id);

            int chunks;
            if (content.length() <= chunkSize) {
                ingestWithTitle(id, title, content);
                chunks = 1;
            } else {
                chunks = ingestChunked(id, content);
            }

            return IngestionResult.chunked(id, chunks, List.of(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Failed to ingest file '{}': {}", file, e.getMessage());
            return IngestionResult.chunked(file.toString(), 0,
                    List.of(file.toString()), System.currentTimeMillis() - start);
        }
    }

    /**
     * Discovers and ingests files from a directory. Mode-aware.
     *
     * @param rootDir     root directory to scan
     * @param filePattern glob pattern (e.g., {@code "**\/*.md"})
     * @param chunkSize   chunk size in characters
     * @param chunkOverlap overlap between chunks
     * @param skipDirs    directories to skip (e.g., {@code ".git,.idea"})
     * @return list of ingestion results (one per file)
     */
    public List<IngestionResult> ingest(Path rootDir, String filePattern,
                                         int chunkSize, int chunkOverlap, String skipDirs) {
        return ingest(rootDir, filePattern, chunkSize, chunkOverlap, skipDirs, null);
    }

    /**
     * Discovers and ingests files from a directory. Mode-aware. With progress reporting.
     *
     * @param rootDir     root directory to scan
     * @param filePattern glob pattern (e.g., {@code "**\/*.md"})
     * @param chunkSize   chunk size in characters
     * @param chunkOverlap overlap between chunks
     * @param skipDirs    directories to skip (e.g., {@code ".git,.idea"})
     * @param progress    optional callback (fileIndex, totalFiles, relativePath, chunks, elapsedMs)
     * @return list of ingestion results (one per file)
     */
    public List<IngestionResult> ingest(Path rootDir, String filePattern,
                                         int chunkSize, int chunkOverlap, String skipDirs,
                                         IngestionProgress progress) {
        return ingest(rootDir, filePattern, chunkSize, chunkOverlap, skipDirs,
                progress, 4, 3, 2000);
    }

    /**
     * Discovers and ingests files from a directory. Mode-aware.
     * Uses virtual threads for parallelism and retry with exponential backoff.
     *
     * @param rootDir      root directory to scan
     * @param filePattern  glob pattern
     * @param chunkSize    chunk size in characters
     * @param chunkOverlap overlap between chunks
     * @param skipDirs     directories to skip
     * @param progress     optional progress callback
     * @param parallelism  max concurrent file ingestions (bounded by Semaphore)
     * @param maxRetries   max retry attempts per file
     * @param retryDelayMs base delay in ms for exponential backoff
     * @return list of ingestion results (one per file)
     */
    public List<IngestionResult> ingest(Path rootDir, String filePattern,
                                         int chunkSize, int chunkOverlap, String skipDirs,
                                         IngestionProgress progress,
                                         int parallelism, int maxRetries, int retryDelayMs) {
        var service = FileIngestionService.builder()
                .rootDirectory(rootDir)
                .filePattern(filePattern)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .skipDirs(skipDirs.split(","))
                .build();
        List<Path> files;
        try {
            files = service.discover();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to discover files in " + rootDir, e);
        }

        int totalFiles = files.size();
        log.info("[Ingestion] Discovered {} files in {} (pattern: {}, parallelism: {})",
                totalFiles, rootDir, filePattern, parallelism);

        // Semaphore bounds concurrency — ConcurrentTasks launches all tasks,
        // but only `parallelism` file ingestions run at a time
        var semaphore = new Semaphore(parallelism);
        var completedCount = new AtomicInteger(0);
        List<ConcurrentTasks.LabeledTask<IngestionResult>> tasks = new ArrayList<>(totalFiles);
        for (Path file : files) {
            String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
            tasks.add(new ConcurrentTasks.LabeledTask<>(relativePath, () -> {
                semaphore.acquire();
                try {
                    return ingestFileWithRetry(file, rootDir, chunkSize, maxRetries, retryDelayMs,
                            completedCount, totalFiles, progress);
                } finally {
                    semaphore.release();
                }
            }));
        }

        // Execute via ConcurrentTasks — uses StructuredTaskScope or ExecutorService
        // with configurable timeout per batch
        Duration timeout = Duration.ofMinutes(Math.max(totalFiles * 2L, 30));
        ConcurrentTasks.PartialResult<IngestionResult> partial;
        try {
            partial = ConcurrentTasks.forkJoinPartial(tasks, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ingestion interrupted");
            return List.of();
        }

        // Collect results — successes + synthetic failures for timed-out/failed tasks
        var results = new ArrayList<IngestionResult>(totalFiles);
        for (var entry : partial.successes()) {
            results.add(entry.result());
        }
        for (String timedOut : partial.timedOut()) {
            log.warn("[Ingestion] Timed out: {}", timedOut);
            if (progress != null) {
                progress.onFile(completedCount.incrementAndGet(), totalFiles,
                        timedOut, 0, -1, "Timed out");
            }
            results.add(IngestionResult.chunked(timedOut, 0, List.of(timedOut), -1));
        }
        for (var failure : partial.failures()) {
            log.error("[Ingestion] Failed: {} — {}", failure.label(), failure.cause().getMessage());
            if (progress != null) {
                progress.onFile(completedCount.incrementAndGet(), totalFiles,
                        failure.label(), 0, -1, failure.cause().getMessage());
            }
            results.add(IngestionResult.chunked(failure.label(), 0,
                    List.of(failure.label()), -1));
        }

        return results;
    }

    /**
     * Ingests a single file with retry logic (exponential backoff).
     */
    private IngestionResult ingestFileWithRetry(Path file, Path rootDir, int chunkSize,
                                                 int maxRetries, int retryDelayMs,
                                                 AtomicInteger completedCount, int totalFiles,
                                                 IngestionProgress progress) {
        String relativePath = rootDir.relativize(file).toString().replace('\\', '/');
        long fileStart = System.currentTimeMillis();

        if (progress != null) {
            progress.onFileStart(relativePath, totalFiles);
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String content = Files.readString(file);
                if (content.isBlank()) {
                    int idx = completedCount.incrementAndGet();
                    if (progress != null) {
                        progress.onFile(idx, totalFiles, relativePath, 0,
                                System.currentTimeMillis() - fileStart, null);
                    }
                    return IngestionResult.single(relativePath, 0);
                }

                String title = FileIngestionService.extractTitle(content, relativePath);
                int chunks;
                if (content.length() <= chunkSize) {
                    ingestWithTitle(relativePath, title, content);
                    chunks = 1;
                } else {
                    chunks = ingestChunked(relativePath, content);
                }

                long elapsed = System.currentTimeMillis() - fileStart;
                int idx = completedCount.incrementAndGet();
                log.info("  [{}] {} chunks, {}ms", relativePath, chunks, elapsed);
                if (progress != null) {
                    progress.onFile(idx, totalFiles, relativePath, chunks, elapsed, null);
                }
                return IngestionResult.chunked(relativePath, chunks, List.of(), elapsed);

            } catch (Exception e) {
                if (attempt < maxRetries) {
                    long delay = (long) retryDelayMs * (1L << (attempt - 1));
                    log.warn("  [{}] attempt {}/{} failed: {} — retrying in {}ms",
                            relativePath, attempt, maxRetries, e.getMessage(), delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    long elapsed = System.currentTimeMillis() - fileStart;
                    int idx = completedCount.incrementAndGet();
                    log.error("  [{}] all {} attempts failed: {}",
                            relativePath, maxRetries, e.getMessage());
                    if (progress != null) {
                        progress.onFile(idx, totalFiles, relativePath, 0, elapsed, e.getMessage());
                    }
                    return IngestionResult.chunked(relativePath, 0,
                            List.of(relativePath), elapsed);
                }
            }
        }

        // Shouldn't reach here, but safety net
        long elapsed = System.currentTimeMillis() - fileStart;
        return IngestionResult.chunked(relativePath, 0,
                List.of(relativePath), elapsed);
    }

    /**
     * Progress callback for directory ingestion.
     */
    public interface IngestionProgress {

        /** Called when a file starts processing (before embedding). */
        default void onFileStart(String relativePath, int totalFiles) {}

        /** Called when a file finishes processing (success or failure). */
        void onFile(int fileIndex, int totalFiles, String relativePath,
                    int chunks, long elapsedMs, String error);
    }

    // ─────────────── Count ───────────────

    /**
     * Returns the total number of indexed documents/memories.
     */
    public int count() {
        if (mode == SpectorMode.MEMORY && memory != null) {
            return memory.totalMemories();
        }
        return engine.documentCount();
    }

    private static final int CHUNK_BATCH_SIZE = 1;

    private int ingestChunkedMemory(String id, String content) {
        int chunkSize = 800;
        int overlap = 100;
        int count = 0;
        int start = 0;

        // Collect all chunks first
        var chunks = new ArrayList<String[]>(); // [chunkId, chunkText]
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(new String[]{id + "#chunk-" + count, content.substring(start, end)});
            count++;
            start = end - overlap;
            if (start >= content.length()) break;
        }

        // Process in batches to avoid socket exhaustion
        for (int i = 0; i < chunks.size(); i += CHUNK_BATCH_SIZE) {
            int batchEnd = Math.min(i + CHUNK_BATCH_SIZE, chunks.size());
            var batch = chunks.subList(i, batchEnd);

            var futures = batch.stream()
                    .map(c -> memory.remember(c[0], c[1], MemoryType.SEMANTIC))
                    .toArray(java.util.concurrent.CompletableFuture[]::new);

            java.util.concurrent.CompletableFuture.allOf(futures).join();
        }

        return count;
    }
}
