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
package com.spectrayan.spector.synapse.agent.chat.infrastructure;

import com.spectrayan.spector.synapse.agent.chat.model.Conversation;
import com.spectrayan.spector.synapse.agent.chat.service.ChatMemoryPort;
import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Adapter connecting the Chat domain to Spector's cognitive memory engine
 * and H2 for structured session data.
 *
 * <h3>Dual Storage</h3>
 * <ul>
 *   <li><b>H2</b> — structured session queries (list, paginate, load history)</li>
 *   <li><b>SpectorMemory</b> — cross-session semantic recall for context priming</li>
 * </ul>
 */
@Component
public class SpectorMemoryChatAdapter implements ChatMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryChatAdapter.class);

    private final JdbcTemplate jdbc;
    private final MemoryBridge memoryBridge;

    public SpectorMemoryChatAdapter(JdbcTemplate jdbc, MemoryBridge memoryBridge) {
        this.jdbc = jdbc;
        this.memoryBridge = memoryBridge;
    }

    @Override
    public List<Map<String, Object>> loadSessionHistory(String sessionId) {
        if (sessionId == null) return List.of();

        return jdbc.query("""
                SELECT role, content, created_at FROM chat_messages
                WHERE session_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> Map.<String, Object>of(
                        "role", rs.getString("role"),
                        "content", rs.getString("content"),
                        "timestamp", rs.getTimestamp("created_at").getTime()
                ), sessionId);
    }

    @Override
    public void saveToSession(String sessionId, String userMessage,
                              String assistantResponse, String model) {
        Instant now = Instant.now();

        // Ensure session exists
        jdbc.update("""
                MERGE INTO chat_sessions (session_id, created_at, updated_at, preview, model)
                KEY (session_id)
                VALUES (?, ?, ?, ?, ?)
                """, sessionId, now, now,
                userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage,
                model);

        // Save user turn
        jdbc.update("""
                INSERT INTO chat_messages (session_id, role, content, model, created_at)
                VALUES (?, 'user', ?, ?, ?)
                """, sessionId, userMessage, model, now);

        // Save assistant turn
        jdbc.update("""
                INSERT INTO chat_messages (session_id, role, content, model, created_at)
                VALUES (?, 'assistant', ?, ?, ?)
                """, sessionId, assistantResponse, model, now);

        // Update session timestamp
        jdbc.update("UPDATE chat_sessions SET updated_at = ?, preview = ? WHERE session_id = ?",
                now, userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage,
                sessionId);

        // Also store in SpectorMemory for cross-session semantic recall
        if (memoryBridge.isAvailable()) {
            try {
                memoryBridge.store(new StoreRequest(
                        userMessage,
                        List.of("chat", "session:" + sessionId, "role:user"),
                        null, Map.of()));
                memoryBridge.store(new StoreRequest(
                        assistantResponse,
                        List.of("chat", "session:" + sessionId, "role:assistant", "model:" + model),
                        null, Map.of()));
            } catch (Exception e) {
                log.debug("[ChatAdapter] SpectorMemory store failed: {}", e.getMessage());
            }
        }

        log.debug("[ChatAdapter] Saved turn to session {}", sessionId);
    }

    @Override
    public List<Conversation> listSessions(int limit) {
        return jdbc.query("""
                SELECT s.session_id, s.preview, s.created_at, s.updated_at,
                       (SELECT COUNT(*) FROM chat_messages m WHERE m.session_id = s.session_id) as msg_count
                FROM chat_sessions s
                ORDER BY s.updated_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new Conversation(
                        rs.getString("session_id"),
                        rs.getInt("msg_count"),
                        rs.getString("preview"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ), limit);
    }

    @Override
    public List<PrimedMemory> recallRelevantMemories(String query,
                                                      String excludeSessionId,
                                                      int limit) {
        if (!memoryBridge.isAvailable() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            var results = memoryBridge.recall(new RecallRequest(query, limit, null));

            return results.stream()
                    // Exclude memories from the current session
                    .filter(r -> excludeSessionId == null
                            || r.tags() == null
                            || !r.tags().contains("session:" + excludeSessionId))
                    .map(r -> new PrimedMemory(
                            r.text(),
                            r.memoryType(),
                            r.ageDescription(),
                            (float) r.cognitiveScore()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("[ChatAdapter] Cross-session recall failed: {}", e.getMessage());
            return List.of();
        }
    }
}
