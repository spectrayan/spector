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

import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.graph.DynamicGraphBuilder;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.PlanAdapterNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.PlannerNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.ResultEvaluatorNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.StepExecutorNode;
import com.spectrayan.spector.synapse.agent.graph.coordinator.nodes.SynthesizeNode;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
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
 * The coordinator meta-graph — orchestrates dynamic Plan-and-Execute workflow generation and execution.
 *
 * <h3>Graph Structure</h3>
 * <pre>
 * START → planner → step_executor → evaluator
 *             │           ▲            │
 *             │           │--NEXT_STEP-┤
 *             │           │--REPLAN---► plan_adapter ──► step_executor
 *             │           │
 *             └───────────┴--SYNTHESIZE/DONE ─────────► synthesizer ──► END
 * </pre>
 *
 * <p>The planner decomposes goals into sequenced PlanSteps. The step executor executes each step
 * sequentially via dynamic subgraphs or agent delegation. The evaluator assesses intermediate outputs,
 * triggering adaptive replanning on failure or routing to the synthesizer on completion.</p>
 */
public final class CoordinatorGraph {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorGraph.class);

    private static final String NODE_PLANNER       = "planner";
    private static final String NODE_STEP_EXECUTOR = "step_executor";
    private static final String NODE_EVALUATOR     = "evaluator";
    private static final String NODE_PLAN_ADAPTER  = "plan_adapter";
    private static final String NODE_SYNTHESIZER   = "synthesizer";

    private final CompiledGraph<CoordinatorState> graph;

    private CoordinatorGraph(CompiledGraph<CoordinatorState> graph) {
        this.graph = graph;
    }

    /**
     * Builds and compiles the coordinator graph.
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                          DynamicGraphBuilder dynamicBuilder,
                                          List<String> availableTools,
                                          CognitiveSoulService soulService) throws Exception {
        return create(llmBridge, dynamicBuilder, availableTools, soulService, null, 5);
    }

    /**
     * Builds and compiles the coordinator graph with configurable iteration limit.
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                          DynamicGraphBuilder dynamicBuilder,
                                          List<String> availableTools,
                                          CognitiveSoulService soulService,
                                          int maxIterations) throws Exception {
        return create(llmBridge, dynamicBuilder, availableTools, soulService, null, maxIterations);
    }

    /**
     * Builds and compiles the coordinator graph with agent delegation and iteration limits.
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                          DynamicGraphBuilder dynamicBuilder,
                                          List<String> availableTools,
                                          CognitiveSoulService soulService,
                                          AgenticChatGraph agenticChatGraph,
                                          int maxIterations) throws Exception {
        Objects.requireNonNull(llmBridge, "llmBridge");
        Objects.requireNonNull(dynamicBuilder, "dynamicBuilder");
        Objects.requireNonNull(soulService, "soulService");

        final int maxIter = maxIterations > 0 ? maxIterations : 5;

        var planner = new PlannerNode(llmBridge, availableTools, soulService);
        var stepExecutor = new StepExecutorNode(dynamicBuilder, llmBridge, soulService, agenticChatGraph);
        var evaluator = new ResultEvaluatorNode(llmBridge);
        var planAdapter = new PlanAdapterNode(llmBridge, availableTools, soulService);
        var synthesizer = new SynthesizeNode(llmBridge);

        var stateGraph = new StateGraph<>(CoordinatorState.SCHEMA, CoordinatorState::new)
                .addNode(NODE_PLANNER, node_async(planner))
                .addNode(NODE_STEP_EXECUTOR, node_async(stepExecutor))
                .addNode(NODE_EVALUATOR, node_async(evaluator))
                .addNode(NODE_PLAN_ADAPTER, node_async(planAdapter))
                .addNode(NODE_SYNTHESIZER, node_async(synthesizer))
                .addEdge(START, NODE_PLANNER)
                .addEdge(NODE_PLANNER, NODE_STEP_EXECUTOR)
                .addEdge(NODE_STEP_EXECUTOR, NODE_EVALUATOR)
                .addEdge(NODE_PLAN_ADAPTER, NODE_STEP_EXECUTOR)
                .addEdge(NODE_SYNTHESIZER, END)
                .addConditionalEdges(NODE_EVALUATOR,
                        edge_async(state -> {
                            int iteration = state.iteration();

                            // Guardrail against runaway loops
                            if (iteration >= maxIter) {
                                log.warn("[CoordinatorGraph] Max iterations ({}) reached, routing to SYNTHESIZE", maxIter);
                                return "synthesize";
                            }

                            String decision = state.decision();
                            if ("REPLAN".equalsIgnoreCase(decision)) {
                                return "replan";
                            }
                            if ("NEXT_STEP".equalsIgnoreCase(decision)) {
                                return "next_step";
                            }
                            return "synthesize";
                        }),
                        Map.of(
                                "next_step", NODE_STEP_EXECUTOR,
                                "replan", NODE_PLAN_ADAPTER,
                                "synthesize", NODE_SYNTHESIZER
                        )
                );

        var compiled = stateGraph.compile();
        log.info("[CoordinatorGraph] Compiled Plan-and-Execute graph successfully (maxIterations={})", maxIter);
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
     */
    public sealed interface CoordinatorResult
            permits CoordinatorResult.Success, CoordinatorResult.Failure {

        /** Successful execution with the generated answer. */
        record Success(String answer, int iterations) implements CoordinatorResult {}

        /** Failed execution with error details. */
        record Failure(String message, Throwable cause) implements CoordinatorResult {}
    }
}
