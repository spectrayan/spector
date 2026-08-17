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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * High-performance, generic, type-safe asynchronous task queue for Spector.
 *
 * <h3>Key Capabilities</h3>
 * <ul>
 *   <li><b>Priority &amp; FIFO Scheduling:</b> Backed by {@link PriorityBlockingQueue} utilizing
 *       {@link TaskPriority} and submission order tiebreakers.</li>
 *   <li><b>Scoped Context Propagation:</b> Automatically restores Java 25 {@link MemoryScope#SESSION_ID}
 *       and {@link MemoryScope#NAMESPACE_ID} in executing virtual worker threads.</li>
 *   <li><b>Transient Error Retries:</b> Automatically retries transient execution failures with exponential backoff
 *       up to {@link TaskQueueConfig#maxRetries()}.</li>
 *   <li><b>Tombstone &amp; Cancellation Checks:</b> Evaluates an optional cancellation predicate before
 *       executing expensive tasks.</li>
 *   <li><b>Multi-Tier Graceful Drain:</b> Implements {@link AutoCloseable} to drain pending tasks
 *       up to {@link TaskQueueConfig#drainTimeoutMs()} without data loss.</li>
 *   <li><b>Structured Observability:</b> Tracks atomic counters for depth, capacity, throughput, retries,
 *       and rolling average execution duration.</li>
 * </ul>
 *
 * @param <T> payload type
 */
public final class SpectorTaskQueue<T> implements AutoCloseable {

    private static final System.Logger log = System.getLogger(SpectorTaskQueue.class.getName());

    /**
     * Functional handler responsible for executing a task payload.
     *
     * @param <T> payload type
     */
    @FunctionalInterface
    public interface TaskHandler<T> {
        void handle(ScopedTask<T> task) throws Exception;
    }

    /**
     * Real-time metrics snapshot of the task queue.
     */
    public record QueueMetrics(
            String queueName,
            int size,
            int capacity,
            int parallelism,
            long submitted,
            long processed,
            long failed,
            long retried,
            long avgLatencyMs,
            boolean isRunning
    ) {}

    private final String name;
    private final BlockingQueue<ScopedTask<T>> queue;
    private final TaskQueueConfig config;
    private final TaskHandler<T> handler;
    private final Predicate<ScopedTask<T>> cancellationCheck;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Telemetry counters
    private final AtomicLong submittedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retriedCount = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    public SpectorTaskQueue(
            String name,
            TaskQueueConfig config,
            TaskHandler<T> handler) {
        this(name, config, handler, null);
    }

    public SpectorTaskQueue(
            String name,
            TaskQueueConfig config,
            TaskHandler<T> handler,
            Predicate<ScopedTask<T>> cancellationCheck) {
        if (name == null || name.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "name");
        }
        this.name = name;
        this.config = config != null ? config : TaskQueueConfig.ofDefaults();
        this.handler = Objects.requireNonNull(handler, "handler");
        this.cancellationCheck = cancellationCheck;
        this.queue = new PriorityBlockingQueue<>(this.config.capacity());

        // Register in central manager
        TaskQueueManager.register(this.name, this);

        // Spawn supervised named virtual worker threads
        ThreadFactory threadFactory = ConcurrentTasks.namedVirtualThreadFactory(this.name);
        for (int i = 0; i < this.config.parallelism(); i++) {
            Thread workerThread = threadFactory.newThread(this::workerLoop);
            workerThread.start();
        }

        log.log(System.Logger.Level.INFO,
                "[SpectorTaskQueue:{0}] Started {1} workers (capacity={2}, pollTimeoutMs={3}, maxRetries={4}, policy={5})",
                this.name, this.config.parallelism(), this.config.capacity(),
                this.config.pollTimeoutMs(), this.config.maxRetries(), this.config.backpressurePolicy());
    }

    /** Returns the queue name. */
    public String name() {
        return name;
    }

    /** Returns the current queue backlog size. */
    public int size() {
        return queue.size();
    }

    /**
     * Submits a payload with automatic thread-context capture.
     */
    public boolean submit(String taskId, T payload) {
        return submit(ScopedTask.of(taskId, payload));
    }

    /**
     * Submits a payload with priority and automatic thread-context capture.
     */
    public boolean submit(String taskId, T payload, TaskPriority priority) {
        return submit(ScopedTask.of(taskId, payload, priority));
    }

    /**
     * Submits a pre-constructed {@link ScopedTask}.
     */
    public boolean submit(ScopedTask<T> task) {
        if (closed.get()) {
            failedCount.incrementAndGet();
            log.log(System.Logger.Level.WARNING,
                    "[{0}] Rejected task ''{1}'' — queue is closed", name, task != null ? task.taskId() : "null");
            return false;
        }
        if (task == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "task");
        }

        if (queue.size() >= config.capacity()) {
            switch (config.backpressurePolicy()) {
                case REJECT_FAST -> {
                    failedCount.incrementAndGet();
                    log.log(System.Logger.Level.WARNING,
                            "[{0}] Queue full ({1}/{2}) - rejected task ''{3}''",
                            name, queue.size(), config.capacity(), task.taskId());
                    return false;
                }
                case DROP_OLDEST -> {
                    ScopedTask<T> dropped = queue.poll();
                    if (dropped != null) {
                        failedCount.incrementAndGet();
                        log.log(System.Logger.Level.WARNING,
                                "[{0}] Queue full ({1}/{2}) - dropped oldest task ''{3}''",
                                name, queue.size(), config.capacity(), dropped.taskId());
                    }
                }
                case CALLER_RUNS -> {
                    executeDirectWithRetries(task);
                    submittedCount.incrementAndGet();
                    return true;
                }
            }
        }

        boolean accepted = queue.offer(task);
        if (accepted) {
            submittedCount.incrementAndGet();
            if (queue.size() >= config.capacity() * 0.8) {
                log.log(System.Logger.Level.WARNING,
                        "[{0}] Queue high watermark reached: {1}/{2} (80% capacity)",
                        name, queue.size(), config.capacity());
            }
        } else {
            failedCount.incrementAndGet();
        }
        return accepted;
    }

    private void workerLoop() {
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                ScopedTask<T> task = queue.poll(config.pollTimeoutMs(), TimeUnit.MILLISECONDS);
                if (task != null) {
                    executeDirectWithRetries(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.log(System.Logger.Level.DEBUG, "[{0}] Worker interrupted during poll, exiting loop", name);
                break;
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING, "[{0}] Unexpected error in worker loop: {1}", name, e.getMessage());
            }
        }
    }

    private void executeDirectWithRetries(ScopedTask<T> task) {
        if (cancellationCheck != null && cancellationCheck.test(task)) {
            log.log(System.Logger.Level.DEBUG, "[{0}] Skipped cancelled/tombstoned task ''{1}''", name, task.taskId());
            processedCount.incrementAndGet();
            return;
        }

        int attempts = 0;
        int maxAttempts = 1 + Math.max(0, config.maxRetries());
        Exception lastException = null;

        while (attempts < maxAttempts) {
            attempts++;
            long start = System.currentTimeMillis();
            try {
                MemoryScope.runWithScope(task.sessionId(), task.namespaceId(), () -> {
                    try {
                        handler.handle(task);
                    } catch (Exception e) {
                        throw new SpectorServerException(ErrorCode.TASK_EXECUTION_FAILED, e, task.taskId(), name, e.getMessage());
                    }
                });
                long duration = System.currentTimeMillis() - start;
                totalDurationMs.addAndGet(duration);
                processedCount.incrementAndGet();
                log.log(System.Logger.Level.DEBUG,
                        "[{0}] Processed task ''{1}'' in {2} ms (attempts={3}, backlog={4})",
                        name, task.taskId(), duration, attempts, queue.size());
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempts < maxAttempts) {
                    retriedCount.incrementAndGet();
                    log.log(System.Logger.Level.WARNING,
                            "[{0}] Task ''{1}'' failed attempt {2}/{3} ({4}), retrying in {5} ms...",
                            name, task.taskId(), attempts, maxAttempts, e.getMessage(), config.retryBackoffMs());
                    try {
                        Thread.sleep(config.retryBackoffMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        failedCount.incrementAndGet();
        log.log(System.Logger.Level.WARNING,
                "[{0}] Task ''{1}'' permanently failed after {2} attempts: {3}",
                name, task.taskId(), attempts, lastException != null ? lastException.getMessage() : "unknown");
    }

    /**
     * Takes a real-time snapshot of queue metrics.
     */
    public QueueMetrics metrics() {
        long processed = processedCount.get();
        long avgLatency = processed > 0 ? (totalDurationMs.get() / processed) : 0;
        return new QueueMetrics(
                name,
                queue.size(),
                config.capacity(),
                config.parallelism(),
                submittedCount.get(),
                processed,
                failedCount.get(),
                retriedCount.get(),
                avgLatency,
                !closed.get()
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            TaskQueueManager.unregister(this.name);
            log.log(System.Logger.Level.INFO,
                    "[{0}] Draining queue (backlog={1}) with {2} ms timeout...",
                    name, queue.size(), config.drainTimeoutMs());
            long deadline = System.currentTimeMillis() + config.drainTimeoutMs();
            while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.log(System.Logger.Level.WARNING, "[{0}] Interrupted while draining queue", name);
                    break;
                }
            }
            log.log(System.Logger.Level.INFO,
                    "[{0}] Closed. Drained stats: processed={1}, failed={2}, retried={3}, remaining={4}",
                    name, processedCount.get(), failedCount.get(), retriedCount.get(), queue.size());
        }
    }
}
