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
package com.spectrayan.spector.synapse.agent.chat.api;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatConfig;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ModelsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionMessagesResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolsResponse;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import com.spectrayan.spector.synapse.config.FeatureGate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for autonomous agent chat.
 *
 * <p>Exposes the full agentic chat pipeline via HTTP endpoints.
 * All request/response types use proper Java record DTOs from
 * {@link com.spectrayan.spector.synapse.agent.chat.dto.ChatDto}.</p>
 *
 * <p>Gated by the {@code chatEnabled} feature flag — returns HTTP 404
 * when chat is disabled (e.g., Ollama not detected).</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/chat} — main agentic chat</li>
 *   <li>{@code GET /api/v1/chat/tools} — available tools</li>
 *   <li>{@code GET /api/v1/chat/sessions} — session listing</li>
 *   <li>{@code GET /api/v1/chat/sessions/{id}/messages} — session messages</li>
 *   <li>{@code GET /api/v1/chat/config} — chat configuration</li>
 *   <li>{@code GET /api/v1/chat/models} — available LLM models</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/chat")
@FeatureGate("chatEnabled")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final CognitiveSoulService soulService;

    public ChatController(ChatService chatService, CognitiveSoulService soulService) {
        this.chatService = chatService;
        this.soulService = soulService;
    }

    /**
     * Main agentic chat endpoint.
     *
     * <p>Mapped to both {@code POST /chat} and {@code POST /chat/agent}
     * for backward compatibility with the Cortex UI.</p>
     */
    @PostMapping({"", "/agent"})
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        String message = request.message();

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String sessionId = request.resolvedSessionId();
        String model = request.model();
        int contextDepth = request.contextDepth() != null ? request.contextDepth() : -1;

        // Load agent soul (use cognitive memory)
        AgentSoul soul = soulService.getEffectiveSoul(null);

        try {
            AgentChatResponse response = chatService.executeChat(
                    message, sessionId, model, soul,
                    contextDepth, request.messages(),
                    AgentChatListener.NOOP);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[ChatController] Chat failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Lists available agent tools.
     */
    @GetMapping("/tools")
    public ToolsResponse listTools() {
        return chatService.listTools();
    }

    /**
     * Lists recent chat sessions.
     */
    @GetMapping("/sessions")
    public SessionsResponse listSessions(@RequestParam(defaultValue = "10") int limit) {
        var sessions = chatService.listSessions(limit);
        return new SessionsResponse(sessions, sessions.size() >= limit);
    }

    /**
     * Loads messages for a specific session.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<SessionMessagesResponse> loadSessionMessages(
            @PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new SessionMessagesResponse(
                chatService.loadSessionMessages(sessionId), sessionId));
    }

    /**
     * Returns chat configuration.
     */
    @GetMapping("/config")
    public ChatConfig chatConfig() {
        return chatService.chatConfig();
    }

    /**
     * Lists available LLM models.
     */
    @GetMapping("/models")
    public ModelsResponse listModels() {
        return chatService.listModels();
    }
}
