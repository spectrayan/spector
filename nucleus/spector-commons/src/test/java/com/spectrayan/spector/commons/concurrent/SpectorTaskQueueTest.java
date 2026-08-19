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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("SpectorTaskQueue")
class SpectorTaskQueueTest {

    @Test
    @DisplayName("Executes tasks asynchronously and propagates ScopedValue session and namespace")
    void testAsyncExecutionAndScopePropagation() throws Exception {
        AtomicReference<String> seenSession = new AtomicReference<>();
        AtomicReference<String> seenNamespace = new AtomicReference<>();
        AtomicReference<String> seenPayload = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        TaskQueueConfig config = TaskQueueConfig.of(100, 1);
        try (var queue = new SpectorTaskQueue<String>("test-scope-queue", config, task -> {
            seenSession.set(MemoryScope.sessionId());
            seenNamespace.set(MemoryScope.namespaceId());
            seenPayload.set(task.payload());
            latch.countDown();
        })) {
            ScopedTask<String> task = ScopedTask.of("t-1", "hello-world", "sess-123", "ns-456", TaskPriority.NORMAL);
            boolean accepted = queue.submit(task);
            assertThat(accepted).isTrue();

            boolean done = latch.await(3, TimeUnit.SECONDS);
            assertThat(done).isTrue();
            assertThat(seenSession.get()).isEqualTo("sess-123");
            assertThat(seenNamespace.get()).isEqualTo("ns-456");
            assertThat(seenPayload.get()).isEqualTo("hello-world");

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        var metrics = queue.metrics();
                        assertThat(metrics.submitted()).isEqualTo(1);
                        assertThat(metrics.processed()).isEqualTo(1);
                        assertThat(metrics.failed()).isEqualTo(0);
                    });
        }
    }

    @Test
    @DisplayName("Prioritizes HIGH priority tasks over NORMAL priority tasks")
    void testPriorityOrdering() throws Exception {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch unblockGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(3);

        TaskQueueConfig config = new TaskQueueConfig(100, 1, 100, 2000, 0, 0, BackpressurePolicy.REJECT_FAST);
        try (var queue = new SpectorTaskQueue<String>("test-priority-queue", config, task -> {
            if ("blocker".equals(task.payload())) {
                blockerStarted.countDown();
                try {
                    unblockGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            executionOrder.add(task.payload());
            finishGate.countDown();
        })) {
            // First submit blocker to keep worker 1 occupied
            queue.submit(ScopedTask.of("blocker", "blocker", "s", "n", TaskPriority.LOW));
            boolean started = blockerStarted.await(3, TimeUnit.SECONDS);
            assertThat(started).isTrue();

            // Now submit tasks while worker is blocked
            queue.submit(ScopedTask.of("task-low", "low", "s", "n", TaskPriority.LOW));
            queue.submit(ScopedTask.of("task-normal", "normal", "s", "n", TaskPriority.NORMAL));
            queue.submit(ScopedTask.of("task-high", "high", "s", "n", TaskPriority.HIGH));

            // Release worker
            unblockGate.countDown();
            boolean done = finishGate.await(3, TimeUnit.SECONDS);
            assertThat(done).isTrue();

            // High priority task should precede lower priority tasks
            assertThat(executionOrder.get(0)).isEqualTo("high");
            assertThat(executionOrder.get(1)).isEqualTo("normal");
            assertThat(executionOrder.get(2)).isEqualTo("low");
        }
    }

    @Test
    @DisplayName("Retries failed task with backoff before marking failed")
    void testRetryWithBackoff() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        TaskQueueConfig config = new TaskQueueConfig(100, 1, 100, 2000, 2, 50, BackpressurePolicy.REJECT_FAST);
        try (var queue = new SpectorTaskQueue<String>("test-retry-queue", config, task -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("transient error on attempt " + attempt);
            }
            latch.countDown();
        })) {
            queue.submit("t-retry", "payload");
            boolean done = latch.await(3, TimeUnit.SECONDS);
            assertThat(done).isTrue();
            assertThat(attempts.get()).isEqualTo(3);

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        var metrics = queue.metrics();
                        assertThat(metrics.retried()).isEqualTo(2);
                        assertThat(metrics.processed()).isEqualTo(1);
                        assertThat(metrics.failed()).isEqualTo(0);
                    });
        }
    }

    @Test
    @DisplayName("Applies REJECT_FAST backpressure when queue is full")
    void testBackpressureRejectFast() throws InterruptedException {
        TaskQueueConfig config = new TaskQueueConfig(16, 1, 100, 2000, 0, 0, BackpressurePolicy.REJECT_FAST);
        // Worker that blocks to fill queue
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        try (var queue = new SpectorTaskQueue<String>("test-full-queue", config, task -> {
            workerStarted.countDown();
            blocker.await();
        })) {
            // First task to occupy worker
            queue.submit("task-blocker", "item");
            boolean started = workerStarted.await(2, TimeUnit.SECONDS);
            assertThat(started).isTrue();

            // Fill queue to capacity (16 items)
            for (int i = 0; i < 16; i++) {
                boolean added = queue.submit("task-" + i, "item");
                assertThat(added).isTrue();
            }

            // 17th task in queue (exceeding capacity 16) must be rejected
            boolean accepted = queue.submit("task-overflow", "item");
            assertThat(accepted).isFalse();

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        var metrics = queue.metrics();
                        assertThat(metrics.failed()).isGreaterThanOrEqualTo(1);
                    });
        } finally {
            blocker.countDown();
        }
    }

    @Test
    @DisplayName("TaskQueueManager registers and aggregates metrics")
    void testTaskQueueManagerRegistry() {
        TaskQueueConfig config = TaskQueueConfig.of(50, 1);
        try (var q1 = new SpectorTaskQueue<String>("manager-q1", config, t -> {});
             var q2 = new SpectorTaskQueue<String>("manager-q2", config, t -> {})) {

            assertThat(TaskQueueManager.registeredQueueCount()).isGreaterThanOrEqualTo(2);
            var allMetrics = TaskQueueManager.allMetrics();
            assertThat(allMetrics.stream().anyMatch(m -> m.queueName().equals("manager-q1"))).isTrue();
            assertThat(allMetrics.stream().anyMatch(m -> m.queueName().equals("manager-q2"))).isTrue();
        }
    }
}
