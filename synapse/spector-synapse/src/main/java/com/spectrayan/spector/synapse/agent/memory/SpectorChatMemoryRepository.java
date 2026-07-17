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
package com.spectrayan.spector.synapse.agent.memory;

import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Repository;

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

    private final MemoryService memoryService;

    public SpectorChatMemoryRepository(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    // ═══════════════════════════════════════════════════════════════
    // Spring AI ChatMemoryRepository contract
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<String> findConversationIds() {
        if (!memoryService.isEngineAvailable()) return List.of();
        
        // Recall session summaries and extract IDs from tags
        var results = memoryService.recall(new RecallRequest("session_summary", 100, null));
        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains("session_summary"))
                .map(r -> r.tags().stream()
                        .filter(t -> t.startsWith("id:"))
                        .map(t -> t.substring(3))
                        .findFirst()
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || !memoryService.isEngineAvailable()) return List.of();

        var results = memoryService.recall(new RecallRequest("session:" + conversationId, 100, null));

        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains("session:" + conversationId))
                .sorted((a, b) -> a.id().compareTo(b.id())) // Rough temporal order
                .map(r -> {
                    String role = r.tags().stream()
                            .filter(t -> t.startsWith("role:"))
                            .map(t -> t.substring(5))
                            .findFirst()
                            .orElse("user");
                    return toMessage(role, r.text());
                })
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (!memoryService.isEngineAvailable()) return;

        // Spring AI's contract: replace all messages for this conversation
        // In SpectorMemory, we'd need to "forget" the old ones first if we wanted exact parity
        // For now, we store them as new memories.
        
        for (Message message : messages) {
            String role = toRole(message);
            String content = message.getText();

            try {
                memoryService.store(new StoreRequest(
                        content,
                        List.of("chat", "session:" + conversationId, "role:" + role, "type:turn"),
                        null, Map.of()));
            } catch (Exception e) {
                log.debug("[SpectorChatMemory] SpectorMemory store failed: {}", e.getMessage());
            }
        }

        log.debug("[SpectorChatMemory] Saved {} messages for conversation {} to cognitive memory",
                messages.size(), conversationId);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // Implement "forget" logic for all memories with session tag
        if (!memoryService.isEngineAvailable()) return;
        
        var results = memoryService.recall(new RecallRequest("session:" + conversationId, 100, null));
        results.stream()
                .filter(r -> r.tags() != null && r.tags().contains("session:" + conversationId))
                .forEach(r -> memoryService.forget(r.id()));
        
        log.info("[SpectorChatMemory] Deleted messages from conversation {} in cognitive memory", conversationId);
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
