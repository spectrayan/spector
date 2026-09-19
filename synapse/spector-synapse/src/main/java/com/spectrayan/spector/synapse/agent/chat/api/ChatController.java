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
package com.spectrayan.spector.synapse.agent.chat.api;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.AgentChatResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatConfig;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionRecord;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatSessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ChatTurnView;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ModelsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.RenameSessionRequest;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionHistoryResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionSummary;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.SessionsResponse;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.ToolsResponse;
import com.spectrayan.spector.synapse.agent.chat.service.ChatService;
import com.spectrayan.spector.synapse.agent.chat.service.ChatTranscriptPort;
import com.spectrayan.spector.synapse.agent.graph.AgentChatListener;
import com.spectrayan.spector.synapse.config.FeatureGate;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * REST controller for autonomous agent chat and operational session management.
 *
 * <p>Exposes the full agentic chat pipeline and dual-plane operational session lifecycle
 * via HTTP endpoints. All request/response types use proper Java record DTOs from
 * {@link com.spectrayan.spector.synapse.agent.chat.dto.ChatDto}.</p>
 *
 * <p>Gated by the {@code chatEnabled} feature flag — returns HTTP 404
 * when chat is disabled (e.g., Ollama not detected).</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/chat} — main agentic chat</li>
 *   <li>{@code GET /api/v1/chat/tools} — available tools</li>
 *   <li>{@code GET /api/v1/chat/sessions} — operational session listing</li>
 *   <li>{@code PATCH /api/v1/chat/sessions/{id}} — rename operational session</li>
 *   <li>{@code DELETE /api/v1/chat/sessions/{id}} — delete operational session records</li>
 *   <li>{@code GET /api/v1/chat/sessions/{id}/messages} — structured turn history</li>
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
    private final ChatTranscriptPort transcriptPort;

    @Autowired
    public ChatController(ChatService chatService,
                          CognitiveSoulService soulService,
                          @Autowired(required = false) ChatTranscriptPort transcriptPort) {
        this.chatService = Objects.requireNonNull(chatService, "ChatService must not be null");
        this.soulService = Objects.requireNonNull(soulService, "CognitiveSoulService must not be null");
        this.transcriptPort = transcriptPort;
    }

    public ChatController(ChatService chatService, CognitiveSoulService soulService) {
        this(chatService, soulService, null);
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
     * Lists recent chat sessions from the operational execution plane.
     */
    @GetMapping("/sessions")
    public ResponseEntity<SessionsResponse> listSessions(@RequestParam(defaultValue = "10") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        List<SessionSummary> summaries;
        if (transcriptPort != null) {
            List<ChatSessionSummary> operationalSessions = transcriptPort.listSessions(boundedLimit);
            summaries = operationalSessions.stream()
                    .map(s -> new SessionSummary(
                            s.sessionId(),
                            s.preview(),
                            s.lastActivity() != null ? s.lastActivity().toString() : "",
                            s.messageCount()
                    ))
                    .toList();
        } else {
            summaries = chatService.listSessions(boundedLimit);
        }
        return ResponseEntity.ok(new SessionsResponse(summaries, summaries.size() >= boundedLimit));
    }

    /**
     * Renames an operational chat session title.
     */
    @PatchMapping("/sessions/{id}")
    public ResponseEntity<SessionSummary> renameSession(
            @PathVariable("id") String id,
            @RequestBody RenameSessionRequest request) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request == null || request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String trimmedId = id.trim();
        String trimmedTitle = request.title().trim();

        if (transcriptPort != null) {
            Optional<ChatSessionRecord> sessionOpt = transcriptPort.getSession(trimmedId);
            if (sessionOpt.isEmpty()) {
                throw new SynapseNotFoundException("ChatSession", trimmedId);
            }
            transcriptPort.renameSession(trimmedId, trimmedTitle);
            ChatSessionRecord updated = transcriptPort.getSession(trimmedId).orElseThrow();
            int turnCount = transcriptPort.countTurns(trimmedId);
            return ResponseEntity.ok(new SessionSummary(
                    updated.id(),
                    updated.title(),
                    updated.updatedAt().toString(),
                    turnCount
            ));
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Deletes an operational chat session, its turns, events, and checkpoints.
     *
     * <p>INVARIANT: Deletes operational plane records only. Does NOT delete or purge
     * long-term cognitive engrams from Spector Memory.</p>
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String trimmedId = id.trim();
        if (transcriptPort != null) {
            transcriptPort.deleteSession(trimmedId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Loads the full structured turn history for an operational chat session.
     *
     * <p>Replaces untyped message maps with rich {@link ChatTurnView} structures
     * matching the live SSE stream schema.</p>
     */
    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<SessionHistoryResponse> loadSessionHistory(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String trimmedId = id.trim();
        if (transcriptPort != null) {
            Optional<ChatSessionRecord> sessionOpt = transcriptPort.getSession(trimmedId);
            if (sessionOpt.isEmpty()) {
                throw new SynapseNotFoundException("ChatSession", trimmedId);
            }
            List<ChatTurnView> turns = transcriptPort.loadTurns(trimmedId);
            return ResponseEntity.ok(new SessionHistoryResponse(trimmedId, sessionOpt.get().title(), turns));
        } else {
            return ResponseEntity.ok(new SessionHistoryResponse(trimmedId, null, List.of()));
        }
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
