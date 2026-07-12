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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.graph.spec.AgentSpec;
import com.spectrayan.spector.synapse.agent.graph.spec.ConditionalEdgeSpec;
import com.spectrayan.spector.synapse.agent.graph.spec.EdgeSpec;
import com.spectrayan.spector.synapse.agent.graph.spec.FlowSpec;
import com.spectrayan.spector.synapse.agent.graph.spec.NodeSpec;

import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic graph builder — compiles {@link FlowSpec} JSON into executable LangGraph4j graphs.
 *
 * <p>Takes a declarative JSON specification and produces a compiled
 * {@code CompiledGraph} that can be invoked or embedded as a subgraph.</p>
 *
 * <h3>Node Type Resolution</h3>
 * <ul>
 *   <li>{@code AGENT} — creates an LLM call node using the referenced AgentSpec</li>
 *   <li>{@code TOOL} — looks up an {@link AgentTool} from the {@link ToolRegistry}</li>
 *   <li>{@code SUBGRAPH} — recursively compiles another FlowSpec as a subgraph</li>
 *   <li>{@code END} — terminal node (routes to LangGraph4j END)</li>
 * </ul>
 *
 * <h3>Spring AI Integration</h3>
 * <p>Uses {@link LlmBridge} for all LLM calls. The model used can be changed
 * dynamically by the user during chat sessions.</p>
 */
@Component
public final class DynamicGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicGraphBuilder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmBridge llmBridge;
    private final ToolRegistry toolRegistry;

    public DynamicGraphBuilder(LlmBridge llmBridge, ToolRegistry toolRegistry) {
        this.llmBridge = Objects.requireNonNull(llmBridge, "llmBridge");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
    }

    /**
     * Parses JSON and compiles into a graph.
     *
     * @param json FlowSpec JSON string
     * @return compiled graph ready for invocation
     */
    public CompiledGraph<CognitiveState> buildFromJson(String json) throws Exception {
        FlowSpec spec = MAPPER.readValue(json, FlowSpec.class);
        return build(spec);
    }

    /**
     * Compiles a FlowSpec into an executable LangGraph4j graph.
     *
     * @param spec the flow specification
     * @return compiled graph
     */
    public CompiledGraph<CognitiveState> build(FlowSpec spec) throws Exception {
        log.info("[DynamicGraphBuilder] Building flow '{}' ({}) with {} nodes, {} edges",
                spec.name(), spec.id(), spec.nodes().size(), spec.edges().size());

        var graph = new StateGraph<>(CognitiveState.SCHEMA, CognitiveState::new);

        // 1. Add nodes
        for (var entry : spec.nodes().entrySet()) {
            String nodeName = entry.getKey();
            NodeSpec nodeSpec = entry.getValue();

            if (nodeSpec.type() == NodeSpec.NodeType.END) {
                log.debug("[DynamicGraphBuilder] Skipping END node '{}'", nodeName);
                continue; // END nodes are handled by edge routing
            }

            NodeAction<CognitiveState> action = resolveNodeAction(nodeName, nodeSpec, spec);
            graph.addNode(nodeName, node_async(action));
            log.debug("[DynamicGraphBuilder] Added node '{}' (type={})", nodeName, nodeSpec.type());
        }

        // 2. Set entry point
        graph.addEdge(START, spec.entryPoint());

        // 3. Add simple edges
        for (EdgeSpec edge : spec.edges()) {
            String target = "END".equalsIgnoreCase(edge.to()) ? END : edge.to();

            if ("always".equals(edge.condition())) {
                graph.addEdge(edge.from(), target);
            }
        }

        // 4. Add conditional edges
        for (ConditionalEdgeSpec condEdge : spec.conditionalEdges()) {
            addConditionalEdge(graph, condEdge);
        }

        CompiledGraph<CognitiveState> compiled = graph.compile();
        log.info("[DynamicGraphBuilder] Flow '{}' compiled successfully", spec.name());
        return compiled;
    }

    // ── Node resolution ───────────────────────────────────────

    private NodeAction<CognitiveState> resolveNodeAction(String nodeName,
                                                          NodeSpec nodeSpec,
                                                          FlowSpec flowSpec) {
        return switch (nodeSpec.type()) {
            case AGENT -> createAgentNode(nodeName, nodeSpec, flowSpec);
            case TOOL -> createToolNode(nodeName, nodeSpec);
            case FUNCTION -> createFunctionNode(nodeName, nodeSpec);
            case SUBGRAPH -> createSubgraphNode(nodeName, nodeSpec);
            case END -> throw new IllegalStateException("END nodes should not be resolved");
        };
    }

    private NodeAction<CognitiveState> createAgentNode(String nodeName,
                                                        NodeSpec nodeSpec,
                                                        FlowSpec flowSpec) {
        String agentId = nodeSpec.agent() != null ? nodeSpec.agent() : nodeName;
        AgentSpec agentSpec = flowSpec.agentById(agentId);

        String systemPrompt;
        if (agentSpec != null && agentSpec.systemPrompt() != null) {
            systemPrompt = agentSpec.systemPrompt();
        } else {
            // Load default system prompt from classpath
            systemPrompt = loadPromptTemplate("agent-default-system");
        }

        // Resolve LLM configuration from the agent spec (if present)
        final var llmSpec = (agentSpec != null && agentSpec.llm() != null)
                ? agentSpec.llm() : null;

        final String prompt = systemPrompt;
        return state -> {
            String query = state.query();
            String context = String.join("\n", state.context());

            String fullPrompt = prompt + "\n\n=== Context ===\n" + context
                    + "\n\n=== User Query ===\n" + query;

            // Use per-agent LlmSpec if configured, otherwise default model
            String response = (llmSpec != null)
                    ? llmBridge.generate(fullPrompt, llmSpec)
                    : llmBridge.generate(fullPrompt);

            log.debug("[DynamicGraphBuilder] Agent '{}' generated {} chars (model={})",
                    nodeName, response.length(),
                    llmSpec != null ? llmSpec.model() : "default");

            return Map.of("answer", response);
        };
    }

    private NodeAction<CognitiveState> createToolNode(String nodeName, NodeSpec nodeSpec) {
        String toolName = nodeSpec.toolName() != null ? nodeSpec.toolName() : nodeName;

        return state -> {
            AgentTool tool = toolRegistry.get(toolName).orElse(null);
            if (tool == null) {
                String error = "Tool '" + toolName + "' not found in registry";
                log.warn("[DynamicGraphBuilder] {}", error);
                return Map.of("context", List.of("[tool_error:" + toolName + "] " + error));
            }

            // Merge static inputParams from spec with dynamic state query
            var mergedArgs = new LinkedHashMap<String, Object>();

            // Static params from the flow spec take priority
            if (nodeSpec.inputParams() != null && !nodeSpec.inputParams().isEmpty()) {
                mergedArgs.putAll(nodeSpec.inputParams());
            }

            // Add query from state if not already provided by inputParams
            if (!mergedArgs.containsKey("query")) {
                mergedArgs.put("query", state.query());
            }

            log.debug("[DynamicGraphBuilder] Tool '{}' executing with args: {}", toolName, mergedArgs);
            String result = tool.execute(mergedArgs);
            return Map.of("context", List.of("[tool:" + toolName + "] " + result));
        };
    }

    private NodeAction<CognitiveState> createFunctionNode(String nodeName, NodeSpec nodeSpec) {
        return state -> {
            log.info("[FunctionNode:{}] Executing with params: {}", nodeName, nodeSpec.inputParams());
            return Map.of();
        };
    }

    private NodeAction<CognitiveState> createSubgraphNode(String nodeName, NodeSpec nodeSpec) {
        return state -> {
            log.info("[SubgraphNode:{}] Executing subgraph: {}", nodeName, nodeSpec.subgraph());

            String subgraphJson = loadFlowJson(nodeSpec.subgraph());
            CompiledGraph<CognitiveState> subgraph = buildFromJson(subgraphJson);

            var result = subgraph.invoke(Map.of(
                    "query", state.query(),
                    "original_query", state.originalQuery(),
                    "context", state.context()
            ));

            if (result.isPresent()) {
                CognitiveState subState = result.get();
                var answer = subState.answer();
                if (answer.isPresent()) {
                    return Map.of(
                            "context", List.of("[subgraph:" + nodeName + "] " + answer.get()),
                            "answer", answer.get()
                    );
                }
            }
            return Map.of();
        };
    }

    // ── Conditional edges ─────────────────────────────────────

    private void addConditionalEdge(StateGraph<CognitiveState> graph,
                                    ConditionalEdgeSpec condEdge) throws Exception {
        String field = condEdge.conditionField();
        Map<String, String> mapping = condEdge.conditionMapping();
        String defaultTarget = condEdge.defaultTarget();

        graph.addConditionalEdges(condEdge.from(),
                edge_async(state -> {
                    String fieldValue = state.<String>value(field).orElse("");
                    String target = mapping.getOrDefault(fieldValue.toUpperCase(), defaultTarget);
                    return "END".equalsIgnoreCase(target) ? END : target;
                }),
                resolveEdgeMappings(mapping, defaultTarget)
        );
    }

    private Map<String, String> resolveEdgeMappings(Map<String, String> condMapping,
                                                     String defaultTarget) {
        var resolved = new LinkedHashMap<String, String>();
        for (var entry : condMapping.entrySet()) {
            String target = "END".equalsIgnoreCase(entry.getValue()) ? END : entry.getValue();
            resolved.put(entry.getKey(), target);
        }
        String defTarget = "END".equalsIgnoreCase(defaultTarget) ? END : defaultTarget;
        resolved.put(defaultTarget, defTarget);
        return resolved;
    }

    // ── Helpers ───────────────────────────────────────────────

    private String loadFlowJson(String flowId) {
        String path = "flows/" + flowId + ".json";
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Flow spec not found on classpath: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load flow spec: " + path, e);
        }
    }

    private String loadPromptTemplate(String name) {
        String path = "/prompts/" + name + ".txt";
        try (var is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[DynamicGraphBuilder] Failed to load prompt: {}", name);
        }
        return "You are an intelligent assistant. Answer the user's question thoughtfully.";
    }
}
