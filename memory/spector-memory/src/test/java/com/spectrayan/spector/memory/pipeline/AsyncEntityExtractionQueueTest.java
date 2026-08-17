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

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncEntityExtractionQueueTest {

    @Mock
    private EntityExtractor entityExtractor;

    @Mock
    private PostIngestSync postIngestSync;

    @Test
    @DisplayName("Submits task and processes asynchronously without blocking caller")
    void testAsyncSubmissionAndProcessing() throws Exception {
        when(entityExtractor.isAvailable()).thenReturn(true);
        List<ExtractedEntity> entities = List.of(
                new ExtractedEntity("Alice", "PERSON", List.of())
        );
        when(entityExtractor.extract("mem-1", "Alice is a software engineer."))
                .thenReturn(entities);

        try (var queue = new AsyncEntityExtractionQueue(entityExtractor, postIngestSync, 1, 100)) {
            boolean accepted = queue.submit("mem-1", "Alice is a software engineer.",
                    42, 1700000000L, "session-abc", "ns-tenant-1");

            assertThat(accepted).isTrue();

            verify(postIngestSync, timeout(2000)).syncPreExtractedEntities(eq(entities), eq(42), eq("mem-1"));
            verify(postIngestSync, timeout(2000)).syncTemporalFacts(eq(entities), eq(42), eq("mem-1"), eq(1700000000L));

            var stats = queue.stats();
            assertThat(stats.totalSubmitted()).isEqualTo(1);
            assertThat(stats.totalProcessed()).isEqualTo(1);
            assertThat(stats.totalEntitiesExtracted()).isEqualTo(1);
            assertThat(stats.totalFailed()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Propagates MemoryScope.SESSION_ID and NAMESPACE_ID into the executing virtual thread")
    void testMemoryScopePropagation() throws Exception {
        when(entityExtractor.isAvailable()).thenReturn(true);

        AtomicReference<String> capturedSessionId = new AtomicReference<>();
        AtomicReference<String> capturedNamespaceId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        when(entityExtractor.extract(anyString(), anyString())).thenAnswer(invocation -> {
            capturedSessionId.set(MemoryScope.sessionId());
            capturedNamespaceId.set(MemoryScope.namespaceId());
            latch.countDown();
            return List.of(new ExtractedEntity("Bob", "PERSON", List.of()));
        });

        try (var queue = new AsyncEntityExtractionQueue(entityExtractor, postIngestSync, 1, 100)) {
            queue.submit("mem-2", "Bob works with Alice.", 10, 1700000000L, "scoped-session-999", "scoped-ns-888");

            boolean completed = latch.await(3, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(capturedSessionId.get()).isEqualTo("scoped-session-999");
            assertThat(capturedNamespaceId.get()).isEqualTo("scoped-ns-888");
        }
    }

    @Test
    @DisplayName("Gracefully ignores submissions when entity extractor is unavailable")
    void testExtractorUnavailable() {
        when(entityExtractor.isAvailable()).thenReturn(false);

        try (var queue = new AsyncEntityExtractionQueue(entityExtractor, postIngestSync, 1, 100)) {
            boolean accepted = queue.submit("mem-3", "Some text", 1, 1700000000L, null);
            assertThat(accepted).isFalse();
            assertThat(queue.stats().queueSize()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Gracefully rejects tasks after shutdown")
    void testShutdownRejection() {
        var queue = new AsyncEntityExtractionQueue(entityExtractor, postIngestSync, 1, 100);
        queue.close();

        boolean accepted = queue.submit("mem-4", "Some text", 1, 1700000000L, null);
        assertThat(accepted).isFalse();
        assertThat(queue.stats().isRunning()).isFalse();
    }

    @Test
    @DisplayName("Tracks failed extraction attempts and updates failure counter")
    void testExtractionFailureHandling() throws Exception {
        when(entityExtractor.isAvailable()).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        when(entityExtractor.extract("mem-err", "Err text")).thenAnswer(inv -> {
            latch.countDown();
            throw new RuntimeException("LLM timeout");
        });

        try (var queue = new AsyncEntityExtractionQueue(entityExtractor, postIngestSync, 1, 100)) {
            queue.submit("mem-err", "Err text", 5, 1700000000L, null);

            boolean completed = latch.await(3, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            Thread.sleep(50);
            assertThat(queue.stats().totalFailed()).isGreaterThanOrEqualTo(1);
        }
    }
}
