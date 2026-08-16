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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTrackerTest {

    private CaffeineCacheManager cacheManager;
    private SimpleMeterRegistry meterRegistry;
    private TokenUsageTracker tracker;

    @BeforeEach
    void setUp() {
        cacheManager = new CaffeineCacheManager(SynapseCacheConstants.CACHE_TOKEN_USAGE);
        meterRegistry = new SimpleMeterRegistry();
        tracker = new TokenUsageTracker(cacheManager, meterRegistry);
    }

    @Test
    @DisplayName("records generation tokens across Spring Cache partitions and Micrometer counters")
    void recordGenerationTokens() {
        TokenUsageEvent event = TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "openai", "gpt-4o", "alice", "session-123", 150, 75);

        tracker.record(event);

        // Verify Spring Cache global stats
        TokenUsageStats global = tracker.getGlobalStats();
        assertThat(global.inputTokens()).isEqualTo(150);
        assertThat(global.outputTokens()).isEqualTo(75);
        assertThat(global.totalTokens()).isEqualTo(225);
        assertThat(global.requestCount()).isEqualTo(1);

        // Verify User stats
        TokenUsageStats userStats = tracker.getUserStats("alice");
        assertThat(userStats.totalTokens()).isEqualTo(225);
        assertThat(userStats.requestCount()).isEqualTo(1);

        // Verify Model stats
        TokenUsageStats modelStats = tracker.getModelStats("gpt-4o");
        assertThat(modelStats.totalTokens()).isEqualTo(225);

        // Verify Session stats
        TokenUsageStats sessionStats = tracker.getSessionStats("session-123");
        assertThat(sessionStats.totalTokens()).isEqualTo(225);

        // Verify Category stats
        TokenUsageStats categoryStats = tracker.getCategoryStats(TokenUsageCategory.CHAT);
        assertThat(categoryStats.totalTokens()).isEqualTo(225);

        // Verify Micrometer metrics
        Counter userInCounter = meterRegistry.find("spector.tokens.user")
                .tag("user_id", "alice").tag("type", "input").counter();
        assertThat(userInCounter).isNotNull();
        assertThat(userInCounter.count()).isEqualTo(150.0);

        Counter modelOutCounter = meterRegistry.find("spector.tokens.model")
                .tag("model", "gpt-4o").tag("type", "output").counter();
        assertThat(modelOutCounter).isNotNull();
        assertThat(modelOutCounter.count()).isEqualTo(75.0);

        Counter sessionTotalCounter = meterRegistry.find("spector.tokens.session")
                .tag("session_id", "session-123").tag("type", "input").counter();
        assertThat(sessionTotalCounter).isNotNull();
        assertThat(sessionTotalCounter.count()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("records embedding tokens in Spring Cache and metrics")
    void recordEmbeddingTokens() {
        TokenUsageEvent event = TokenUsageEvent.ofEmbedding(
                "google", "text-embedding-004", "bob", 500);

        tracker.record(event);

        TokenUsageStats global = tracker.getGlobalStats();
        assertThat(global.embeddingTokens()).isEqualTo(500);
        assertThat(global.totalTokens()).isEqualTo(500);

        TokenUsageStats userStats = tracker.getUserStats("bob");
        assertThat(userStats.embeddingTokens()).isEqualTo(500);

        Counter embCounter = meterRegistry.find("spector.tokens.total")
                .tag("type", "embedding").tag("category", "embedding").counter();
        assertThat(embCounter).isNotNull();
        assertThat(embCounter.count()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("handles high concurrent recording safely and accurately")
    void concurrentRecording() throws InterruptedException {
        int threads = 10;
        int iterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final String userId = "user-" + (t % 2); // 2 users
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        tracker.record(TokenUsageEvent.ofGeneration(
                                TokenUsageCategory.CHAT, "ollama", "qwen3.5:latest",
                                userId, "sess-" + userId, 10, 5));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).isTrue();

        long expectedTotalEvents = (long) threads * iterations;
        long expectedTotalTokens = expectedTotalEvents * 15; // 10 in + 5 out

        TokenUsageStats global = tracker.getGlobalStats();
        assertThat(global.requestCount()).isEqualTo(expectedTotalEvents);
        assertThat(global.inputTokens()).isEqualTo(expectedTotalEvents * 10);
        assertThat(global.outputTokens()).isEqualTo(expectedTotalEvents * 5);
        assertThat(global.totalTokens()).isEqualTo(expectedTotalTokens);
    }

    @Test
    @DisplayName("getSummary builds hierarchical map of all active dimensions")
    void getSummary() {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "openai", "gpt-4o", "alice", "sess-1", 100, 50));
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.REFLECTION, "anthropic", "claude-sonnet-4", "bob", "sess-2", 200, 100));

        Map<String, Object> summary = tracker.getSummary();
        assertThat(summary).containsKey("global");
        assertThat(summary).containsKey("models");
        assertThat(summary).containsKey("users");
        assertThat(summary).containsKey("sessions");
        assertThat(summary).containsKey("categories");

        @SuppressWarnings("unchecked")
        Map<String, TokenUsageStats> users = (Map<String, TokenUsageStats>) summary.get("users");
        assertThat(users).containsKeys("alice", "bob");
    }

    @Test
    @DisplayName("reset clears all Spring Cache entries and registered sets")
    void resetClearsCache() {
        tracker.record(TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "openai", "gpt-4o", "alice", "sess-1", 100, 50));

        assertThat(tracker.getGlobalStats().totalTokens()).isEqualTo(150);

        tracker.reset();

        assertThat(tracker.getGlobalStats().totalTokens()).isZero();
        assertThat(tracker.getUserStats("alice").totalTokens()).isZero();
        assertThat(tracker.getKnownUsers()).isEmpty();
    }
}
