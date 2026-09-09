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
package com.spectrayan.spector.commons.concurrent.spi;

import com.spectrayan.spector.commons.concurrent.SpectorExecutors;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpectorExecutorProvider & DefaultExecutorProvider")
class SpectorExecutorProviderTest {

    @AfterEach
    void tearDown() {
        SpectorExecutors.reset();
    }

    @Test
    @DisplayName("DefaultExecutorProvider provides executors for all ThreadPlanes")
    void testDefaultExecutorProviderPlanes() {
        DefaultExecutorProvider provider = new DefaultExecutorProvider();

        Executor virtual = provider.executor(ThreadPlane.VIRTUAL, "default");
        Executor shared = provider.executor(ThreadPlane.PLATFORM_SHARED, "default");
        Executor writer1 = provider.executor(ThreadPlane.PLATFORM_WRITER, "ns-1");
        Executor writer2 = provider.executor(ThreadPlane.PLATFORM_WRITER, "ns-2");

        assertThat(virtual).isNotNull();
        assertThat(shared).isNotNull();
        assertThat(writer1).isNotNull();
        assertThat(writer2).isNotNull();

        // Memoization: same pool name gives same executor
        assertThat(provider.executor(ThreadPlane.PLATFORM_WRITER, "ns-1")).isSameAs(writer1);
        // Different pool names give different executors
        assertThat(writer1).isNotSameAs(writer2);

        provider.close();
    }

    @Test
    @DisplayName("Virtual executor creates virtual threads")
    void testVirtualExecutorRunsVirtualThreads() throws InterruptedException {
        DefaultExecutorProvider provider = new DefaultExecutorProvider();
        Executor virtual = provider.executor(ThreadPlane.VIRTUAL, "test");

        AtomicBoolean isVirtual = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        virtual.execute(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(isVirtual.get()).isTrue();

        provider.close();
    }

    @Test
    @DisplayName("Platform writer executes sequentially on single thread")
    void testPlatformWriterSerialExecution() throws InterruptedException {
        DefaultExecutorProvider provider = new DefaultExecutorProvider();
        Executor writer = provider.executor(ThreadPlane.PLATFORM_WRITER, "ns-serial");

        AtomicBoolean isVirtual = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(2);

        writer.execute(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });
        writer.execute(latch::countDown);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(isVirtual.get()).isFalse();

        provider.close();
    }

    @Test
    @DisplayName("Drain waits cooperatively for tasks to complete")
    void testDrain() throws InterruptedException {
        DefaultExecutorProvider provider = new DefaultExecutorProvider();
        Executor shared = provider.executor(ThreadPlane.PLATFORM_SHARED, "pool-drain");

        CountDownLatch taskCompleted = new CountDownLatch(1);
        shared.execute(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            taskCompleted.countDown();
        });

        DrainResult result = provider.drain(Duration.ofSeconds(2));
        assertThat(result.completed()).isTrue();
        assertThat(taskCompleted.getCount()).isZero();

        provider.close();
    }

    @Test
    @DisplayName("SpectorExecutors delegates to installed provider")
    void testSpectorExecutorsDelegation() {
        DefaultExecutorProvider customProvider = new DefaultExecutorProvider();
        SpectorExecutors.install(customProvider);

        assertThat(SpectorExecutors.current()).isSameAs(customProvider);
        Executor exec = SpectorExecutors.executor(ThreadPlane.VIRTUAL, "default");
        assertThat(exec).isSameAs(customProvider.executor(ThreadPlane.VIRTUAL, "default"));

        SpectorExecutors.reset();
        assertThat(SpectorExecutors.current()).isNotSameAs(customProvider);
        customProvider.close();
    }
}
