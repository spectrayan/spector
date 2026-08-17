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

import com.spectrayan.spector.commons.concurrent.SpectorTaskQueue;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.Objects;

/**
 * Micrometer {@link MeterBinder} that exports real-time telemetry from a {@link SpectorTaskQueue}.
 *
 * <h3>Exported Metrics</h3>
 * <ul>
 *   <li>{@code spector.taskqueue.size} — current backlog of tasks in the queue</li>
 *   <li>{@code spector.taskqueue.capacity} — maximum buffer capacity</li>
 *   <li>{@code spector.taskqueue.parallelism} — worker virtual thread count</li>
 *   <li>{@code spector.taskqueue.submitted} — total tasks accepted</li>
 *   <li>{@code spector.taskqueue.processed} — total tasks successfully processed</li>
 *   <li>{@code spector.taskqueue.failed} — total tasks rejected or permanently failed</li>
 *   <li>{@code spector.taskqueue.retried} — total retry attempts</li>
 *   <li>{@code spector.taskqueue.latency.avg.ms} — rolling average task duration (ms)</li>
 *   <li>{@code spector.taskqueue.running} — 1 if running, 0 if closed</li>
 * </ul>
 */
public final class TaskQueueMetricsBinder implements MeterBinder {

    private final SpectorTaskQueue<?> taskQueue;

    public TaskQueueMetricsBinder(SpectorTaskQueue<?> taskQueue) {
        if (taskQueue == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "taskQueue");
        }
        this.taskQueue = taskQueue;
    }

    /**
     * Helper to bind a queue directly to the global {@link SpectorMetrics#registry()}.
     */
    public static TaskQueueMetricsBinder bind(SpectorTaskQueue<?> taskQueue) {
        return bind(taskQueue, SpectorMetrics.registry());
    }

    /**
     * Helper to bind a queue to a specific {@link MeterRegistry}.
     */
    public static TaskQueueMetricsBinder bind(SpectorTaskQueue<?> taskQueue, MeterRegistry registry) {
        TaskQueueMetricsBinder binder = new TaskQueueMetricsBinder(taskQueue);
        binder.bindTo(registry);
        return binder;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        String name = taskQueue.name();

        Gauge.builder("spector.taskqueue.size", taskQueue, q -> q.metrics().size())
                .tag("queue", name)
                .description("Current backlog of tasks in the queue")
                .register(registry);

        Gauge.builder("spector.taskqueue.capacity", taskQueue, q -> q.metrics().capacity())
                .tag("queue", name)
                .description("Configured maximum capacity of the queue")
                .register(registry);

        Gauge.builder("spector.taskqueue.parallelism", taskQueue, q -> q.metrics().parallelism())
                .tag("queue", name)
                .description("Configured worker virtual thread parallelism")
                .register(registry);

        FunctionCounter.builder("spector.taskqueue.submitted", taskQueue, q -> q.metrics().submitted())
                .tag("queue", name)
                .description("Total number of tasks submitted")
                .register(registry);

        FunctionCounter.builder("spector.taskqueue.processed", taskQueue, q -> q.metrics().processed())
                .tag("queue", name)
                .description("Total number of tasks successfully processed")
                .register(registry);

        FunctionCounter.builder("spector.taskqueue.failed", taskQueue, q -> q.metrics().failed())
                .tag("queue", name)
                .description("Total number of failed or rejected tasks")
                .register(registry);

        FunctionCounter.builder("spector.taskqueue.retried", taskQueue, q -> q.metrics().retried())
                .tag("queue", name)
                .description("Total number of task retry attempts")
                .register(registry);

        Gauge.builder("spector.taskqueue.latency.avg.ms", taskQueue, q -> q.metrics().avgLatencyMs())
                .tag("queue", name)
                .description("Rolling average task processing latency in milliseconds")
                .register(registry);

        Gauge.builder("spector.taskqueue.running", taskQueue, q -> q.metrics().isRunning() ? 1.0 : 0.0)
                .tag("queue", name)
                .description("1.0 if queue is actively running, 0.0 if closed")
                .register(registry);
    }
}
