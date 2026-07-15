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
package com.spectrayan.spector.synapse.agent.graph.coordinator;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.PlannerNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.ResultEvaluatorNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.SubgraphExecutorNode;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The coordinator meta-graph — orchestrates dynamic workflow generation and execution.
 *
 * <h3>Graph Structure</h3>
 * <pre>
 * START → planner → executor → evaluator →(DONE)→ END
 *            ↑                               │
 *            └────── CONTINUE ───────────────┘
 * </pre>
 *
 * <p>The planner asks the LLM to generate a FlowSpec JSON. The executor compiles
 * and runs it as a subgraph. The evaluator decides if the task is complete or
 * needs another planning/execution cycle.</p>
 *
 * <h3>Spring AI Integration</h3>
 * <p>Uses {@link LlmBridge} for all LLM calls. The model can be changed
 * dynamically by the user during chat sessions.</p>
 */
public final class CoordinatorGraph {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorGraph.class);

    private static final String NODE_PLANNER   = "planner";
    private static final String NODE_EXECUTOR  = "executor";
    private static final String NODE_EVALUATOR = "evaluator";

    private final CompiledGraph<CoordinatorState> graph;

    private CoordinatorGraph(CompiledGraph<CoordinatorState> graph) {
        this.graph = graph;
    }

    /**
     * Builds and compiles the coordinator graph.
     *
     * @param llmBridge      LLM bridge for planner and evaluator calls
     * @param dynamicBuilder dynamic graph builder for subgraph compilation
     * @param availableTools list of available tool names
     * @return a new coordinator graph instance
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                           DynamicGraphBuilder dynamicBuilder,
                                           List<String> availableTools,
                                           com.spectrayan.spector.synapse.agent.service.CognitiveSoulService soulService) throws Exception {
        return create(llmBridge, dynamicBuilder, availableTools, soulService, 5);
    }

    /**
     * Builds and compiles the coordinator graph with configurable iteration limit.
     *
     * @param llmBridge      LLM bridge for planner and evaluator calls
     * @param dynamicBuilder dynamic graph builder for subgraph compilation
     * @param availableTools list of available tool names
     * @param soulService    cognitive soul service for retrieving agent identities
     * @param maxIterations  maximum planning-execution cycles before forced termination
     * @return a new coordinator graph instance
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                           DynamicGraphBuilder dynamicBuilder,
                                           List<String> availableTools,
                                           com.spectrayan.spector.synapse.agent.service.CognitiveSoulService soulService,
                                           int maxIterations) throws Exception {
        Objects.requireNonNull(llmBridge, "llmBridge");
        Objects.requireNonNull(dynamicBuilder, "dynamicBuilder");
        Objects.requireNonNull(soulService, "soulService");

        final int maxIter = maxIterations > 0 ? maxIterations : 5;

        var planner = new PlannerNode(llmBridge, availableTools, soulService);
        var executor = new SubgraphExecutorNode(dynamicBuilder);
        var evaluator = new ResultEvaluatorNode(llmBridge);

        var graph = new StateGraph<>(CoordinatorState.SCHEMA, CoordinatorState::new)
                .addNode(NODE_PLANNER, node_async(planner))
                .addNode(NODE_EXECUTOR, node_async(executor))
                .addNode(NODE_EVALUATOR, node_async(evaluator))
                .addEdge(START, NODE_PLANNER)
                .addEdge(NODE_PLANNER, NODE_EXECUTOR)
                .addEdge(NODE_EXECUTOR, NODE_EVALUATOR)
                .addConditionalEdges(NODE_EVALUATOR,
                        edge_async(state -> {
                            int iteration = state.iteration();

                            // Iteration guard — prevent infinite loops
                            if (iteration >= maxIter) {
                                log.warn("[CoordinatorGraph] Max iterations ({}) reached, " +
                                        "forcing DONE", maxIter);
                                return "done";
                            }

                            String decision = state.decision();
                            if ("DONE".equalsIgnoreCase(decision)) return "done";
                            return "continue";
                        }),
                        Map.of(
                                "done", END,
                                "continue", NODE_PLANNER
                        )
                );

        var compiled = graph.compile();
        log.info("[CoordinatorGraph] Compiled successfully (maxIterations={})", maxIter);
        return new CoordinatorGraph(compiled);
    }

    /**
     * Executes a task through the dynamic coordinator pipeline.
     *
     * @param task the task description
     * @return structured result containing answer or error details
     */
    public CoordinatorResult execute(String task) {
        log.info("[CoordinatorGraph] Executing task: '{}'", task);

        try {
            Optional<CoordinatorState> result = graph.invoke(Map.of(
                    "task", task,
                    "query", task,
                    "original_query", task
            ));

            if (result.isPresent()) {
                CoordinatorState state = result.get();
                String answer = state.answer().orElse("No answer produced");
                int iterations = state.iteration();
                log.info("[CoordinatorGraph] Task completed: {} chars, {} iterations",
                        answer.length(), iterations);
                return new CoordinatorResult.Success(answer, iterations);
            }

            return new CoordinatorResult.Failure("Coordinator returned empty state", null);

        } catch (Exception e) {
            log.error("[CoordinatorGraph] Task execution failed", e);
            return new CoordinatorResult.Failure(e.getMessage(), e);
        }
    }

    /** Returns the compiled graph for direct invocation or testing. */
    public CompiledGraph<CoordinatorState> compiledGraph() {
        return graph;
    }

    // ═══════════════════════════════════════════════════════════════
    // Result Type
    // ═══════════════════════════════════════════════════════════════

    /**
     * Structured result of a coordinator graph execution.
     *
     * <p>Callers can pattern-match on success/failure:</p>
     * <pre>{@code
     * switch (result) {
     *     case CoordinatorResult.Success s -> handleAnswer(s.answer());
     *     case CoordinatorResult.Failure f -> handleError(f.message());
     * }
     * }</pre>
     */
    public sealed interface CoordinatorResult
            permits CoordinatorResult.Success, CoordinatorResult.Failure {

        /** Successful execution with the generated answer. */
        record Success(String answer, int iterations) implements CoordinatorResult {}

        /** Failed execution with error details. */
        record Failure(String message, Throwable cause) implements CoordinatorResult {}
    }
}
