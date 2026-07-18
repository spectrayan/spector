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
package com.spectrayan.spector.synapse.agent.graph;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Public API for the LangGraph4j-based cognitive agent.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * var runner = new CognitiveGraphRunner(memory, llmBridge, toolRegistry);
 * CognitiveQueryResult result = runner.query("What do I know about X?");
 * System.out.println(result.answer());
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe. The compiled graph is immutable; each {@link #query} call
 * produces an independent execution with its own state.</p>
 */
public final class CognitiveGraphRunner {

    private static final Logger log = LoggerFactory.getLogger(CognitiveGraphRunner.class);

    private final CompiledGraph<CognitiveState> graph;

    /**
     * Creates a runner with a pre-compiled graph.
     */
    public CognitiveGraphRunner(CompiledGraph<CognitiveState> graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    /**
     * Convenience constructor that builds a default graph (topK=10, maxAttempts=3).
     */
    public CognitiveGraphRunner(SpectorMemory memory,
                                LlmBridge llmBridge,
                                ToolRegistry toolRegistry) {
        try {
            this.graph = new CognitiveGraphBuilder(memory, llmBridge, toolRegistry).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build cognitive graph", e);
        }
    }

    /**
     * Queries the cognitive agent with a user query.
     *
     * @param query the user's query
     * @return result containing the answer, context, and execution metadata
     */
    public CognitiveQueryResult query(String query) {
        Objects.requireNonNull(query, "query");
        log.info("[CognitiveGraphRunner] Starting query: '{}'", query);

        try {
            Optional<CognitiveState> finalState = graph.invoke(Map.of(
                    "query", query,
                    "original_query", query
            ));

            if (finalState.isEmpty()) {
                log.error("[CognitiveGraphRunner] Graph returned empty state");
                return CognitiveQueryResult.error(query, "Graph execution returned no result");
            }

            CognitiveState state = finalState.get();
            String answer = state.answer().orElse("No answer generated");
            List<String> context = state.context();
            int attempts = state.attempt();
            String finalQuery = state.query();

            log.info("[CognitiveGraphRunner] Completed in {} attempt(s), answer={} chars",
                    attempts, answer.length());

            return new CognitiveQueryResult(
                    answer,
                    context,
                    attempts,
                    finalQuery,
                    query,
                    null  // no error
            );

        } catch (Exception e) {
            log.error("[CognitiveGraphRunner] Graph execution failed for query: '{}'", query, e);
            return CognitiveQueryResult.error(query, e.getMessage());
        }
    }

    /**
     * Returns a Mermaid diagram representation of the graph for documentation.
     */
    public String getMermaidDiagram() {
        try {
            return graph.getGraph(
                    org.bsc.langgraph4j.GraphRepresentation.Type.MERMAID
            ).getContent();
        } catch (Exception e) {
            log.warn("[CognitiveGraphRunner] Failed to generate Mermaid diagram", e);
            return "<!-- diagram generation failed -->";
        }
    }

    /**
     * Creates a builder for customized graph construction.
     */
    public static CognitiveGraphBuilder builder(SpectorMemory memory,
                                                LlmBridge llmBridge,
                                                ToolRegistry toolRegistry) {
        return new CognitiveGraphBuilder(memory, llmBridge, toolRegistry);
    }

    // ══════════════════════════════════════════════════════════════
    // RESULT
    // ══════════════════════════════════════════════════════════════

    /**
     * Result of a cognitive agent query.
     *
     * @param answer          the generated answer
     * @param context         accumulated context entries from retrieval
     * @param retrievalRounds number of retrieval rounds performed
     * @param finalQuery      the final query used (may differ from original if refined)
     * @param originalQuery   the original user query
     * @param error           error message if execution failed, null otherwise
     */
    public record CognitiveQueryResult(
            String answer,
            List<String> context,
            int retrievalRounds,
            String finalQuery,
            String originalQuery,
            String error
    ) {
        /** Returns true if the query completed successfully. */
        public boolean isSuccess() {
            return error == null;
        }

        /** Creates an error result. */
        static CognitiveQueryResult error(String query, String errorMessage) {
            return new CognitiveQueryResult(
                    "Error: " + errorMessage,
                    List.of(),
                    0,
                    query,
                    query,
                    errorMessage
            );
        }
    }
}
