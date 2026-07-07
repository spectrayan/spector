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

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Agentic chat graph — a LangGraph4j {@link StateGraph} that implements
 * the ReAct (Reasoning + Acting) pattern for tool-augmented conversations.
 *
 * <h3>Graph Flow</h3>
 * <pre>
 *   START → agent → shouldUseTool? → tools → agent (loop)
 *                                  → END   (no tools needed)
 * </pre>
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>Agent Node</b> — sends messages to LLM via {@link LlmBridge}, receives
 *       either a text response or tool call requests.</li>
 *   <li><b>Tool Node</b> — executes tool calls via {@link ToolRegistry} and feeds
 *       results back to the agent node for the next reasoning step.</li>
 *   <li><b>Conditional Edge</b> — routes between tool execution and end based on
 *       whether the LLM response contains tool calls.</li>
 * </ul>
 */
@Component
public class AgenticChatGraph {

    private static final Logger log = LoggerFactory.getLogger(AgenticChatGraph.class);
    private static final String MESSAGES_KEY = "messages";
    private static final String AGENT_NODE = "agent";
    private static final String TOOLS_NODE = "tools";

    private final LlmBridge llmBridge;
    private final ToolRegistry toolRegistry;

    public AgenticChatGraph(LlmBridge llmBridge, ToolRegistry toolRegistry) {
        this.llmBridge = llmBridge;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Builds and compiles the agentic chat graph for a given agent soul.
     *
     * @param soul the agent identity providing system prompt and tool configuration
     * @return a compiled, ready-to-execute graph
     */
    public CompiledGraph<AgentState> compile(AgentSoul soul) {
        try {
            // Define channels — messages are accumulated via appender channel
            Map<String, Channel<?>> channels = Map.of(
                    MESSAGES_KEY, Channels.<ChatMessage>appender(ArrayList::new)
            );

            List<ToolSpecification> toolSpecs = resolveToolSpecs(soul);
            String systemPrompt = soul.systemPrompt() != null
                    ? soul.systemPrompt()
                    : "You are a helpful AI assistant with access to tools.";

            // Build node actions as NodeAction (sync) and wrap to async
            NodeAction<AgentState> agentAction =
                    state -> agentNode(state, systemPrompt, toolSpecs);
            NodeAction<AgentState> toolAction = this::toolNode;

            StateGraph<AgentState> graph = new StateGraph<>(channels, AgentState::new)
                    .addNode(AGENT_NODE, AsyncNodeAction.node_async(agentAction))
                    .addNode(TOOLS_NODE, AsyncNodeAction.node_async(toolAction))
                    .addEdge(START, AGENT_NODE)
                    .addConditionalEdges(AGENT_NODE,
                            AsyncEdgeAction.edge_async(this::shouldUseTool),
                            Map.of("tools", TOOLS_NODE, "end", END))
                    .addEdge(TOOLS_NODE, AGENT_NODE);

            CompiledGraph<AgentState> compiled = graph.compile();
            log.info("[AgenticChatGraph] Compiled graph for soul '{}' with {} tools",
                    soul.name(), toolSpecs.size());
            return compiled;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to compile agentic graph for soul '" + soul.name() + "'", e);
        }
    }

    /**
     * Executes a single user message through the agentic graph.
     *
     * @param soul    the agent identity
     * @param message the user's message
     * @return the agent's final response text
     */
    public String chat(AgentSoul soul, String message) {
        return chat(soul, message, AgentChatListener.NOOP);
    }

    /**
     * Executes a single user message with a streaming listener.
     *
     * @param soul     the agent identity
     * @param message  the user's message
     * @param listener callback for real-time streaming events
     * @return the agent's final response text
     */
    public String chat(AgentSoul soul, String message, AgentChatListener listener) {
        CompiledGraph<AgentState> compiled = compile(soul);

        try {
            listener.onThinking("Processing message...");

            // Seed state with the user message
            Map<String, Object> input = Map.of(
                    MESSAGES_KEY, List.of(UserMessage.from(message))
            );

            var result = compiled.invoke(input);
            @SuppressWarnings("unchecked")
            List<ChatMessage> messages = result.map(state ->
                    state.<List<ChatMessage>>value(MESSAGES_KEY)
                            .orElse(List.of()))
                    .orElse(List.of());

            // Extract the last assistant message
            String response = messages.reversed().stream()
                    .filter(m -> m instanceof AiMessage)
                    .map(m -> ((AiMessage) m).text())
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse("I couldn't generate a response.");

            listener.onContent(response);
            listener.onDone("Completed");
            return response;

        } catch (Exception e) {
            log.error("[AgenticChatGraph] Chat execution failed: {}", e.getMessage(), e);
            listener.onError(e.getMessage());
            return "Error during agentic processing: " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Graph Node Implementations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Agent node — sends accumulated messages to the LLM with tool specifications.
     * Returns the LLM's response (text or tool calls) appended to messages.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> agentNode(AgentState state,
                                          String systemPrompt,
                                          List<ToolSpecification> toolSpecs) {
        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        // Build the full message list with system prompt
        List<ChatMessage> fullMessages = new ArrayList<>();
        fullMessages.add(SystemMessage.from(systemPrompt));
        fullMessages.addAll(messages);

        log.debug("[AgenticChatGraph] Agent node — {} messages, {} tool specs",
                messages.size(), toolSpecs.size());

        // Call LLM with tool specifications
        ChatResponse response;
        if (!toolSpecs.isEmpty()) {
            response = llmBridge.chatModel().chat(
                    dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(fullMessages)
                            .toolSpecifications(toolSpecs)
                            .build()
            );
        } else {
            response = llmBridge.chatModel().chat(fullMessages);
        }

        AiMessage aiMessage = response.aiMessage();
        log.debug("[AgenticChatGraph] LLM response — text={}, toolCalls={}",
                aiMessage.text() != null ? aiMessage.text().length() + " chars" : "null",
                aiMessage.hasToolExecutionRequests() ? aiMessage.toolExecutionRequests().size() : 0);

        return Map.of(MESSAGES_KEY, List.of((ChatMessage) aiMessage));
    }

    /**
     * Tool node — executes all pending tool calls from the last AI message
     * and appends results back to the message history.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toolNode(AgentState state) {
        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        // Find the last AI message with tool calls
        AiMessage lastAi = messages.reversed().stream()
                .filter(m -> m instanceof AiMessage ai && ai.hasToolExecutionRequests())
                .map(m -> (AiMessage) m)
                .findFirst()
                .orElse(null);

        if (lastAi == null || !lastAi.hasToolExecutionRequests()) {
            log.warn("[AgenticChatGraph] Tool node called but no tool requests found");
            return Map.of();
        }

        List<ChatMessage> toolResults = new ArrayList<>();
        for (ToolExecutionRequest req : lastAi.toolExecutionRequests()) {
            log.info("[AgenticChatGraph] Executing tool: {} ({})", req.name(), req.arguments());

            String result = toolRegistry.executeTool(req);

            toolResults.add(ToolExecutionResultMessage.from(req, result));
            log.debug("[AgenticChatGraph] Tool '{}' → {} chars", req.name(), result.length());
        }

        return Map.of(MESSAGES_KEY, toolResults);
    }

    /**
     * Conditional edge — determines whether to route to tools or end.
     */
    @SuppressWarnings("unchecked")
    private String shouldUseTool(AgentState state) {
        List<ChatMessage> messages = state.<List<ChatMessage>>value(MESSAGES_KEY)
                .orElse(List.of());

        // Check if the last message is an AI message with tool calls
        if (!messages.isEmpty()) {
            ChatMessage last = messages.getLast();
            if (last instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                return "tools";
            }
        }
        return "end";
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Resolves tool specifications from the agent soul's tool list.
     * If the soul specifies tools, only those are included; otherwise all are used.
     */
    private List<ToolSpecification> resolveToolSpecs(AgentSoul soul) {
        if (soul.tools() != null && !soul.tools().isEmpty()) {
            return toolRegistry.forNames(soul.tools()).stream()
                    .map(tool -> toolRegistry.toolSpecifications().stream()
                            .filter(spec -> spec.name().equals(tool.name()))
                            .findFirst()
                            .orElse(null))
                    .filter(spec -> spec != null)
                    .toList();
        }
        return toolRegistry.toolSpecifications();
    }
}
