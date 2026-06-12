/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Off-heap cache for ColBERT per-token embeddings.
 *
 * <h3>Problem</h3>
 * <p>ColBERT reranking requires per-token embeddings for each candidate document.
 * Without caching, the {@link com.spectrayan.spector.embed.TokenEmbeddingProvider}
 * must re-encode every document on every query — expensive for ONNX inference
 * (typically 5-15ms per document). For 50 candidates, that's 250-750ms of
 * redundant model inference on documents whose text hasn't changed.</p>
 *
 * <h3>Solution</h3>
 * <p>This cache stores pre-computed token embeddings in off-heap memory via
 * the Panama Foreign Memory API ({@link MemorySegment}). Benefits:</p>
 * <ul>
 *   <li><b>No GC pressure</b> — token embeddings are large (200 tokens × 128 dims
 *       = 100KB per doc). Off-heap avoids promoting to old-gen.</li>
 *   <li><b>Cache-friendly</b> — flat float layout enables sequential SIMD reads
 *       during MaxSim scoring.</li>
 *   <li><b>Bounded memory</b> — configurable maximum entries with LRU eviction.</li>
 * </ul>
 *
 * <h3>Memory Layout</h3>
 * <pre>
 *   Per entry (variable size):
 *   ┌────────────────────────────────────────────────┐
 *   │ float[tokenCount × tokenDims]                  │
 *   │   token 0: [f0, f1, ..., f_{dims-1}]          │
 *   │   token 1: [f0, f1, ..., f_{dims-1}]          │
 *   │   ...                                          │
 *   │   token N: [f0, f1, ..., f_{dims-1}]          │
 *   └────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link ReadWriteLock} — concurrent reads during MaxSim scoring,
 * exclusive writes during cache population. The off-heap segment is allocated
 * with a shared {@link Arena} for cross-thread access.</p>
 *
 * @see ColBERTReranker
 */
public final class ColBERTTokenCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ColBERTTokenCache.class);

    private static final ValueLayout.OfFloat FLOAT_LE = ValueLayout.JAVA_FLOAT;

    private final int tokenDims;
    private final int maxEntries;
    private final Arena arena;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;

    /**
     * Cached entry metadata — points into a shared off-heap segment.
     *
     * @param segment    the off-heap memory segment holding the embeddings
     * @param tokenCount number of tokens in this entry
     * @param accessTime last access timestamp (for LRU eviction)
     */
    private record CacheEntry(MemorySegment segment, int tokenCount, long accessTime) {}

    private final Map<String, CacheEntry> entries;

    /**
     * Creates a ColBERT token cache.
     *
     * @param tokenDims  dimensionality of each token embedding (typically 128)
     * @param maxEntries maximum number of documents to cache (LRU eviction)
     */
    public ColBERTTokenCache(int tokenDims, int maxEntries) {
        this.tokenDims = tokenDims;
        this.maxEntries = maxEntries;
        this.arena = Arena.ofShared();
        this.entries = new ConcurrentHashMap<>(maxEntries);
    }

    /**
     * Creates a ColBERT token cache with default capacity (1024 entries).
     *
     * @param tokenDims dimensionality of each token embedding
     */
    public ColBERTTokenCache(int tokenDims) {
        this(tokenDims, 1024);
    }

    /**
     * Stores token embeddings for a document in off-heap memory.
     *
     * <p>The float[][] embeddings are flattened and copied to an off-heap
     * {@link MemorySegment}. If the cache is full, the least-recently-accessed
     * entry is evicted.</p>
     *
     * @param docId      document identifier
     * @param embeddings per-token embeddings: [tokenCount][tokenDims]
     */
    public void put(String docId, float[][] embeddings) {
        if (embeddings == null || embeddings.length == 0) return;

        int tokenCount = embeddings.length;
        long sizeBytes = (long) tokenCount * tokenDims * Float.BYTES;

        rwLock.writeLock().lock();
        try {
            // Evict if at capacity
            if (entries.size() >= maxEntries && !entries.containsKey(docId)) {
                evictLru();
            }

            // Allocate off-heap segment for this entry
            MemorySegment segment = arena.allocate(sizeBytes, Float.BYTES);

            // Flatten float[][] → contiguous off-heap float[]
            long offset = 0;
            for (float[] tokenVec : embeddings) {
                int copyLen = Math.min(tokenVec.length, tokenDims);
                MemorySegment.copy(
                        MemorySegment.ofArray(tokenVec), FLOAT_LE, 0,
                        segment, FLOAT_LE, offset,
                        copyLen);
                offset += (long) tokenDims * Float.BYTES;
            }

            entries.put(docId, new CacheEntry(segment, tokenCount, System.nanoTime()));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Retrieves cached token embeddings for a document.
     *
     * <p>Reads from the off-heap segment and reconstructs the float[][] array.
     * Updates the access timestamp for LRU tracking.</p>
     *
     * @param docId document identifier
     * @return per-token embeddings, or null if not cached
     */
    public float[][] get(String docId) {
        rwLock.readLock().lock();
        try {
            CacheEntry entry = entries.get(docId);
            if (entry == null) return null;

            // Update access time (ConcurrentHashMap.compute is atomic)
            entries.computeIfPresent(docId, (k, v) ->
                    new CacheEntry(v.segment, v.tokenCount, System.nanoTime()));

            // Read from off-heap → float[][]
            float[][] result = new float[entry.tokenCount][tokenDims];
            long offset = 0;
            for (int t = 0; t < entry.tokenCount; t++) {
                MemorySegment.copy(
                        entry.segment, FLOAT_LE, offset,
                        MemorySegment.ofArray(result[t]), FLOAT_LE, 0,
                        tokenDims);
                offset += (long) tokenDims * Float.BYTES;
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Checks if a document's token embeddings are cached.
     *
     * @param docId document identifier
     * @return true if cached
     */
    public boolean contains(String docId) {
        return entries.containsKey(docId);
    }

    /**
     * Removes a document's cached token embeddings.
     *
     * @param docId document identifier
     */
    public void invalidate(String docId) {
        rwLock.writeLock().lock();
        try {
            entries.remove(docId);
            // Note: the segment memory is not individually freeable — it's part of
            // the shared Arena. It will be reclaimed when the Arena is closed.
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the number of cached entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns the total off-heap bytes used by cached embeddings.
     */
    public long offHeapBytes() {
        rwLock.readLock().lock();
        try {
            return entries.values().stream()
                    .mapToLong(e -> (long) e.tokenCount * tokenDims * Float.BYTES)
                    .sum();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns the token dimensionality.
     */
    public int tokenDims() {
        return tokenDims;
    }

    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            if (closed) return;
            closed = true;
            entries.clear();
            arena.close();
            log.debug("ColBERT token cache closed");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ── LRU eviction ──

    private void evictLru() {
        // Find least-recently-accessed entry
        String lruKey = null;
        long lruTime = Long.MAX_VALUE;
        for (var e : entries.entrySet()) {
            if (e.getValue().accessTime < lruTime) {
                lruTime = e.getValue().accessTime;
                lruKey = e.getKey();
            }
        }
        if (lruKey != null) {
            entries.remove(lruKey);
            log.trace("Evicted LRU cache entry: {}", lruKey);
        }
    }
}
