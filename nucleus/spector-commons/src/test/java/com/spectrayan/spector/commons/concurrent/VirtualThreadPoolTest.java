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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadPoolTest {

    @Test
    @DisplayName("VirtualThreadPool defaults to ConcurrentTasks virtual executor")
    void testDefaultConstructor() throws Exception {
        VirtualThreadPool pool = new VirtualThreadPool();
        pool.setInstanceName("test-default-sched");
        pool.initialize();

        assertThat(pool.isRunning()).isTrue();
        assertThat(pool.executor()).isNotNull();
        assertThat(pool.instanceName()).isEqualTo("test-default-sched");
        assertThat(pool.getPoolSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(pool.blockForAvailableThreads()).isGreaterThan(0);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ranOnVirtual = new AtomicBoolean(false);

        boolean submitted = pool.runInThread(() -> {
            ranOnVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        assertThat(submitted).isTrue();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ranOnVirtual.get()).isTrue();

        pool.shutdown(true);
        assertThat(pool.isRunning()).isFalse();
    }

    @Test
    @DisplayName("VirtualThreadPool delegates to supplied custom Executor")
    void testSuppliedExecutor() throws Exception {
        try (ExecutorService customExec = Executors.newVirtualThreadPerTaskExecutor()) {
            VirtualThreadPool pool = new VirtualThreadPool(customExec);
            pool.setInstanceName("custom-sched");
            pool.initialize();

            assertThat(pool.executor()).isSameAs(customExec);

            CountDownLatch latch = new CountDownLatch(5);
            for (int i = 0; i < 5; i++) {
                pool.runInThread(latch::countDown);
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            pool.shutdown(false);
        }
    }

    @Test
    @DisplayName("VirtualThreadPool handles null runnable gracefully")
    void testNullRunnable() {
        VirtualThreadPool pool = new VirtualThreadPool();
        assertThat(pool.runInThread(null)).isFalse();
    }
}
