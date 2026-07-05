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
     */
    public static CoordinatorGraph create(LlmBridge llmBridge,
                                           DynamicGraphBuilder dynamicBuilder,
                                           List<String> availableTools) throws Exception {
        Objects.requireNonNull(llmBridge, "llmBridge");
        Objects.requireNonNull(dynamicBuilder, "dynamicBuilder");

        var planner = new PlannerNode(llmBridge, availableTools);
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
        log.info("[CoordinatorGraph] Compiled successfully");
        return new CoordinatorGraph(compiled);
    }

    /**
     * Executes a task through the dynamic coordinator pipeline.
     *
     * @param task the task description
     * @return the final answer
     */
    public String execute(String task) {
        log.info("[CoordinatorGraph] Executing task: '{}'", task);

        try {
            Optional<CoordinatorState> result = graph.invoke(Map.of(
                    "task", task,
                    "query", task,
                    "original_query", task
            ));

            if (result.isPresent()) {
                String answer = result.get().answer().orElse("No answer produced");
                log.info("[CoordinatorGraph] Task completed: {} chars", answer.length());
                return answer;
            }

            return "Coordinator returned empty state";

        } catch (Exception e) {
            log.error("[CoordinatorGraph] Task execution failed", e);
            return "ERROR: " + e.getMessage();
        }
    }

    /** Returns the compiled graph for direct invocation or testing. */
    public CompiledGraph<CoordinatorState> compiledGraph() {
        return graph;
    }
}
