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
package com.spectrayan.spector.metrics;

import com.spectrayan.spector.commons.concurrent.BackpressurePolicy;
import com.spectrayan.spector.commons.concurrent.SpectorTaskQueue;
import com.spectrayan.spector.commons.concurrent.TaskQueueConfig;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskQueueMetricsBinder")
class TaskQueueMetricsBinderTest {

    @Test
    @DisplayName("Binds all queue gauges and counters to Micrometer registry")
    void testMetricsBinding() throws Exception {
        var registry = new SimpleMeterRegistry();
        TaskQueueConfig config = new TaskQueueConfig(50, 1, 100, 1000, 0, 0, BackpressurePolicy.REJECT_FAST);
        CountDownLatch latch = new CountDownLatch(2);

        try (var queue = new SpectorTaskQueue<String>("test-metered-queue", config, task -> latch.countDown())) {
            TaskQueueMetricsBinder.bind(queue, registry);

            queue.submit("t1", "p1");
            queue.submit("t2", "p2");

            boolean done = latch.await(3, TimeUnit.SECONDS);
            assertThat(done).isTrue();

            var sizeGauge = registry.find("spector.taskqueue.size").tag("queue", "test-metered-queue").gauge();
            assertThat(sizeGauge).isNotNull();

            var capacityGauge = registry.find("spector.taskqueue.capacity").tag("queue", "test-metered-queue").gauge();
            assertThat(capacityGauge).isNotNull();
            assertThat(capacityGauge.value()).isEqualTo(50.0);

            var parallelismGauge = registry.find("spector.taskqueue.parallelism").tag("queue", "test-metered-queue").gauge();
            assertThat(parallelismGauge).isNotNull();
            assertThat(parallelismGauge.value()).isEqualTo(1.0);

            var submittedCounter = registry.find("spector.taskqueue.submitted").tag("queue", "test-metered-queue").functionCounter();
            assertThat(submittedCounter).isNotNull();
            assertThat(submittedCounter.count()).isEqualTo(2.0);

            var processedCounter = registry.find("spector.taskqueue.processed").tag("queue", "test-metered-queue").functionCounter();
            assertThat(processedCounter).isNotNull();
            assertThat(processedCounter.count()).isEqualTo(2.0);

            var runningGauge = registry.find("spector.taskqueue.running").tag("queue", "test-metered-queue").gauge();
            assertThat(runningGauge).isNotNull();
            assertThat(runningGauge.value()).isEqualTo(1.0);
        }
    }
}
