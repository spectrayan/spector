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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpectorQuartzThreadPool")
class SpectorQuartzThreadPoolTest {

    private SpectorQuartzThreadPool pool;

    @BeforeEach
    void setUp() throws Exception {
        pool = new SpectorQuartzThreadPool();
        pool.initialize();
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdown(false);
        }
    }

    @Test
    @DisplayName("Initializes with virtual pool size and tracks available threads")
    void testPoolInitialization() {
        assertThat(pool.getPoolSize()).isGreaterThanOrEqualTo(1);
        assertThat(pool.blockForAvailableThreads()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Executes plain Runnable on VIRTUAL plane by default")
    void testPlainRunnableExecutesOnVirtualPlane() throws InterruptedException {
        AtomicBoolean isVirtual = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        boolean accepted = pool.runInThread(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        assertThat(accepted).isTrue();
        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(isVirtual.get()).isTrue();
    }

    @Test
    @DisplayName("Routes PlaneAware runnable declared for PLATFORM_WRITER")
    void testPlaneAwareRunnableRoutesToPlatformWriter() throws InterruptedException {
        AtomicBoolean isVirtual = new AtomicBoolean(true);
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        class WriterTask implements Runnable, PlaneAware {
            @Override
            public void run() {
                isVirtual.set(Thread.currentThread().isVirtual());
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }

            @Override
            public ThreadPlane plane() {
                return ThreadPlane.PLATFORM_WRITER;
            }

            @Override
            public String poolName() {
                return "test-writer";
            }
        }

        boolean accepted = pool.runInThread(new WriterTask());
        assertThat(accepted).isTrue();
        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(isVirtual.get()).isFalse();
        assertThat(threadName.get()).contains("test-writer");
    }

    @Test
    @DisplayName("Routes PlaneAware runnable declared for PLATFORM_SHARED")
    void testPlaneAwareRunnableRoutesToPlatformShared() throws InterruptedException {
        AtomicBoolean isVirtual = new AtomicBoolean(true);
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        class SharedTask implements Runnable, PlaneAware {
            @Override
            public void run() {
                isVirtual.set(Thread.currentThread().isVirtual());
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            }

            @Override
            public ThreadPlane plane() {
                return ThreadPlane.PLATFORM_SHARED;
            }

            @Override
            public String poolName() {
                return "test-shared";
            }
        }

        boolean accepted = pool.runInThread(new SharedTask());
        assertThat(accepted).isTrue();
        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(isVirtual.get()).isFalse();
        assertThat(threadName.get()).contains("test-shared");
    }
}
