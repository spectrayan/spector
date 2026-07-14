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
package com.spectrayan.spector.synapse.agent.chat.infrastructure;

import com.spectrayan.spector.synapse.agent.chat.model.Conversation;
import com.spectrayan.spector.synapse.agent.chat.service.ChatMemoryPort;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Adapter connecting the Chat domain to Spector's cognitive memory engine.
 *
 * <p>All chat sessions and messages are stored as cognitive memories,
 * eliminating the need for a relational database.</p>
 */
@Component
public class SpectorMemoryChatAdapter implements ChatMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryChatAdapter.class);

    private final MemoryService memoryService;

    public SpectorMemoryChatAdapter(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public List<Map<String, Object>> loadSessionHistory(String sessionId) {
        if (sessionId == null || !memoryService.isEngineAvailable()) return List.of();

        // Recall memories for this session, sorted by temporal chain (ideally)
        // For now, we query specifically for session tags
        var results = memoryService.recall(new RecallRequest("session:" + sessionId, 100, null));

        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains("session:" + sessionId))
                .sorted((a, b) -> a.id().compareTo(b.id())) // Rough temporal order by ID if they are sequential
                .map(r -> {
                    String role = r.tags().stream()
                            .filter(t -> t.startsWith("role:"))
                            .map(t -> t.substring(5))
                            .findFirst()
                            .orElse("user");
                    return Map.<String, Object>of(
                            "role", role,
                            "content", r.text(),
                            "timestamp", System.currentTimeMillis() // Metadata not fully preserved in recall currently
                    );
                })
                .toList();
    }

    @Override
    public void saveToSession(String sessionId, String userMessage,
                               String assistantResponse, String model) {
        if (!memoryService.isEngineAvailable()) return;

        try {
            // Store user turn
            memoryService.store(new StoreRequest(
                    userMessage,
                    List.of("chat", "session:" + sessionId, "role:user", "type:turn"),
                    null, Map.of()));
            
            // Store assistant turn
            memoryService.store(new StoreRequest(
                    assistantResponse,
                    List.of("chat", "session:" + sessionId, "role:assistant", "model:" + model, "type:turn"),
                    null, Map.of()));

            // Delete any existing session summaries for this session to avoid duplicates
            try {
                var existing = memoryService.recall(new RecallRequest("id:" + sessionId, 10, null));
                for (var r : existing) {
                    if (r.tags() != null && r.tags().contains("session_summary") && r.tags().contains("id:" + sessionId)) {
                        memoryService.forget(r.id());
                    }
                }
            } catch (Exception e) {
                log.debug("[ChatAdapter] Failed to delete old session summary: {}", e.getMessage());
            }

            // Store/Update session summary record for listing
            String preview = userMessage.length() > 200 ? userMessage.substring(0, 200) : userMessage;
            memoryService.store(new StoreRequest(
                    "Session Preview: " + preview,
                    List.of("chat", "session_summary", "id:" + sessionId),
                    null, Map.of()));

        } catch (Exception e) {
            log.debug("[ChatAdapter] SpectorMemory store failed: {}", e.getMessage());
        }

        log.debug("[ChatAdapter] Saved turn to cognitive memory for session {}", sessionId);
    }

    @Override
    public List<Conversation> listSessions(int limit) {
        if (!memoryService.isEngineAvailable()) return List.of();

        // Query for session summaries
        var results = memoryService.recall(new RecallRequest("session_summary", limit, null));

        return results.stream()
                .filter(r -> r.tags() != null && r.tags().contains("session_summary"))
                .map(r -> {
                    String sid = r.tags().stream()
                            .filter(t -> t.startsWith("id:"))
                            .map(t -> t.substring(3))
                            .findFirst()
                            .orElse("unknown");
                    return new Conversation(
                            sid,
                            0, // Message count not easily available without another query
                            r.text().replace("Session Preview: ", ""),
                            Instant.now(), // Real timestamps not available in recall DTO
                            Instant.now()
                    );
                })
                .toList();
    }

    @Override
    public List<PrimedMemory> recallRelevantMemories(String query,
                                                      String excludeSessionId,
                                                      int limit) {
        if (!memoryService.isEngineAvailable() || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            var results = memoryService.recall(new RecallRequest(query, limit, null));

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
