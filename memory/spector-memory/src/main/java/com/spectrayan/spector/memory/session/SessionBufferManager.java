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

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Manages active session write buffers, uncommitted write isolation, and search result merging.
 *
 * <p>Extracted from {@code DefaultSpectorMemory} as part of the god class decomposition.</p>
 *
 * @since 1.4.0
 */
public final class SessionBufferManager {

    private final ConcurrentHashMap<String, SessionWriteBuffer> sessionBuffers = new ConcurrentHashMap<>();

    public void add(String sessionId, String id, String text, float[] vector, MemoryType type, long timestamp) {
        if (sessionId != null) {
            sessionBuffers.computeIfAbsent(sessionId, k -> new SessionWriteBuffer())
                    .add(id, text, vector, type, timestamp);
        }
    }

    public List<CognitiveResult> merge(String sessionId,
                                       String queryText,
                                       RecallOptions options,
                                       List<CognitiveResult> primaryResults,
                                       EmbeddingProvider embeddingProvider,
                                       Supplier<Integer> visibleCountSupplier) {
        if (sessionId == null) {
            return primaryResults;
        }
        SessionWriteBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            return primaryResults;
        }
        buffer.evictConfirmed(visibleCountSupplier != null ? visibleCountSupplier : () -> 0);
        if (buffer.isEmpty()) {
            return primaryResults;
        }
        float[] queryVector = embeddingProvider.embed(queryText).vector();
        List<CognitiveResult> bufferedResults = buffer.search(queryText, queryVector, options);
        List<CognitiveResult> merged = new ArrayList<>(primaryResults);
        merged.addAll(bufferedResults);
        merged.sort(Comparator.comparing(CognitiveResult::score).reversed().thenComparing(CognitiveResult::id));
        return merged.stream().limit(options.topK()).collect(Collectors.toList());
    }

    public void clear() {
        sessionBuffers.clear();
    }
}
