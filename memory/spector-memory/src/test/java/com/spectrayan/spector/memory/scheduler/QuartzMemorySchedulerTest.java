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
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzMemorySchedulerTest {

    @Test
    @DisplayName("Standalone SpectorMemory initializes QuartzMemoryScheduler with registered tasks")
    void testStandaloneSchedulerLifecycle(@TempDir Path tempDir) {
        try (SpectorMemory memory = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(tempDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("test-standalone-ns")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofMinutes(10)).build())
                .build()) {

            MemoryScheduler scheduler = memory.scheduler();
            assertThat(scheduler).isNotNull();
            assertThat(scheduler.namespaceId()).isEqualTo("test-standalone-ns");
            assertThat(scheduler.isRunning()).isTrue();

            List<TaskStatus> tasks = scheduler.listTasks();
            assertThat(tasks).isNotEmpty();

            Optional<TaskStatus> sleepTask = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(sleepTask).isPresent();
            assertThat(sleepTask.get().id()).isEqualTo(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(sleepTask.get().state()).isEqualTo("NORMAL");
            assertThat(sleepTask.get().description()).contains("Hippocampal sleep consolidation");
        }
    }

    @Test
    @DisplayName("triggerNow executes task immediately without error")
    void testTriggerNow(@TempDir Path tempDir) {
        try (SpectorMemory memory = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(tempDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("test-trigger-ns")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofHours(1)).build())
                .build()) {

            memory.remember("mem-1", "Episodic test memory content", MemoryType.EPISODIC, MemorySource.OBSERVED);

            MemoryScheduler scheduler = memory.scheduler();
            scheduler.triggerNow(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);

            Optional<TaskStatus> task = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(task).isPresent();
            assertThat(task.get().state()).isIn("NORMAL", "BLOCKED");
        }
    }

    @Test
    @DisplayName("pause and resume dynamically toggle task trigger state")
    void testPauseAndResume(@TempDir Path tempDir) {
        try (SpectorMemory memory = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(tempDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("test-pause-ns")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofMinutes(5)).build())
                .build()) {

            MemoryScheduler scheduler = memory.scheduler();
            scheduler.pause(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);

            Optional<TaskStatus> paused = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(paused).isPresent();
            assertThat(paused.get().state()).isEqualTo("PAUSED");

            scheduler.resume(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);

            Optional<TaskStatus> resumed = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(resumed).isPresent();
            assertThat(resumed.get().state()).isEqualTo("NORMAL");
        }
    }

    @Test
    @DisplayName("rescheduleInterval and rescheduleCron update trigger schedule")
    void testReschedule(@TempDir Path tempDir) {
        try (SpectorMemory memory = DefaultSpectorMemory.builder()
                .dimensions(128)
                .embeddingProvider(new FakeEmbeddingProvider())
                .persistence(tempDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId("test-resched-ns")
                .circadianPolicy(CircadianPolicy.builder().timeTrigger(Duration.ofMinutes(5)).build())
                .build()) {

            MemoryScheduler scheduler = memory.scheduler();

            scheduler.rescheduleInterval(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION, Duration.ofSeconds(45));
            Optional<TaskStatus> updatedInterval = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(updatedInterval).isPresent();
            assertThat(updatedInterval.get().nextFireTime()).isNotNull();

            scheduler.rescheduleCron(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION, "0 0/15 * * * ?");
            Optional<TaskStatus> updatedCron = scheduler.getTask(QuartzMemoryScheduler.TASK_SLEEP_CONSOLIDATION);
            assertThat(updatedCron).isPresent();
            assertThat(updatedCron.get().nextFireTime()).isNotNull();
        }
    }
}
