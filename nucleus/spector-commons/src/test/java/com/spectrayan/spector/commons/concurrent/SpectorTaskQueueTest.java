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

    @Test
    @DisplayName("Enforces PLATFORM_WRITER concurrency invariants")
    void testPlatformWriterInvariants() {
        // Parallelism > 1 on PLATFORM_WRITER must fail
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new TaskQueueConfig(10, 2, 100, 1000, 0, 0, BackpressurePolicy.BLOCK, ThreadPlane.PLATFORM_WRITER, 1))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);

        // CALLER_RUNS on PLATFORM_WRITER must fail
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new TaskQueueConfig(10, 1, 100, 1000, 0, 0, BackpressurePolicy.CALLER_RUNS, ThreadPlane.PLATFORM_WRITER, 1))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class);
    }

    @Test
    @DisplayName("Applies BLOCK backpressure when queue is full and unblocks on drain")
    void testBackpressureBlock() throws Exception {
        TaskQueueConfig config = new TaskQueueConfig(
                16, 1, 100, 2000, 0, 0, BackpressurePolicy.BLOCK, ThreadPlane.PLATFORM_WRITER, 1);
        CountDownLatch workerHold = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        try (var queue = new SpectorTaskQueue<String>("test-block-queue", config, task -> {
            workerStarted.countDown();
            try {
                workerHold.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            // Task to occupy single worker
            queue.submit("blocker", "b");
            assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // Fill queue to capacity (16 items)
            for (int i = 0; i < 16; i++) {
                assertThat(queue.submit("item-" + i, "val-" + i)).isTrue();
            }

            // 17th submit should block
            CountDownLatch threadSubmitted = new CountDownLatch(1);
            Thread submitter = Thread.ofVirtual().start(() -> {
                queue.submit("item-overflow", "val-overflow");
                threadSubmitted.countDown();
            });

            // Submitter should still be waiting since queue is full
            assertThat(threadSubmitted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            // Release worker
            workerHold.countDown();

            // Submitter should now complete
            assertThat(threadSubmitted.await(3, TimeUnit.SECONDS)).isTrue();
            submitter.join();
        }
    }

    @Test
    @DisplayName("Applies DROP_OLDEST by evicting oldest submitted task instead of priority head")
    void testDropOldestEvictsOldestSubmitted() throws Exception {
        TaskQueueConfig config = new TaskQueueConfig(
                16, 1, 100, 2000, 0, 0, BackpressurePolicy.DROP_OLDEST, ThreadPlane.PLATFORM_SHARED, 1);
        CountDownLatch workerHold = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        List<String> processed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch allProcessed = new CountDownLatch(16);

        try (var queue = new SpectorTaskQueue<String>("test-drop-oldest-queue", config, task -> {
            if ("blocker".equals(task.payload())) {
                workerStarted.countDown();
                try {
                    workerHold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            processed.add(task.payload());
            allProcessed.countDown();
        })) {
            queue.submit(new ScopedTask<>("blocker", "blocker", "s", "n", TaskPriority.LOW, 50L, null));
            assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // Enqueue 16 tasks to fill queue. Task-0 has earliest timestamp (100) and HIGH priority.
            queue.submit(new ScopedTask<>("task-0", "payload-0", "s", "n", TaskPriority.HIGH, 100L, null));
            for (int i = 1; i < 16; i++) {
                queue.submit(new ScopedTask<>("task-" + i, "payload-" + i, "s", "n", TaskPriority.LOW, 100L + (i * 10), null));
            }

            // Queue is now full at 16 items. Now submit task-16 (timestamp 5000).
            // Task-0 has earlier timestamp (100), so even though it's HIGH priority, DROP_OLDEST must drop task-0!
            queue.submit(new ScopedTask<>("task-16", "payload-16", "s", "n", TaskPriority.NORMAL, 5000L, null));

            workerHold.countDown();
            assertThat(allProcessed.await(3, TimeUnit.SECONDS)).isTrue();

            assertThat(processed).contains("payload-15", "payload-16");
            assertThat(processed).doesNotContain("payload-0");
        }
    }

    @Test
    @DisplayName("Executes tasks in batches using BatchTaskHandler")
    void testBatchTaskHandler() throws Exception {
        TaskQueueConfig config = TaskQueueConfig.ofBatched(ThreadPlane.PLATFORM_WRITER, 20, 1, 5);
        List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
        List<String> allPayloads = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch receivedAll = new CountDownLatch(6);

        BatchTaskHandler<String> handler = tasks -> {
            batchSizes.add(tasks.size());
            for (ScopedTask<String> t : tasks) {
                allPayloads.add(t.payload());
                receivedAll.countDown();
            }
        };

        try (var queue = SpectorTaskQueue.<String>batched("test-batch-queue", config, handler)) {
            for (int i = 0; i < 6; i++) {
                queue.submit("task-" + i, "item-" + i);
            }

            assertThat(receivedAll.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(allPayloads).hasSize(6);
            assertThat(batchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(6);
        }
    }
}
