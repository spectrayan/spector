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
package com.spectrayan.spector.memory.scheduler;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.hippocampus.CircadianPolicy;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MultiTenantQuartzSchedulerTest {

    @Test
    @DisplayName("Multiple SpectorMemory instances have completely isolated schedulers and audit history")
    void testMultiTenantIsolation(@TempDir Path tempDir) {
        Path pathA = tempDir.resolve("tenant-a");
        Path pathB = tempDir.resolve("tenant-b");

        try (SpectorMemory memoryA = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(pathA)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("tenant-a")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofHours(1)).build())
                .build();
             SpectorMemory memoryB = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(pathB)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("tenant-b")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofHours(1)).build())
                .build()) {

            MemoryScheduler schedA = memoryA.scheduler();
            MemoryScheduler schedB = memoryB.scheduler();

            assertThat(schedA.namespaceId()).isEqualTo("tenant-a");
            assertThat(schedB.namespaceId()).isEqualTo("tenant-b");

            // Pause task in Tenant A
            schedA.pause(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(schedA.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION).get().state()).isEqualTo("PAUSED");
            // Tenant B must still be NORMAL
            assertThat(schedB.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION).get().state()).isEqualTo("NORMAL");

            // Ingest in Tenant A and trigger
            memoryA.remember("mem-a-1", "Tenant A episodic fact", MemoryType.EPISODIC, MemorySource.OBSERVED);
            schedA.triggerNow(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<TaskRunAuditRecord> historyA = schedA.getAuditHistory(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION, 10);
                assertThat(historyA).isNotEmpty();
                assertThat(historyA.get(0).namespaceId()).isEqualTo("tenant-a");
            });

            // Tenant B audit history must remain empty
            assertThat(schedB.getAuditHistory(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION, 10)).isEmpty();
        }
    }
}
