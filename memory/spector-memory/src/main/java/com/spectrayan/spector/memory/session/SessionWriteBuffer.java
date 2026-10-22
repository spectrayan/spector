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
package com.spectrayan.spector.memory.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;

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

        float alpha = options != null ? options.alpha() : 0.6f;
        float beta = options != null ? options.beta() : 0.4f;
        float tagRelevanceBoost = options != null ? options.tagRelevanceBoost() : 0.0f;

        List<CognitiveResult> results = new ArrayList<>();
        for (BufferedEntry entry : entries) {
            // computeSafe: buffered entries may carry a vector of a different dimension than the
            // query if the embedding model changed mid-session. Pre-migration behaviour was to
            // score such entries 0 and skip them, never to throw out of the recall path.
            float cosine = CosineSimilarity.computeSafe(queryVector, entry.vector());
            if (cosine < minScore) {
                continue;
            }
            // The reported score is the same linear blend the store path uses. Cosine stays
            // on its own field so a buffered hit cannot outrank a fused store hit.
            float similarity = Math.max(0.0f, cosine);
            float importance = 1.0f;
            float decay = 1.0f;
            float fused = CognitiveScoreFusionKernel.computeLinearBlendScore(
                    similarity, importance, decay, 0.0f, alpha, beta, tagRelevanceBoost);
            ScoreBreakdown breakdown = new ScoreBreakdown(
                    similarity, importance * decay, 1.0f, 1.0f, 1.0f, 1.0f, fused);
            results.add(new CognitiveResult(
                    entry.id(),
                    entry.text(),
                    fused,
                    importance,
                    0.0f, // ageDays
                    0,    // agentRecallCount
                    (byte) 0, // valence
                    entry.type(),
                    MemorySource.USER_STATED, // Simplified default
                    new String[0],
                    1.0f,
                    1.0f,
                    CognitiveResult.RetrievalMode.STANDARD,
                    breakdown
            ).withCosine(cosine));
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
}

