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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageStatsTest {

    @Test
    @DisplayName("empty stats should initialize with zero counts")
    void emptyStats() {
        TokenUsageStats stats = TokenUsageStats.empty();
        assertThat(stats.inputTokens()).isZero();
        assertThat(stats.outputTokens()).isZero();
        assertThat(stats.embeddingTokens()).isZero();
        assertThat(stats.totalTokens()).isZero();
        assertThat(stats.requestCount()).isZero();
        assertThat(stats.firstRecorded()).isNotNull();
        assertThat(stats.lastRecorded()).isNotNull();
    }

    @Test
    @DisplayName("accumulate should increment tokens and requestCount accurately")
    void accumulateEvent() {
        TokenUsageStats stats = TokenUsageStats.empty();
        TokenUsageEvent event1 = TokenUsageEvent.ofGeneration(
                TokenUsageCategory.CHAT, "google", "gemini-2.0-flash", "u1", "s1", 100, 50);

        TokenUsageStats updated = stats.accumulate(event1);
        assertThat(updated.inputTokens()).isEqualTo(100);
        assertThat(updated.outputTokens()).isEqualTo(50);
        assertThat(updated.totalTokens()).isEqualTo(150);
        assertThat(updated.requestCount()).isEqualTo(1);

        TokenUsageEvent event2 = TokenUsageEvent.ofEmbedding(
                "google", "text-embedding-004", "u1", 200);

        TokenUsageStats second = updated.accumulate(event2);
        assertThat(second.inputTokens()).isEqualTo(100);
        assertThat(second.outputTokens()).isEqualTo(50);
        assertThat(second.embeddingTokens()).isEqualTo(200);
        assertThat(second.totalTokens()).isEqualTo(350);
        assertThat(second.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("merge should combine two independent stats objects")
    void mergeStats() {
        TokenUsageStats s1 = new TokenUsageStats(100, 50, 20, 170, 2, Instant.now().minusSeconds(60), Instant.now().minusSeconds(30));
        TokenUsageStats s2 = new TokenUsageStats(200, 100, 40, 340, 3, Instant.now().minusSeconds(40), Instant.now());

        TokenUsageStats merged = s1.merge(s2);
        assertThat(merged.inputTokens()).isEqualTo(300);
        assertThat(merged.outputTokens()).isEqualTo(150);
        assertThat(merged.embeddingTokens()).isEqualTo(60);
        assertThat(merged.totalTokens()).isEqualTo(510);
        assertThat(merged.requestCount()).isEqualTo(5);
        assertThat(merged.firstRecorded()).isEqualTo(s1.firstRecorded());
        assertThat(merged.lastRecorded()).isEqualTo(s2.lastRecorded());
    }
}
