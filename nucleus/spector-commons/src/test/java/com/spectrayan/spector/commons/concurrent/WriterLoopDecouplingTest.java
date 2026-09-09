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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WriterLoopDecouplingTest")
class WriterLoopDecouplingTest {

    @Test
    @DisplayName("Idle SpectorTaskQueue worker loop on PLATFORM_WRITER does not seize the writer thread")
    void testIdleQueueDoesNotBlockWriterThread() throws Exception {
        TaskQueueConfig config = new TaskQueueConfig(
                100,
                1,
                500L, // 500ms poll timeout
                1000L,
                0,
                0L,
                BackpressurePolicy.REJECT_FAST,
                ThreadPlane.PLATFORM_WRITER,
                1
        );

        CountDownLatch queueTaskLatch = new CountDownLatch(1);
        AtomicReference<String> queueTaskThread = new AtomicReference<>();

        try (SpectorTaskQueue<String> queue = new SpectorTaskQueue<>("decouple-queue", config, task -> {
            queueTaskThread.set(Thread.currentThread().getName());
            queueTaskLatch.countDown();
        })) {
            // Queue is running and idle (poll loop is active on virtual thread).
            // Directly submit a task to the PLATFORM_WRITER executor for this pool.
            CountDownLatch directTaskLatch = new CountDownLatch(1);
            AtomicBoolean directIsVirtual = new AtomicBoolean(true);
            AtomicReference<String> directTaskThread = new AtomicReference<>();

            long start = System.currentTimeMillis();
            SpectorExecutors.executor(ThreadPlane.PLATFORM_WRITER, "decouple-queue").execute(() -> {
                directIsVirtual.set(Thread.currentThread().isVirtual());
                directTaskThread.set(Thread.currentThread().getName());
                directTaskLatch.countDown();
            });

            boolean directDone = directTaskLatch.await(200, TimeUnit.MILLISECONDS);
            long directElapsed = System.currentTimeMillis() - start;

            // Direct task must execute immediately without waiting for queue.poll(500ms)
            assertThat(directDone).isTrue();
            assertThat(directElapsed).isLessThan(250);
            assertThat(directIsVirtual.get()).isFalse();
            assertThat(directTaskThread.get()).contains("spector-pool-writer-decouple-queue");

            // Now submit a task into the queue and ensure it also runs on the writer thread
            queue.submit("task-1", "payload");
            boolean queueDone = queueTaskLatch.await(3, TimeUnit.SECONDS);
            assertThat(queueDone).isTrue();
            assertThat(queueTaskThread.get()).contains("spector-pool-writer-decouple-queue");
        }
    }
}