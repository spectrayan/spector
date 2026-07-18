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
package com.spectrayan.spector.synapse.agent.graph.coordinator.nodes;

import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.graph.coordinator.CoordinatorState;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EXECUTOR node — compiles the LLM-generated FlowSpec JSON into a subgraph
 * and executes it.
 *
 * <p>This is where the dynamic magic happens: the planner generates JSON,
 * this node compiles it into a real LangGraph4j StateGraph, runs it,
 * and feeds the result back into the coordinator state.</p>
 */
public final class SubgraphExecutorNode implements NodeAction<CoordinatorState> {

    private static final Logger log = LoggerFactory.getLogger(SubgraphExecutorNode.class);

    private final DynamicGraphBuilder dynamicBuilder;

    public SubgraphExecutorNode(DynamicGraphBuilder dynamicBuilder) {
        this.dynamicBuilder = Objects.requireNonNull(dynamicBuilder, "dynamicBuilder");
    }

    @Override
    public Map<String, Object> apply(CoordinatorState state) {
        String flowJson = state.flowSpecJson().orElse(null);
        if (flowJson == null || flowJson.isBlank()) {
            log.warn("[SubgraphExecutorNode] No flow spec JSON in state");
            return Map.of(
                    "execution_result", "ERROR: No flow spec JSON provided",
                    "status", "EVALUATING"
            );
        }

        log.info("[SubgraphExecutorNode] Compiling and executing dynamic subgraph");

        try {
            // Compile the LLM-generated JSON into a graph
            CompiledGraph<CognitiveState> subgraph = dynamicBuilder.buildFromJson(flowJson);

            // Execute with current state
            var result = subgraph.invoke(Map.of(
                    "query", state.query(),
                    "original_query", state.originalQuery()
            ));

            if (result.isPresent()) {
                CognitiveState subState = result.get();
                String answer = subState.answer().orElse("(no answer from subgraph)");
                List<String> subContext = subState.context();
                List<?> subChildResults = subState.value("child_results")
                        .map(o -> (List<?>) o).orElse(List.of());

                log.info("[SubgraphExecutorNode] Subgraph completed: answer={} chars, context={} entries, childResults={} entries",
                        answer.length(), subContext.size(), subChildResults.size());

                return Map.of(
                        "execution_result", answer,
                        "context", subContext,
                        "child_results", subChildResults,
                        "status", "EVALUATING"
                );
            }

            return Map.of(
                    "execution_result", "Subgraph returned empty state",
                    "status", "EVALUATING"
            );

        } catch (Exception e) {
            log.error("[SubgraphExecutorNode] Failed to execute subgraph", e);
            return Map.of(
                    "execution_result", "ERROR: " + e.getMessage(),
                    "status", "EVALUATING"
            );
        }
    }
}
