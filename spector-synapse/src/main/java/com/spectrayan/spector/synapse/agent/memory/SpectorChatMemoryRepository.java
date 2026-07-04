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
package com.spectrayan.spector.synapse.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link ChatMemoryRepository} implementation backed by Spector's
 * cognitive memory engine and H2 for structured data.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>H2 (primary)</b> — {@code chat_messages} table for fast sequential
 *       access, session management, and pagination.</li>
 *   <li><b>SpectorMemory (secondary)</b> — key messages are also stored in the
 *       cognitive memory engine for cross-session semantic recall. This enables
 *       the agent to recall relevant context from past conversations.</li>
 * </ul>
 *
 * <p>This implementation fulfills Spring AI's {@code ChatMemoryRepository} contract
 * so it can be used directly with {@code MessageWindowChatMemory} and
 * {@code MessageChatMemoryAdvisor}.</p>
 */
@Repository
public class SpectorChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(SpectorChatMemoryRepository.class);

    private final JdbcTemplate jdbc;
    private final MemoryBridge memoryBridge;
    private final ObjectMapper mapper;

    public SpectorChatMemoryRepository(JdbcTemplate jdbc,
                                       MemoryBridge memoryBridge,
                                       ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.memoryBridge = memoryBridge;
        this.mapper = mapper;
    }

    // ═══════════════════════════════════════════════════════════════
    // Spring AI ChatMemoryRepository contract
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<String> findConversationIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT session_id FROM chat_messages ORDER BY session_id",
                String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbc.query(
                "SELECT role, content FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
                (rs, rowNum) -> toMessage(rs.getString("role"), rs.getString("content")),
                conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // Spring AI's contract: replace all messages for this conversation
        jdbc.update("DELETE FROM chat_messages WHERE session_id = ?", conversationId);

        Instant now = Instant.now();
        for (Message message : messages) {
            String role = toRole(message);
            String content = message.getText();

            jdbc.update("""
                    INSERT INTO chat_messages (session_id, role, content, model, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, conversationId, role, content, "default", now);

            // Also store in SpectorMemory for cross-session semantic recall
            if (memoryBridge.isAvailable() && !content.isBlank()
                    && ("user".equals(role) || "assistant".equals(role))) {
                try {
                    memoryBridge.store(new StoreRequest(
                            content,
                            List.of("chat", "session:" + conversationId, "role:" + role),
                            null, Map.of()));
                } catch (Exception e) {
                    log.debug("[SpectorChatMemory] SpectorMemory store failed: {}", e.getMessage());
                }
            }
        }

        log.debug("[SpectorChatMemory] Saved {} messages for conversation {}",
                messages.size(), conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        int deleted = jdbc.update("DELETE FROM chat_messages WHERE session_id = ?", conversationId);
        log.info("[SpectorChatMemory] Deleted {} messages from conversation {}", deleted, conversationId);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private static Message toMessage(String role, String content) {
        return switch (role) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
    }

    private static String toRole(Message message) {
        return switch (message) {
            case AssistantMessage _ -> "assistant";
            case SystemMessage _ -> "system";
            case UserMessage _ -> "user";
            default -> "user";
        };
    }
}
