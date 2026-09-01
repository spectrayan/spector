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
package com.spectrayan.spector.memory.pipeline.reranker;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

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
 */
public final class ColBERTTokenCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ColBERTTokenCache.class);

    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final int DEFAULT_MAX_TOKENS = 512;

    private final int maxEntries;
    private final int maxTokensPerDoc;
    private final Arena arena;
    private final Map<String, CacheEntry> entries;
    private final ReadWriteLock rwLock;
    private volatile boolean closed;

    private record CacheEntry(MemorySegment segment, int tokenCount, int tokenDims, long accessTime) {}

    public ColBERTTokenCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_TOKENS);
    }

    public ColBERTTokenCache(int maxEntries, int maxTokensPerDoc) {
        this.maxEntries = maxEntries;
        this.maxTokensPerDoc = maxTokensPerDoc;
        this.arena = Arena.ofShared();
        this.entries = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        this.closed = false;
    }

    public float[][] get(String docId) {
        if (closed || docId == null) {
            return null;
        }

        rwLock.readLock().lock();
        try {
            CacheEntry entry = entries.get(docId);
            if (entry == null) {
                return null;
            }

            int tokenCount = entry.tokenCount();
            int tokenDims = entry.tokenDims();
            float[][] result = new float[tokenCount][tokenDims];

            for (int t = 0; t < tokenCount; t++) {
                for (int d = 0; d < tokenDims; d++) {
                    long byteOffset = (long) (t * tokenDims + d) * ValueLayout.JAVA_FLOAT.byteSize();
                    result[t][d] = entry.segment().get(ValueLayout.JAVA_FLOAT, byteOffset);
                }
            }

            entries.put(docId, new CacheEntry(entry.segment(), tokenCount, tokenDims, System.currentTimeMillis()));
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void put(String docId, float[][] embeddings) {
        if (closed || docId == null || embeddings == null || embeddings.length == 0) {
            return;
        }

        int tokenCount = Math.min(embeddings.length, maxTokensPerDoc);
        int tokenDims = embeddings[0].length;

        rwLock.writeLock().lock();
        try {
            if (entries.size() >= maxEntries && !entries.containsKey(docId)) {
                evictOldest();
            }

            long totalFloats = (long) tokenCount * tokenDims;
            MemorySegment segment = arena.allocate(totalFloats * ValueLayout.JAVA_FLOAT.byteSize(), 8);

            for (int t = 0; t < tokenCount; t++) {
                for (int d = 0; d < tokenDims; d++) {
                    long byteOffset = (long) (t * tokenDims + d) * ValueLayout.JAVA_FLOAT.byteSize();
                    segment.set(ValueLayout.JAVA_FLOAT, byteOffset, embeddings[t][d]);
                }
            }

            entries.put(docId, new CacheEntry(segment, tokenCount, tokenDims, System.currentTimeMillis()));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
            if (entry.getValue().accessTime() < oldestTime) {
                oldestTime = entry.getValue().accessTime();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            entries.remove(oldestKey);
        }
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            entries.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        rwLock.writeLock().lock();
        try {
            closed = true;
            entries.clear();
            arena.close();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
