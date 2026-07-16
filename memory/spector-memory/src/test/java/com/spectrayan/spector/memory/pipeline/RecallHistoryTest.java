/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pipeline;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RecallHistory} — thread-safe circular buffer tracking recent recall context tags.
 */
@DisplayName("RecallHistory")
class RecallHistoryTest {

    // ══════════════════════════════════════════════════════════════
    // Empty state
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("empty history returns empty tags")
    void emptyHistoryReturnsEmptyTags() {
        var history = new RecallHistory();
        String[] tags = history.recentTags(10);
        assertThat(tags).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // Basic recording and retrieval
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("record one set and retrieve its tags")
    void recordAndRetrieveTags() {
        var history = new RecallHistory();
        history.record(new String[]{"database", "timeout"});

        String[] tags = history.recentTags(10);
        assertThat(tags).containsExactly("database", "timeout");
    }

    // ══════════════════════════════════════════════════════════════
    // Ring buffer wrapping
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ring buffer wraps correctly — oldest entries are evicted")
    void ringBufferWrapsCorrectly() {
        int capacity = 3;
        var history = new RecallHistory(capacity);

        history.record(new String[]{"oldest"});
        history.record(new String[]{"middle"});
        history.record(new String[]{"newest"});
        // Buffer is full; next record evicts "oldest"
        history.record(new String[]{"replacement"});

        assertThat(history.size()).isEqualTo(capacity);

        // Most recent first: replacement, newest, middle — oldest is evicted
        String[] tags = history.recentTags(10);
        assertThat(tags).contains("replacement", "newest", "middle");
        assertThat(tags).doesNotContain("oldest");
    }

    // ══════════════════════════════════════════════════════════════
    // Deduplication
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("recentTags deduplicates — same tag across records appears once")
    void recentTagsDeduplicates() {
        var history = new RecallHistory();
        history.record(new String[]{"java", "database"});
        history.record(new String[]{"database", "redis"});
        history.record(new String[]{"java", "cache"});

        String[] tags = history.recentTags(10);
        // Each unique tag should appear exactly once
        assertThat(tags).containsExactlyInAnyOrder("java", "database", "redis", "cache");
        // But size matches unique count
        assertThat(tags).hasSize(4);
    }

    // ══════════════════════════════════════════════════════════════
    // Weighted tags with decay
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("weightedRecentTags applies exponential decay — most recent has highest weight")
    void weightedRecentTagsAppliesDecay() {
        var history = new RecallHistory();
        history.record(new String[]{"old_tag"});
        history.record(new String[]{"mid_tag"});
        history.record(new String[]{"new_tag"});

        Map<String, Float> weighted = history.weightedRecentTags(10, 0.5f);

        // Most recent entry has weight 1.0, next 0.5, then 0.25
        assertThat(weighted.get("new_tag")).isCloseTo(1.0f, within(1e-6f));
        assertThat(weighted.get("mid_tag")).isCloseTo(0.5f, within(1e-6f));
        assertThat(weighted.get("old_tag")).isCloseTo(0.25f, within(1e-6f));
    }

    // ══════════════════════════════════════════════════════════════
    // Null / Empty handling
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("record handles null and empty arrays without crashing")
    void recordHandlesNullAndEmpty() {
        var history = new RecallHistory();

        // These should not throw
        assertThatCode(() -> history.record(null)).doesNotThrowAnyException();
        assertThatCode(() -> history.record(new String[]{})).doesNotThrowAnyException();

        // Buffer should still be empty
        assertThat(history.size()).isZero();
        assertThat(history.recentTags(10)).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════
    // Thread safety
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("thread safety — concurrent record() + recentTags() doesn't throw")
    void threadSafetyUnderConcurrentAccess() throws InterruptedException {
        var history = new RecallHistory(5);
        int threadCount = 8;
        int opsPerThread = 500;
        var errors = new AtomicInteger(0);
        var latch = new CountDownLatch(threadCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            if (i % 2 == 0) {
                                history.record(new String[]{"tag-" + threadId + "-" + i});
                            } else {
                                history.recentTags(5);
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertThat(completed).as("All threads should complete within timeout").isTrue();
            assertThat(errors.get()).as("No exceptions should occur under concurrent access").isZero();
        }
    }
}
