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
package com.spectrayan.spector.memory.pathway.pipeline.reranker;

import com.spectrayan.spector.kernel.scratch.DefaultTokenVectorTable;
import com.spectrayan.spector.kernel.scratch.TokenVectorTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Off-heap cache facade for ColBERT per-token embeddings (R8.3).
 *
 * <p>Rewritten against {@link TokenVectorTable} in {@code spector-kernel} to eliminate
 * all direct off-heap segment allocations, {@code Arena} ownership, and Panama dependencies
 * from the cognitive layer.</p>
 */
public final class ColBERTTokenCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ColBERTTokenCache.class);

    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_DIMS = 128;

    private final TokenVectorTable tokenTable;
    private volatile boolean closed;

    /**
     * Constructs a cache backed by a managed kernel {@link TokenVectorTable}.
     *
     * @param tokenTable kernel token vector table
     */
    public ColBERTTokenCache(TokenVectorTable tokenTable) {
        this.tokenTable = Objects.requireNonNull(tokenTable, "tokenTable cannot be null");
        this.closed = false;
    }

    /**
     * Constructs a cache with default capacity and dimension limits.
     */
    public ColBERTTokenCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_TOKENS);
    }

    /**
     * Constructs a cache with specified entry and token capacity limits.
     *
     * @param maxEntries maximum document entries before LRU eviction
     * @param maxTokensPerDoc maximum tokens per document
     */
    public ColBERTTokenCache(int maxEntries, int maxTokensPerDoc) {
        this(new DefaultTokenVectorTable(maxEntries, maxTokensPerDoc, DEFAULT_DIMS));
    }

    /**
     * Constructs a cache with specified entry, token, and dimension limits.
     *
     * @param maxEntries maximum document entries before LRU eviction
     * @param maxTokensPerDoc maximum tokens per document
     * @param dims token vector dimension
     */
    public ColBERTTokenCache(int maxEntries, int maxTokensPerDoc, int dims) {
        this(new DefaultTokenVectorTable(maxEntries, maxTokensPerDoc, dims));
    }

    /**
     * Retrieves token vectors for the specified document ID.
     *
     * @param docId document identifier
     * @return token embedding array (tokens x dims) or {@code null} if not cached
     */
    public float[][] get(String docId) {
        if (closed || docId == null) {
            return null;
        }
        return tokenTable.get(docId);
    }

    /**
     * Retrieves token vectors into a pre-allocated destination buffer.
     *
     * @param docId document identifier
     * @param dest destination array
     * @return true if cached and copied, false otherwise
     */
    public boolean get(String docId, float[][] dest) {
        if (closed || docId == null || dest == null) {
            return false;
        }
        return tokenTable.get(docId, dest);
    }

    /**
     * Caches token vectors for the specified document ID.
     *
     * @param docId document identifier
     * @param embeddings array of per-token embeddings
     */
    public void put(String docId, float[][] embeddings) {
        if (closed || docId == null || embeddings == null || embeddings.length == 0) {
            return;
        }
        tokenTable.put(docId, embeddings);
    }

    /**
     * Number of document vector sets currently held in the cache.
     *
     * @return cache size
     */
    public int size() {
        return closed ? 0 : tokenTable.size();
    }

    /**
     * Off-heap bytes currently allocated by this cache.
     *
     * @return total allocated bytes
     */
    public long allocatedBytes() {
        return closed ? 0L : tokenTable.allocatedBytes();
    }

    /**
     * Clears all cached token vectors.
     */
    public void clear() {
        if (!closed) {
            tokenTable.clear();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            tokenTable.close();
        } catch (Exception e) {
            log.warn("Error closing TokenVectorTable: {}", e.getMessage());
        }
    }
}
