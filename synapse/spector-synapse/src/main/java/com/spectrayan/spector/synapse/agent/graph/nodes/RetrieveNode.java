/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;

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

    public RetrieveNode(SpectorMemory memory, int topK) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.topK = topK > 0 ? topK : 10;
    }

    public RetrieveNode(SpectorMemory memory) {
        this(memory, 10);
    }

    @Override
    public Map<String, Object> apply(CognitiveState state) {
        String query = state.query();
        int attempt = state.attempt();

        log.info("[RetrieveNode] query='{}', attempt={}", query, attempt);

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

        return Map.of(
                "context", contextEntries,
                "attempt", attempt + 1
        );
    }
}
