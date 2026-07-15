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
package com.spectrayan.spector.synapse.agent.chat.service;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatConfig;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ModelsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.OllamaModel;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TraceEvent;
import com.spectrayan.spector.synapse.agent.cognitive.ConversationReflector;
import com.spectrayan.spector.synapse.agent.cognitive.ConversationSummarizer;
import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import com.spectrayan.spector.synapse.agent.graph.AgenticChatGraph;
import com.spectrayan.spector.synapse.agent.service.IdentityPrimerService;
import com.spectrayan.spector.synapse.config.SynapseProperties;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;

import com.spectrayan.spector.memory.id.TsidGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-level service for executing cognitive chat turns via the agentic graph.
 *
 * <p>Orchestrates context priming, message assembly, agentic graph execution,
 * and session persistence. This is the central entry point for all chat
 * interactions in Spector Synapse.</p>
 *
 * <h3>Flow</h3>
 * <pre>
 *   1. Prime context (cross-session memory injection)
 *   2. Assemble message list (history + new message)
 *   3. Build enriched system prompt (agent soul + primed memories)
 *   4. Execute agentic graph (LLM + tool calls)
 *   5. Persist turn to session memory
 *   6. Return typed response with trace and metadata
 * </pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int DEFAULT_CONTEXT_DEPTH = 10;
    private static final String DEFAULT_MODEL = "qwen3.5:latest";

    private final ChatMemoryPort chatMemoryPort;
    private final ContextPrimingService contextPrimingService;
    private final IdentityPrimerService identityPrimerService;
    private final ToolRegistry toolRegistry;
    private final AgenticChatGraph agenticChatGraph;
    private final TsidGenerator tsid;
    private final String ollamaBaseUrl;

    /** Lazily initialized cognitive engines. */
    private final ConversationSummarizer summarizer;
    private final ConversationReflector reflector;

    public ChatService(ChatMemoryPort chatMemoryPort,
                       ContextPrimingService contextPrimingService,
                       IdentityPrimerService identityPrimerService,
                       ToolRegistry toolRegistry,
                       AgenticChatGraph agenticChatGraph,
                       TsidGenerator tsid,
                       SynapseProperties props) {
        this.chatMemoryPort = Objects.requireNonNull(chatMemoryPort);
        this.contextPrimingService = Objects.requireNonNull(contextPrimingService);
        this.identityPrimerService = Objects.requireNonNull(identityPrimerService);
        this.toolRegistry = Objects.requireNonNull(toolRegistry);
        this.agenticChatGraph = Objects.requireNonNull(agenticChatGraph);
        this.tsid = Objects.requireNonNull(tsid);
        this.ollamaBaseUrl = props.ollama().baseUrl();

        // Initialize cognitive engines
        this.summarizer = new ConversationSummarizer(
                props.ollama().baseUrl(), props.ollama().model());
        this.reflector = new ConversationReflector(
                props.ollama().baseUrl(), props.ollama().model(), toolRegistry);
        log.info("[ChatService] Cognitive engines initialized: Summarizer + Reflector");
    }

    // ═══════════════════════════════════════════════════════════════
    // Core Chat
    // ═══════════════════════════════════════════════════════════════

    /**
     * Executes a full agentic chat turn and returns a typed response.
     *
     * @param message        the user's message
     * @param sessionId      the session identifier (null for new sessions)
     * @param model          the LLM model to use (null for default)
     * @param soul           the agent soul for identity priming
     * @param contextDepth   cross-session memory recall depth
     * @param messages       optional pre-assembled message list
     * @param listener       callback for real-time streaming events
     * @return typed agent chat response with trace, metadata, and session info
     */
    public AgentChatResponse executeChat(
            String message,
            String sessionId,
            String model,
            AgentSoul soul,
            int contextDepth,
            List<Map<String, Object>> messages,
            AgentChatListener listener) {

        long startNanos = System.nanoTime();
        int depth = contextDepth > 0 ? contextDepth : DEFAULT_CONTEXT_DEPTH;
        boolean isNewSession = (sessionId == null || sessionId.isBlank());

        // ── Step 0: Load and compact session history if needed ──
        List<Map<String, Object>> history = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            history = new ArrayList<>(messages);
        } else if (sessionId != null && !sessionId.isBlank()) {
            history = new ArrayList<>(chatMemoryPort.loadSessionHistory(sessionId));
        }

        if (summarizer.needsCompaction(history)) {
            log.info("[ChatService] Compacting session {} history ({} messages)",
                    sessionId, history.size());
            history = summarizer.compact(history);
        }

        // Convert Map to ChatMessage
        List<ChatMessage> historyMessages = new ArrayList<>();
        for (var msg : history) {
            String role = String.valueOf(msg.getOrDefault("role", "user"));
            String content = String.valueOf(msg.getOrDefault("content", ""));
            if ("user".equalsIgnoreCase(role)) {
                historyMessages.add(UserMessage.from(content));
            } else if ("assistant".equalsIgnoreCase(role) || "agent".equalsIgnoreCase(role)) {
                historyMessages.add(AiMessage.from(content));
            } else if ("system".equalsIgnoreCase(role)) {
                historyMessages.add(SystemMessage.from(content));
            }
        }

        // ── Step 1: Prime context ──
        var primedContext = contextPrimingService.prime(message, sessionId, depth);

        // ── Step 2: Build enriched system prompt ──
        String basePrompt = identityPrimerService.buildSystemPrompt(soul);
        String enrichedPrompt = basePrompt;
        if (!primedContext.contextBlock().isEmpty()) {
            enrichedPrompt = basePrompt + "\n" + primedContext.contextBlock();
        }

        // Resolve session ID
        String resolvedSessionId = sessionId != null && !sessionId.isBlank()
                ? sessionId
                : tsid.generate();

        // ── Step 3: Build agentic soul with enriched prompt ──
        var soulBuilder = AgentSoul.builder()
                .id(soul != null ? soul.id() : "default")
                .name(soul != null ? soul.name() : "Assistant")
                .description(soul != null ? soul.description() : null)
                .systemPrompt(enrichedPrompt)
                .purpose(soul != null ? soul.purpose() : null)
                .personality(soul != null ? soul.personality() : null)
                .model(model != null ? model : DEFAULT_MODEL)
                .tools(soul != null ? soul.tools() : List.of())
                .createdAt(soul != null ? soul.createdAt() : java.time.Instant.now())
                .updatedAt(soul != null ? soul.updatedAt() : java.time.Instant.now());

        if (soul != null) {
            soulBuilder
                    .expertiseDomains(soul.expertiseDomains())
                    .coreValues(soul.coreValues())
                    .ethicalGuardrails(soul.ethicalGuardrails())
                    .emotionalBaseline(soul.emotionalBaseline())
                    .communicationStyle(soul.communicationStyle());
        }

        AgentSoul enrichedSoul = soulBuilder.build();

        // ── Step 4: Collect trace events ──
        List<TraceEvent> traceEvents = new CopyOnWriteArrayList<>();
        AgentChatListener wrappedListener = new AgentChatListener() {
            @Override public void onThinking(String thought) {
                traceEvents.add(new TraceEvent("thinking", thought));
                listener.onThinking(thought);
            }
            @Override public void onToolCall(String name, Map<String, Object> arguments) {
                traceEvents.add(new TraceEvent("tool_call", name));
                listener.onToolCall(name, arguments);
            }
            @Override public void onToolResult(String name, String result, boolean success) {
                traceEvents.add(new TraceEvent("tool_result", name + " -> " + result));
                listener.onToolResult(name, result, success);
            }
            @Override public void onContent(String text) {
                traceEvents.add(new TraceEvent("content", text));
                listener.onContent(text);
            }
            @Override public void onDone(String summary) {
                traceEvents.add(new TraceEvent("done", summary));
                listener.onDone(summary);
            }
            @Override public void onError(String error) {
                traceEvents.add(new TraceEvent("error", error));
                listener.onError(error);
            }
        };

        // ── Step 5: Execute agentic graph ──
        String finalResponse = agenticChatGraph.chat(enrichedSoul, historyMessages, message, wrappedListener);

        // ── Step 6: Persist the turn (skip on error) ──
        boolean hasErrorTrace = traceEvents.stream()
                .anyMatch(e -> "error".equals(e.type()));
        if (!hasErrorTrace && message != null && !message.isBlank() && !finalResponse.isEmpty()) {
            chatMemoryPort.saveToSession(resolvedSessionId, message, finalResponse,
                    model != null ? model : DEFAULT_MODEL);
        }

        // ── Step 7: Post-conversation reflection (async, skip on error) ──
        if (!hasErrorTrace && message != null && !message.isBlank() && !finalResponse.isEmpty()) {
            var conversationMessages = new ArrayList<Map<String, Object>>();
            conversationMessages.add(Map.of("role", "user", "content", message));
            conversationMessages.add(Map.of("role", "assistant", "content", finalResponse));
            reflector.reflectAsync(conversationMessages);
        }

        // ── Step 8: Build typed response ──
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        String status = hasErrorTrace ? "ERROR" : "DONE";

        return new AgentChatResponse(
                finalResponse,
                resolvedSessionId,
                isNewSession,
                model != null ? model : DEFAULT_MODEL,
                status,
                elapsedMs,
                elapsedMs,
                primedContext.crossSessionMemories().size(),
                traceEvents,
                null,
                List.of()
        );
    }

    /**
     * Simplified chat entry point.
     */
    public AgentChatResponse chat(String message, String sessionId,
                                   String model, AgentSoul soul) {
        return executeChat(message, sessionId, model, soul,
                DEFAULT_CONTEXT_DEPTH, null, AgentChatListener.NOOP);
    }

    // ═══════════════════════════════════════════════════════════════
    // Tools
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists available agent tools as function definitions.
     */
    public ToolsResponse listTools() {
        return new ToolsResponse(toolRegistry.toFunctionDefinitions());
    }

    // ═══════════════════════════════════════════════════════════════
    // Sessions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists recent chat sessions.
     */
    public List<SessionSummary> listSessions(int limit) {
        return chatMemoryPort.listSessions(limit).stream()
                .map(c -> new SessionSummary(
                        c.sessionId(), c.preview(),
                        c.lastActivityAt() != null ? c.lastActivityAt().toString() : "",
                        c.messageCount()))
                .toList();
    }

    /**
     * Loads all messages for a specific session.
     */
    public List<Map<String, Object>> loadSessionMessages(String sessionId) {
        return chatMemoryPort.loadSessionHistory(sessionId);
    }

    // ═══════════════════════════════════════════════════════════════
    // Models
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lists available LLM models from the Ollama API.
     */
    @SuppressWarnings("unchecked")
    public ModelsResponse listModels() {
        try {
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var body = mapper.readValue(resp.body(), Map.class);
                var models = (List<Map<String, Object>>) body.get("models");
                if (models != null) {
                    List<OllamaModel> result = models.stream()
                            .filter(m -> {
                                String name = String.valueOf(m.getOrDefault("name", ""));
                                return !name.contains("embed") && !name.contains("nomic");
                            })
                            .map(m -> {
                                String name = String.valueOf(m.getOrDefault("name", ""));
                                long size = m.get("size") instanceof Number n ? n.longValue() : 0;
                                String modifiedAt = String.valueOf(m.getOrDefault("modified_at", ""));
                                return new OllamaModel(name, name, size, modifiedAt,
                                        name.equals(DEFAULT_MODEL));
                            })
                            .toList();
                    return new ModelsResponse(result);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list Ollama models: {}", e.getMessage());
        }
        return new ModelsResponse(List.of(
                new OllamaModel(DEFAULT_MODEL, DEFAULT_MODEL, 0, "", true)));
    }

    /**
     * Returns chat configuration.
     */
    public ChatConfig chatConfig() {
        return new ChatConfig(DEFAULT_MODEL, 50, 10, true, "2.0-cognitive");
    }
}
