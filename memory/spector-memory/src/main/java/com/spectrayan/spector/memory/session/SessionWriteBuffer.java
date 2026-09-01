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
package com.spectrayan.spector.memory.session;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thread-safe ephemeral write buffer for a single session.
 * Stores recently added memories that might not be fully visible in the
 * underlying data store yet, providing read-your-writes consistency.
 */
public class SessionWriteBuffer {

    private static final int MAX_SIZE = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SESSION_BUFFER_MAX_SIZE;
    private static final long TTL_MS = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_SESSION_BUFFER_TTL_MS;

    private final ConcurrentLinkedDeque<BufferedEntry> entries = new ConcurrentLinkedDeque<>();

    public record BufferedEntry(String id, String text, float[] vector, MemoryType type, long timestamp, long addedAtMs) {}

    public void add(String id, String text, float[] vector, MemoryType type, long timestamp) {
        entries.addFirst(new BufferedEntry(id, text, vector, type, timestamp, System.currentTimeMillis()));
        while (entries.size() > MAX_SIZE) {
            entries.pollLast();
        }
    }

    public List<CognitiveResult> search(String queryText, float[] queryVector, RecallOptions options) {
        int k = options != null ? options.topK() : 10;
        float minScore = options != null ? options.minImportance() : 0.0f; // Simplified

        List<CognitiveResult> results = new ArrayList<>();
        for (BufferedEntry entry : entries) {
            float score = cosineSimilarity(queryVector, entry.vector());
            if (score >= minScore) {
                // Approximate representation of CognitiveResult for buffered entries
                results.add(new CognitiveResult(
                        entry.id(),
                        entry.text(),
                        score,
                        1.0f, // importance
                        0.0f, // ageDays
                        0,    // agentRecallCount
                        (byte) 0, // valence
                        entry.type(),
                        MemorySource.USER_STATED, // Simplified default
                        new String[0],
                        1.0f,
                        1.0f
                ));
            }
        }

        results.sort(Comparator.comparing(CognitiveResult::score).reversed());
        return results.stream().limit(k).collect(Collectors.toList());
    }

    public void evictConfirmed(Supplier<Integer> visibleCountSupplier) {
        long now = System.currentTimeMillis();
        entries.removeIf(e -> (now - e.addedAtMs()) > TTL_MS);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private float cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) return 0f;
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
