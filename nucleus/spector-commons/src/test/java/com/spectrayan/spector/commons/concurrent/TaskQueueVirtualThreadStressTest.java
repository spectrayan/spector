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
package com.spectrayan.spector.commons.concurrent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("TaskQueueVirtualThreadStressTest")
class TaskQueueVirtualThreadStressTest {

    @Test
    @DisplayName("500 virtual threads burst 10,000 tasks through SpectorTaskQueue with 0 loss")
    void testMassiveVirtualThreadBurstSubmission() throws Exception {
        int threadCount = 500;
        int tasksPerThread = 20;
        int totalExpected = threadCount * tasksPerThread;

        TaskQueueConfig config = new TaskQueueConfig(
                totalExpected + 1000,
                8, // 8 virtual workers
                100,
                5000,
                0,
                0,
                BackpressurePolicy.REJECT_FAST
        );

        CountDownLatch allProcessedLatch = new CountDownLatch(totalExpected);
        Set<String> processedTaskIds = ConcurrentHashMap.newKeySet(totalExpected);

        try (var queue = new SpectorTaskQueue<String>("stress-burst-queue", config, task -> {
            processedTaskIds.add(task.taskId());
            allProcessedLatch.countDown();
        })) {
            try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int t = 0; t < threadCount; t++) {
                    final int threadId = t;
                    virtualExecutor.submit(() -> {
                        for (int i = 0; i < tasksPerThread; i++) {
                            String taskId = "t-" + threadId + "-" + i;
                            boolean submitted = queue.submit(taskId, "payload-" + i);
                            assertThat(submitted).isTrue();
                        }
                    });
                }
            }

            boolean completed = allProcessedLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).as("All 10,000 tasks should be processed within deadline").isTrue();
            assertThat(processedTaskIds).hasSize(totalExpected);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                var metrics = queue.metrics();
                assertThat(metrics.submitted()).isEqualTo(totalExpected);
                assertThat(metrics.processed()).isEqualTo(totalExpected);
                assertThat(metrics.failed()).isEqualTo(0);
            });
        }
    }

    @Test
    @DisplayName("Backpressure storm under 500 parallel virtual threads")
    void testBackpressurePolicySaturation() throws Exception {
        int capacity = 50;
        TaskQueueConfig config = new TaskQueueConfig(
                capacity,
                1,
                100,
                2000,
                0,
                0,
                BackpressurePolicy.REJECT_FAST
        );

        CountDownLatch workerHoldLatch = new CountDownLatch(1);
        CountDownLatch workerActiveLatch = new CountDownLatch(1);

        try (var queue = new SpectorTaskQueue<String>("stress-backpressure-queue", config, task -> {
            workerActiveLatch.countDown();
            try {
                workerHoldLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            // Fill worker
            queue.submit("initial-blocker", "data");
            workerActiveLatch.await(2, TimeUnit.SECONDS);

            // 500 parallel virtual threads hammering the saturated queue
            int threadCount = 500;
            AtomicInteger rejectedCount = new AtomicInteger(0);
            AtomicInteger acceptedCount = new AtomicInteger(0);

            try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int t = 0; t < threadCount; t++) {
                    final int threadId = t;
                    virtualExecutor.submit(() -> {
                        boolean accepted = queue.submit("task-" + threadId, "data");
                        if (accepted) {
                            acceptedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    });
                }
            }

            // High concurrency saturation check: accepted count is bounded near capacity
            assertThat(acceptedCount.get()).isBetween(capacity, capacity + 10);
            assertThat(rejectedCount.get()).isGreaterThanOrEqualTo(threadCount - (capacity + 10));

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                var metrics = queue.metrics();
                assertThat(metrics.failed()).isGreaterThanOrEqualTo(threadCount - (capacity + 10));
            });
        } finally {
            workerHoldLatch.countDown();
        }
    }

    @Test
    @DisplayName("MemoryScope session and namespace context propagation across 1,000 parallel virtual threads")
    void testMemoryScopePropagationUnder1000VirtualThreads() throws Exception {
        int count = 1000;
        TaskQueueConfig config = new TaskQueueConfig(count + 100, 8, 100, 5000, 0, 0, BackpressurePolicy.REJECT_FAST);
        CountDownLatch latch = new CountDownLatch(count);
        ConcurrentHashMap<String, String> recordedContexts = new ConcurrentHashMap<>();

        try (var queue = new SpectorTaskQueue<String>("stress-scope-queue", config, task -> {
            String sid = MemoryScope.sessionId();
            String nid = MemoryScope.namespaceId();
            recordedContexts.put(task.taskId(), sid + "::" + nid);
            latch.countDown();
        })) {
            try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < count; i++) {
                    final int id = i;
                    virtualExecutor.submit(() -> {
                        String sid = "session-" + id;
                        String nid = "tenant-" + (id % 10);
                        MemoryScope.runWithScope(sid, nid, () -> {
                            queue.submit("task-" + id, "payload");
                        });
                    });
                }
            }

            boolean done = latch.await(10, TimeUnit.SECONDS);
            assertThat(done).isTrue();
            assertThat(recordedContexts).hasSize(count);

            for (int i = 0; i < count; i++) {
                String expected = "session-" + i + "::tenant-" + (i % 10);
                assertThat(recordedContexts.get("task-" + i)).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("Concurrent close and drain while virtual threads are actively submitting")
    void testConcurrentCloseAndDrainDuringHeavySubmission() throws Exception {
        TaskQueueConfig config = new TaskQueueConfig(1000, 4, 100, 1000, 0, 0, BackpressurePolicy.REJECT_FAST);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger(0);

        var queue = new SpectorTaskQueue<String>("stress-drain-queue", config, task -> {
            processed.incrementAndGet();
        });

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 200; t++) {
                final int threadId = t;
                virtualExecutor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < 20; i++) {
                            queue.submit("drain-" + threadId + "-" + i, "item");
                        }
                    } catch (Exception ignored) {}
                });
            }

            startLatch.countDown();
            Thread.sleep(10);
            queue.close(); // Drain and close under active load
        }

        assertThat(queue.metrics().isRunning()).isFalse();
    }
}
