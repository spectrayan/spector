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
package com.spectrayan.spector.synapse.chat;

import com.spectrayan.spector.synapse.chat.ChatDto.ChatMessage;
import com.spectrayan.spector.synapse.bridge.LlmBridge;
import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.chat.ChatDto.ChatRequest;
import com.spectrayan.spector.synapse.chat.ChatDto.ChatResponse;
import com.spectrayan.spector.synapse.chat.ChatDto.SessionDetail;
import com.spectrayan.spector.synapse.chat.ChatDto.SessionSummary;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cognitive chat service — manages chat sessions and message persistence.
 *
 * <p>Bridges the chat REST API with the LLM provider and memory engine.
 * Messages are persisted to H2 via JdbcTemplate for session continuity.</p>
 *
 * <p>TODO: Wire to actual LLM provider for response generation.
 * Current implementation persists messages with placeholder responses.</p>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final JdbcTemplate jdbc;
    private final SynapseProperties props;
    private final LlmBridge llmBridge;
    private final MemoryBridge memoryBridge;

    public ChatService(JdbcTemplate jdbc, SynapseProperties props,
                       LlmBridge llmBridge, MemoryBridge memoryBridge) {
        this.jdbc = jdbc;
        this.props = props;
        this.llmBridge = llmBridge;
        this.memoryBridge = memoryBridge;
        log.info("[Chat] LLM: {} | MemoryBridge: {}",
                llmBridge.modelName(), memoryBridge.isAvailable() ? "active" : "stub");
    }

    /**
     * Send a chat message and get a response.
     */
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : createSession();
        String model = props.ollama().model();

        // Persist user message
        long userMsgId = persistMessage(sessionId, "user", request.message(), model);
        log.info("[Chat] User message in session {}: {} chars", sessionId, request.message().length());

        // Build context with memory recall if available
        String systemPrompt = buildSystemPrompt(request.message());
        String responseContent = llmBridge.generate(systemPrompt, request.message());

        // Persist assistant response
        long assistantMsgId = persistMessage(sessionId, "assistant", responseContent, model);

        // Update session preview
        String preview = request.message().length() > 100
                ? request.message().substring(0, 100) + "..."
                : request.message();
        jdbc.update("UPDATE chat_sessions SET preview = ?, updated_at = ? WHERE session_id = ?",
                preview, Instant.now(), sessionId);

        return new ChatResponse(sessionId, assistantMsgId, "assistant", responseContent, model, Instant.now());
    }

    /**
     * Create a new chat session.
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO chat_sessions (session_id, created_at, updated_at, model) VALUES (?, ?, ?, ?)",
                sessionId, now, now, props.ollama().model());
        log.info("[Chat] Created session: {}", sessionId);
        return sessionId;
    }

    /**
     * List all chat sessions.
     */
    public List<SessionSummary> listSessions() {
        return jdbc.query("""
                SELECT s.session_id, s.preview, s.model, s.created_at, s.updated_at,
                       (SELECT COUNT(*) FROM chat_messages m WHERE m.session_id = s.session_id) as msg_count
                FROM chat_sessions s ORDER BY s.updated_at DESC
                """,
                (rs, rowNum) -> new SessionSummary(
                        rs.getString("session_id"),
                        rs.getString("preview"),
                        rs.getString("model"),
                        rs.getInt("msg_count"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ));
    }

    /**
     * Get session detail with all messages.
     */
    public Optional<SessionDetail> getSession(String sessionId) {
        List<SessionDetail> results = jdbc.query(
                "SELECT * FROM chat_sessions WHERE session_id = ?",
                (rs, rowNum) -> {
                    List<ChatMessage> messages = getMessages(sessionId);
                    return new SessionDetail(
                            rs.getString("session_id"),
                            rs.getString("model"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            messages
                    );
                }, sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Get messages for a session.
     */
    public List<ChatMessage> getMessages(String sessionId) {
        return jdbc.query(
                "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
                (rs, rowNum) -> new ChatMessage(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("model"),
                        rs.getTimestamp("created_at").toInstant()
                ), sessionId);
    }

    /**
     * Delete a session and all its messages.
     */
    public boolean deleteSession(String sessionId) {
        int deleted = jdbc.update("DELETE FROM chat_sessions WHERE session_id = ?", sessionId);
        return deleted > 0;
    }

    private long persistMessage(String sessionId, String role, String content, String model) {
        jdbc.update("""
                INSERT INTO chat_messages (session_id, role, content, model, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, sessionId, role, content, model, Instant.now());

        Long id = jdbc.queryForObject("SELECT MAX(id) FROM chat_messages WHERE session_id = ?",
                Long.class, sessionId);
        return id != null ? id : 0;
    }

    /**
     * Builds a system prompt with optional memory recall context.
     */
    private String buildSystemPrompt(String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Spector, a cognitive AI assistant with biological memory. ");
        sb.append("You have access to a vector memory engine that stores and recalls ");
        sb.append("information using SIMD-accelerated neural pathways.\n\n");

        // Recall relevant memories if bridge is available
        if (memoryBridge.isAvailable()) {
            try {
                List<RecallResult> memories = memoryBridge.recall(
                        new RecallRequest(userMessage, 5, 1));
                if (!memories.isEmpty()) {
                    sb.append("## Relevant Memories\n");
                    for (RecallResult r : memories) {
                        sb.append("- [").append(r.tier()).append("] ").append(r.text()).append("\n");
                    }
                    sb.append("\nUse these memories to provide contextually aware responses.\n");
                }
            } catch (Exception e) {
                log.debug("[Chat] Memory recall failed for system prompt: {}", e.getMessage());
            }
        }

        sb.append("\nBe helpful, accurate, and concise. If you don't know something, say so.");
        return sb.toString();
    }
}
