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

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.nodes.EvaluateNode;
import com.spectrayan.spector.synapse.agent.graph.nodes.GenerateNode;
import com.spectrayan.spector.synapse.agent.graph.nodes.RetrieveNode;
import com.spectrayan.spector.synapse.agent.graph.nodes.ToolExecutionNode;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a LangGraph4j {@link StateGraph} for cognitive agentic RAG.
 *
 * <h3>Graph Structure</h3>
 * <pre>
 * START ──→ retrieve ──→ evaluate ──┬──→ generate ──→ END
 *               ↑                   │
 *               └──── REQUERY ──────┘
 * </pre>
 *
 * <h3>Spring AI Integration</h3>
 * <p>Uses {@link LlmBridge} (LangChain4j/Ollama) for LLM calls and
 * {@link ToolRegistry} (Spring component) for tool execution. The model
 * can be changed dynamically at runtime via the chat API.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * var builder = new CognitiveGraphBuilder(memory, llmBridge, toolRegistry);
 * CompiledGraph<CognitiveState> graph = builder.build();
 * }</pre>
 */
public final class CognitiveGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveGraphBuilder.class);

    // Node names — constants for graph definition and routing
    static final String NODE_RETRIEVE = "retrieve";
    static final String NODE_EVALUATE = "evaluate";
    static final String NODE_GENERATE = "generate";
    static final String NODE_TOOLS    = "tools";

    // Edge routing constants
    static final String ROUTE_GENERATE = "generate";
    static final String ROUTE_REQUERY  = "retrieve";
    static final String ROUTE_TOOLS    = "tools";

    private final SpectorMemory memory;
    private final LlmBridge llmBridge;
    private final ToolRegistry toolRegistry;
    private int topK;
    private int maxAttempts;
    private boolean enableCheckpointing;

    /**
     * Creates a builder with required dependencies.
     *
     * @param memory       Spector cognitive memory (6-phase scoring preserved)
     * @param llmBridge    LLM bridge for evaluation and generation
     * @param toolRegistry Spring-managed tool registry
     */
    public CognitiveGraphBuilder(SpectorMemory memory,
                                 LlmBridge llmBridge,
                                 ToolRegistry toolRegistry) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.topK = 10;
        this.maxAttempts = 3;
        this.enableCheckpointing = false;
    }

    /** Sets the number of results per retrieval. Default: 10. */
    public CognitiveGraphBuilder topK(int topK) {
        if (topK > 0) this.topK = topK;
        return this;
    }

    /** Sets the max retrieval attempts before forcing generation. Default: 3. */
    public CognitiveGraphBuilder maxAttempts(int maxAttempts) {
        if (maxAttempts > 0) this.maxAttempts = maxAttempts;
        return this;
    }

    /** Enables in-memory checkpointing for pause/resume/debug. */
    public CognitiveGraphBuilder withCheckpointing() {
        this.enableCheckpointing = true;
        return this;
    }

    /**
     * Builds and compiles the cognitive graph.
     *
     * @return compiled, immutable, executable graph
     * @throws Exception if graph validation fails
     */
    public CompiledGraph<CognitiveState> build() throws Exception {
        log.info("[CognitiveGraphBuilder] Building graph (topK={}, maxAttempts={}, tools={}, checkpointing={})",
                topK, maxAttempts, toolRegistry.names().size(), enableCheckpointing);

        // Create node actions
        var retrieveNode = new RetrieveNode(memory, topK);
        var evaluateNode = new EvaluateNode(llmBridge, new ArrayList<>(toolRegistry.names()));
        var generateNode = new GenerateNode(llmBridge);

        // Build the graph declaratively using LangGraph4j API
        var graphBuilder = new StateGraph<>(CognitiveState.SCHEMA, CognitiveState::new)
                .addNode(NODE_RETRIEVE, node_async(retrieveNode))
                .addNode(NODE_EVALUATE, node_async(evaluateNode))
                .addNode(NODE_GENERATE, node_async(generateNode));

        // Add tools node if tools are registered
        boolean hasTools = !toolRegistry.names().isEmpty();
        if (hasTools) {
            var toolNode = new ToolExecutionNode(toolRegistry);
            graphBuilder.addNode(NODE_TOOLS, node_async(toolNode));
        }

        // Define edges: START → retrieve → evaluate
        graphBuilder
                .addEdge(START, NODE_RETRIEVE)
                .addEdge(NODE_RETRIEVE, NODE_EVALUATE);

        // Conditional routing from evaluate node
        final int maxAtt = this.maxAttempts;

        graphBuilder.addConditionalEdges(NODE_EVALUATE,
                edge_async(state -> {
                    String decision = state.decision();
                    int attempt = state.attempt();

                    // Force generate if max attempts exceeded
                    if (attempt >= maxAtt) {
                        log.warn("[CognitiveGraph] Max attempts ({}) reached, forcing GENERATE", maxAtt);
                        return ROUTE_GENERATE;
                    }

                    if ("GENERATE".equalsIgnoreCase(decision)) {
                        return ROUTE_GENERATE;
                    }
                    if ("USE_TOOLS".equalsIgnoreCase(decision) && hasTools) {
                        return ROUTE_TOOLS;
                    }
                    // REQUERY or default → loop back to retrieve
                    return ROUTE_REQUERY;
                }),
                edgeMappings(hasTools)
        );

        // Tools → evaluate (re-evaluate after tool execution)
        if (hasTools) {
            graphBuilder.addEdge(NODE_TOOLS, NODE_EVALUATE);
        }

        // Generate → END
        graphBuilder.addEdge(NODE_GENERATE, END);

        // Compile with optional checkpointing
        CompiledGraph<CognitiveState> compiled;
        if (enableCheckpointing) {
            compiled = graphBuilder.compile(
                    CompileConfig.builder()
                            .checkpointSaver(new MemorySaver())
                            .build()
            );
        } else {
            compiled = graphBuilder.compile();
        }

        log.info("[CognitiveGraphBuilder] Graph compiled successfully");
        return compiled;
    }

    /**
     * Generates a Mermaid diagram of the graph for documentation.
     */
    public String generateMermaidDiagram() throws Exception {
        var graph = build();
        var representation = graph.getGraph(GraphRepresentation.Type.MERMAID);
        return representation.getContent();
    }

    // ── Internal helpers ─────────────────────────────────────────

    private Map<String, String> edgeMappings(boolean hasTools) {
        var map = new LinkedHashMap<String, String>();
        map.put(ROUTE_GENERATE, NODE_GENERATE);
        map.put(ROUTE_REQUERY, NODE_RETRIEVE);
        if (hasTools) {
            map.put(ROUTE_TOOLS, NODE_TOOLS);
        }
        return map;
    }
}
