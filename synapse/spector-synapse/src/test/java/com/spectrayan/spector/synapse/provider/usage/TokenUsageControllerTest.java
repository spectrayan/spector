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
package com.spectrayan.spector.synapse.provider.usage;

import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TokenUsageControllerTest {

    private MockMvc mockMvc;
    private TokenUsageTracker tracker;

    @BeforeEach
    void setUp() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SynapseCacheConstants.CACHE_TOKEN_USAGE);
        tracker = new TokenUsageTracker(cacheManager);
        TokenUsageController controller = new TokenUsageController(tracker);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/usage/summary returns aggregated summary payload")
    void getSummary() throws Exception {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "openai", "gpt-4o", "alice", "sess-1", 100, 50));

        mockMvc.perform(get("/api/v1/usage/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global.inputTokens").value(100))
                .andExpect(jsonPath("$.global.outputTokens").value(50))
                .andExpect(jsonPath("$.global.totalTokens").value(150))
                .andExpect(jsonPath("$.users.alice.totalTokens").value(150))
                .andExpect(jsonPath("$.models['gpt-4o'].totalTokens").value(150));
    }

    @Test
    @DisplayName("GET /api/v1/usage/users/{userId} returns user token stats")
    void getUserStats() throws Exception {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "google", "gemini-2.0-flash", "bob", "sess-2", 300, 150));

        mockMvc.perform(get("/api/v1/usage/users/bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTokens").value(300))
                .andExpect(jsonPath("$.outputTokens").value(150))
                .andExpect(jsonPath("$.totalTokens").value(450));
    }

    @Test
    @DisplayName("GET /api/v1/usage/models/{modelName} returns model token stats")
    void getModelStats() throws Exception {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.REFLECTION, "anthropic", "claude-sonnet-4", "carol", "sess-3", 50, 25));

        mockMvc.perform(get("/api/v1/usage/models/claude-sonnet-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").value(75));
    }

    @Test
    @DisplayName("GET /api/v1/usage/sessions/{sessionId} returns session token stats")
    void getSessionStats() throws Exception {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "ollama", "qwen3.5:latest", "dave", "session-xyz", 40, 20));

        mockMvc.perform(get("/api/v1/usage/sessions/session-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").value(60));
    }

    @Test
    @DisplayName("GET /api/v1/usage/categories/{category} returns category stats and 400 on invalid category")
    void getCategoryStats() throws Exception {
        tracker.record(TokenUsageEvent.ofEmbedding("google", "text-embedding-004", "eve", 200));

        mockMvc.perform(get("/api/v1/usage/categories/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingTokens").value(200));

        mockMvc.perform(get("/api/v1/usage/categories/invalid_category"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/usage/reset clears usage telemetry")
    void resetUsage() throws Exception {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "openai", "gpt-4o", "alice", "sess-1", 100, 50));

        mockMvc.perform(post("/api/v1/usage/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reset"));

        mockMvc.perform(get("/api/v1/usage/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global.totalTokens").value(0));
    }
}
