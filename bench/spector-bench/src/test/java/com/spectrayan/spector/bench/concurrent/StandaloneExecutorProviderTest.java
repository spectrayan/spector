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
package com.spectrayan.spector.bench.concurrent;

import com.spectrayan.spector.commons.concurrent.SpectorExecutors;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StandaloneExecutorProviderTest {

    @AfterEach
    void tearDown() {
        SpectorExecutors.reset();
    }

    @Test
    void testMetricsTrackingAndInstallation() throws InterruptedException {
        StandaloneExecutorProvider provider = new StandaloneExecutorProvider().install();
        assertThat(SpectorExecutors.current()).isSameAs(provider);

        CountDownLatch latch = new CountDownLatch(3);

        provider.executor(ThreadPlane.VIRTUAL, "bench-vt").execute(latch::countDown);
        provider.executor(ThreadPlane.PLATFORM_SHARED, "bench-shared").execute(latch::countDown);
        provider.executor(ThreadPlane.PLATFORM_WRITER, "bench-writer").execute(latch::countDown);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(provider.virtualTaskCount()).isEqualTo(1);
        assertThat(provider.sharedTaskCount()).isEqualTo(1);
        assertThat(provider.writerTaskCount()).isEqualTo(1);
        assertThat(provider.describe()).contains("virtualTasks=1");

        provider.resetMetrics();
        assertThat(provider.virtualTaskCount()).isEqualTo(0);
        assertThat(provider.sharedTaskCount()).isEqualTo(0);
        assertThat(provider.writerTaskCount()).isEqualTo(0);

        provider.close();
    }
}
