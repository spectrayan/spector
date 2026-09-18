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
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.security.injection.InjectionInterceptor;
import com.spectrayan.spector.synapse.security.pii.PiiInterceptor;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RETRIEVE node — queries Spector's cognitive memory with the current query.
 *
 * <p>Performs cognitive recall (preserving the 6-phase scoring pipeline) and
 * appends formatted results to the {@code context} channel. Increments the
 * {@code attempt} counter on each invocation.</p>
 */
public final class RetrieveNode implements NodeAction<CognitiveState> {

    private static final Logger log = LoggerFactory.getLogger(RetrieveNode.class);

    private final SpectorMemory memory;
    private final int topK;
    private final InjectionInterceptor injectionInterceptor;
    private final PiiInterceptor piiInterceptor;

    public RetrieveNode(SpectorMemory memory, int topK) {
        this(memory, topK, null, null);
    }

    public RetrieveNode(SpectorMemory memory) {
        this(memory, 10, null, null);
    }

    public RetrieveNode(SpectorMemory memory, int topK, InjectionInterceptor injectionInterceptor) {
        this(memory, topK, injectionInterceptor, null);
    }

    public RetrieveNode(SpectorMemory memory, int topK,
                        InjectionInterceptor injectionInterceptor,
                        PiiInterceptor piiInterceptor) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.topK = topK > 0 ? topK : 10;
        this.injectionInterceptor = injectionInterceptor;
        this.piiInterceptor = piiInterceptor;
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        String query = state.query();
        int attempt = state.attempt();

        log.info("[RetrieveNode] queryLength={}, attempt={}", query == null ? 0 : query.length(), attempt);

        RecallOptions options = RecallOptions.builder()
                .topK(topK)
                .build();

        List<CognitiveResult> results = memory.recall(query, options);
        log.debug("[RetrieveNode] Retrieved {} results", results.size());

        // Format results as context strings for the appender channel
        List<String> contextEntries = results.stream()
                .map(r -> String.format("[%s | score=%.3f | importance=%.2f] %s",
                        r.memoryType(), r.score(), r.importance(), r.text()))
                .collect(Collectors.toList());

        // Prompt-injection shield — filter poisoned RAG / memory chunks (#204)
        if (injectionInterceptor != null) {
            int before = contextEntries.size();
            contextEntries = injectionInterceptor.filterDocuments(contextEntries);
            if (contextEntries.size() < before) {
                log.warn("[RetrieveNode] Excluded {} document(s) due to prompt injection",
                        before - contextEntries.size());
            }
        }

        // PII redaction — mask before LLM context when a session is active (#203)
        if (piiInterceptor != null) {
            contextEntries = contextEntries.stream()
                    .map(piiInterceptor::redactUsingActiveSession)
                    .collect(Collectors.toList());
        }

        return Map.of(
                "context", contextEntries,
                "attempt", attempt + 1
        );
    }
}
