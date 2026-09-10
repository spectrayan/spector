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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * High-performance, generic, type-safe asynchronous task queue for Spector adhering to Model B (submit-only).
 *
 * <h3>Key Capabilities</h3>
 * <ul>
 *   <li><b>Priority &amp; FIFO Scheduling:</b> Backed by {@link PriorityBlockingQueue} utilizing
 *       {@link TaskPriority} and monotonic sequence tiebreakers.</li>
 *   <li><b>Model B Executor Submission:</b> Never creates or starts raw threads. Submits worker loops
 *       directly onto a host-injected or SPI-resolved {@link Executor}.</li>
 *   <li><b>Bounded Queue Buffer:</b> Strictly bounded capacity with {@link BackpressurePolicy#BLOCK}
 *       and sequence-based {@link BackpressurePolicy#DROP_OLDEST}.</li>
 *   <li><b>Batch Drain Lock Amortization:</b> Drains up to {@link TaskQueueConfig#batchDrainSize()}
 *       tasks for batch processing via {@link BatchTaskHandler}.</li>
 *   <li><b>Scoped Context Propagation:</b> Automatically restores Java 25 {@link MemoryScope#SESSION_ID}
 *       and {@link MemoryScope#NAMESPACE_ID} in executing worker threads.</li>
 *   <li><b>Transient Error Retries:</b> Automatically retries transient failures with exponential backoff.</li>
 *   <li><b>Graceful Drain:</b> Participates in coordinated component shutdown up to {@link TaskQueueConfig#drainTimeoutMs()}.</li>
 * </ul>
 *
 * @param <T> payload type
 */
public final class SpectorTaskQueue<T> implements AutoCloseable {

    private static final System.Logger log = System.getLogger(SpectorTaskQueue.class.getName());

    /**
     * Functional handler responsible for executing a single task payload.
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
    private final BatchTaskHandler<T> batchHandler;
    private final Predicate<ScopedTask<T>> cancellationCheck;
    private final Executor workerExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Concurrency control for strictly bounded capacity and BLOCK backpressure
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final AtomicInteger inFlightWorkers = new AtomicInteger(0);
    private final java.util.Set<Thread> workerThreads = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        this(name, config, handler, null, null);
    }

    public SpectorTaskQueue(
            String name,
            TaskQueueConfig config,
            TaskHandler<T> handler,
            Predicate<ScopedTask<T>> cancellationCheck) {
        this(name, config, handler, cancellationCheck, null);
    }

    public SpectorTaskQueue(
            String name,
            TaskQueueConfig config,
            TaskHandler<T> handler,
            Predicate<ScopedTask<T>> cancellationCheck,
            Executor workerExecutor) {
        this(name, config, handler, null, cancellationCheck, workerExecutor);
    }

    public static <T> SpectorTaskQueue<T> batched(
            String name,
            TaskQueueConfig config,
            BatchTaskHandler<T> batchHandler) {
        return new SpectorTaskQueue<>(name, config, null, batchHandler, null, null);
    }

    public static <T> SpectorTaskQueue<T> batched(
            String name,
            TaskQueueConfig config,
            BatchTaskHandler<T> batchHandler,
            Predicate<ScopedTask<T>> cancellationCheck) {
        return new SpectorTaskQueue<>(name, config, null, batchHandler, cancellationCheck, null);
    }

    public static <T> SpectorTaskQueue<T> batched(
            String name,
            TaskQueueConfig config,
            BatchTaskHandler<T> batchHandler,
            Predicate<ScopedTask<T>> cancellationCheck,
            Executor workerExecutor) {
        return new SpectorTaskQueue<>(name, config, null, batchHandler, cancellationCheck, workerExecutor);
    }

    private SpectorTaskQueue(
            String name,
            TaskQueueConfig config,
            TaskHandler<T> handler,
            BatchTaskHandler<T> batchHandler,
            Predicate<ScopedTask<T>> cancellationCheck,
            Executor workerExecutor) {
        if (name == null || name.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "name");
        }
        if (handler == null && batchHandler == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "handler or batchHandler");
        }
        this.name = name;
        this.config = config != null ? config : TaskQueueConfig.ofDefaults();
        this.handler = handler;
        this.batchHandler = batchHandler;
        this.cancellationCheck = cancellationCheck;
        this.queue = new PriorityBlockingQueue<>(this.config.capacity());

        // Resolve executor from argument or SpectorExecutors locator
        Executor targetExecutor = workerExecutor != null
                ? workerExecutor
                : SpectorExecutors.executor(this.config.plane(), this.name);
        this.workerExecutor = Objects.requireNonNull(targetExecutor, "workerExecutor must not be null");

        // Register in central manager
        TaskQueueManager.register(this.name, this);

        // Submit worker loops onto VIRTUAL plane (Model B: submit, do not start)
        // Polling loop runs on virtual threads so queue.poll() never occupies PLATFORM_WRITER
        Executor loopExecutor = SpectorExecutors.executor(ThreadPlane.VIRTUAL, "tq-worker-" + this.name);
        for (int i = 0; i < this.config.parallelism(); i++) {
            loopExecutor.execute(this::workerLoop);
        }

        log.log(System.Logger.Level.INFO,
                "[SpectorTaskQueue:{0}] Submitted {1} workers to plane [{2}] (capacity={3}, pollTimeoutMs={4}, maxRetries={5}, policy={6}, batchDrainSize={7})",
                this.name, this.config.parallelism(), this.config.plane(), this.config.capacity(),
                this.config.pollTimeoutMs(), this.config.maxRetries(), this.config.backpressurePolicy(),
                this.config.batchDrainSize());
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
     * Submits a pre-constructed {@link ScopedTask} adhering to the configured {@link BackpressurePolicy}.
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

        lock.lock();
        try {
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
                        // Evict task with minimum submittedAtMs (oldest enqueue order) over snapshot,
                        // eliminating racy iterator traversal over PriorityBlockingQueue while workers poll
                        Object[] snapshot = queue.toArray();
                        ScopedTask<T> oldest = null;
                        for (Object elem : snapshot) {
                            if (elem instanceof ScopedTask<?> candidate) {
                                @SuppressWarnings("unchecked")
                                ScopedTask<T> typed = (ScopedTask<T>) candidate;
                                if (oldest == null || typed.submittedAtMs() < oldest.submittedAtMs()) {
                                    oldest = typed;
                                }
                            }
                        }
                        if (oldest != null && queue.remove(oldest)) {
                            failedCount.incrementAndGet();
                            log.log(System.Logger.Level.WARNING,
                                    "[{0}] Queue full ({1}/{2}) - dropped oldest task ''{3}'' (priority={4}, submittedAt={5})",
                                    name, queue.size(), config.capacity(), oldest.taskId(),
                                    oldest.priority(), oldest.submittedAtMs());
                        }
                    }
                    case CALLER_RUNS -> {
                        if (config.plane() == ThreadPlane.PLATFORM_WRITER) {
                            failedCount.incrementAndGet();
                            log.log(System.Logger.Level.ERROR,
                                    "[{0}] CALLER_RUNS is strictly forbidden on PLATFORM_WRITER queues; rejected ''{1}''",
                                    name, task.taskId());
                            return false;
                        }
                        lock.unlock();
                        try {
                            executeSingleTask(task);
                            submittedCount.incrementAndGet();
                            processedCount.incrementAndGet();
                            return true;
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            log.log(System.Logger.Level.ERROR,
                                    "[{0}] CALLER_RUNS task ''{1}'' failed: {2}",
                                    name, task.taskId(), e.getMessage(), e);
                            return false;
                        } finally {
                            lock.lock();
                        }
                    }
                    case BLOCK -> {
                        while (!closed.get() && queue.size() >= config.capacity()) {
                            try {
                                notFull.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                failedCount.incrementAndGet();
                                log.log(System.Logger.Level.WARNING,
                                        "[{0}] Interrupted while blocking on full queue for task ''{1}''",
                                        name, task.taskId());
                                return false;
                            }
                        }
                        if (closed.get()) {
                            failedCount.incrementAndGet();
                            return false;
                        }
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
        } finally {
            lock.unlock();
        }
    }

    private void signalNotFull() {
        lock.lock();
        try {
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void workerLoop() {
        Thread current = Thread.currentThread();
        workerThreads.add(current);
        try {
            while (!current.isInterrupted()) {
                if (closed.get() && queue.isEmpty()) {
                    break;
                }
                try {
                    ScopedTask<T> first = queue.poll(config.pollTimeoutMs(), TimeUnit.MILLISECONDS);
                    if (first != null) {
                        inFlightWorkers.incrementAndGet();
                        try {
                            if (batchHandler != null && config.batchDrainSize() > 1) {
                                List<ScopedTask<T>> batch = new ArrayList<>(config.batchDrainSize());
                                batch.add(first);
                                queue.drainTo(batch, config.batchDrainSize() - 1);
                                signalNotFull();
                                dispatchBatch(batch);
                            } else {
                                signalNotFull();
                                if (batchHandler != null) {
                                    dispatchBatch(List.of(first));
                                } else {
                                    dispatchDirect(first);
                                }
                            }
                        } finally {
                            inFlightWorkers.decrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    current.interrupt();
                    log.log(System.Logger.Level.DEBUG, "[{0}] Worker interrupted during poll, exiting loop", name);
                    break;
                } catch (Exception e) {
                    log.log(System.Logger.Level.WARNING, "[{0}] Unexpected error in worker loop: {1}", name, e.getMessage());
                }
            }
        } finally {
            workerThreads.remove(current);
        }
    }

    private void dispatchBatch(List<ScopedTask<T>> batch) {
        List<ScopedTask<T>> activeTasks = new ArrayList<>(batch.size());
        for (ScopedTask<T> task : batch) {
            if (cancellationCheck != null && cancellationCheck.test(task)) {
                log.log(System.Logger.Level.DEBUG, "[{0}] Skipped cancelled/tombstoned task ''{1}''", name, task.taskId());
                processedCount.incrementAndGet();
            } else {
                activeTasks.add(task);
            }
        }
        if (activeTasks.isEmpty()) {
            return;
        }

        int attempts = 0;
        int maxAttempts = 1 + Math.max(0, config.maxRetries());
        Throwable lastException = null;

        while (attempts < maxAttempts) {
            attempts++;
            long start = System.currentTimeMillis();
            try {
                if (config.plane() == ThreadPlane.PLATFORM_WRITER) {
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                    try {
                        workerExecutor.execute(() -> {
                            try {
                                executeSingleBatch(activeTasks);
                            } catch (Throwable t) {
                                errorRef.set(t);
                            } finally {
                                latch.countDown();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException ree) {
                        errorRef.set(ree);
                        latch.countDown();
                    }
                    try {
                        latch.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    if (errorRef.get() != null) {
                        throw errorRef.get();
                    }
                } else {
                    executeSingleBatch(activeTasks);
                }

                long duration = System.currentTimeMillis() - start;
                totalDurationMs.addAndGet(duration);
                processedCount.addAndGet(activeTasks.size());
                log.log(System.Logger.Level.DEBUG,
                        "[{0}] Processed batch of {1} tasks in {2} ms (attempts={3}, backlog={4})",
                        name, activeTasks.size(), duration, attempts, queue.size());
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastException = ie;
                break;
            } catch (Throwable e) {
                lastException = e;
                if (attempts < maxAttempts) {
                    retriedCount.incrementAndGet();
                    log.log(System.Logger.Level.WARNING,
                            "[{0}] Batch execution failed on attempt {1}/{2}, retrying on worker loop in {3} ms: {4}",
                            name, attempts, maxAttempts, config.retryBackoffMs(), e.getMessage());
                    try {
                        Thread.sleep(config.retryBackoffMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        failedCount.addAndGet(activeTasks.size());
        log.log(System.Logger.Level.ERROR,
                "[{0}] Batch of {1} tasks permanently failed after {2} attempts: {3}",
                name, activeTasks.size(), maxAttempts, lastException != null ? lastException.getMessage() : "unknown", lastException);
    }

    private void executeSingleBatch(List<ScopedTask<T>> activeTasks) {
        ScopedTask<T> primary = activeTasks.getFirst();
        MemoryScope.runWithScope(primary.sessionId(), primary.namespaceId(), () -> {
            try {
                batchHandler.handleBatch(activeTasks);
            } catch (Exception e) {
                throw new SpectorServerException(ErrorCode.TASK_EXECUTION_FAILED, e, primary.taskId(), name, e.getMessage());
            }
        });
    }

    private void dispatchDirect(ScopedTask<T> task) {
        if (cancellationCheck != null && cancellationCheck.test(task)) {
            log.log(System.Logger.Level.DEBUG, "[{0}] Skipped cancelled/tombstoned task ''{1}''", name, task.taskId());
            processedCount.incrementAndGet();
            return;
        }

        int attempts = 0;
        int maxAttempts = 1 + Math.max(0, config.maxRetries());
        Throwable lastException = null;

        while (attempts < maxAttempts) {
            attempts++;
            long start = System.currentTimeMillis();
            try {
                if (config.plane() == ThreadPlane.PLATFORM_WRITER) {
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                    try {
                        workerExecutor.execute(() -> {
                            try {
                                executeSingleTask(task);
                            } catch (Throwable t) {
                                errorRef.set(t);
                            } finally {
                                latch.countDown();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException ree) {
                        errorRef.set(ree);
                        latch.countDown();
                    }
                    try {
                        latch.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    if (errorRef.get() != null) {
                        throw errorRef.get();
                    }
                } else {
                    executeSingleTask(task);
                }

                long duration = System.currentTimeMillis() - start;
                totalDurationMs.addAndGet(duration);
                processedCount.incrementAndGet();
                log.log(System.Logger.Level.DEBUG,
                        "[{0}] Processed task ''{1}'' in {2} ms (attempts={3}, backlog={4})",
                        name, task.taskId(), duration, attempts, queue.size());
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastException = ie;
                break;
            } catch (Throwable e) {
                lastException = e;
                if (attempts < maxAttempts) {
                    retriedCount.incrementAndGet();
                    log.log(System.Logger.Level.WARNING,
                            "[{0}] Task ''{1}'' failed attempt {2}/{3} ({4}), retrying on worker loop in {5} ms...",
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
        log.log(System.Logger.Level.ERROR,
                "[{0}] Task ''{1}'' permanently failed after {2} attempts: {3}",
                name, task.taskId(), attempts, lastException != null ? lastException.getMessage() : "unknown", lastException);
    }

    private void executeSingleTask(ScopedTask<T> task) {
        MemoryScope.runWithScope(task.sessionId(), task.namespaceId(), () -> {
            try {
                handler.handle(task);
            } catch (Exception e) {
                throw new SpectorServerException(ErrorCode.TASK_EXECUTION_FAILED, e, task.taskId(), name, e.getMessage());
            }
        });
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

    public int inFlightCount() {
        return inFlightWorkers.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            TaskQueueManager.unregister(this.name);
            signalNotFull();

            if (queue.isEmpty() && inFlightWorkers.get() == 0) {
                for (Thread worker : workerThreads) {
                    worker.interrupt();
                }
            }

            log.log(System.Logger.Level.INFO,
                    "[{0}] Draining queue (backlog={1}, inFlight={2}) with {3} ms timeout...",
                    name, queue.size(), inFlightWorkers.get(), config.drainTimeoutMs());
            long deadline = System.currentTimeMillis() + config.drainTimeoutMs();
            while ((!queue.isEmpty() || inFlightWorkers.get() > 0) && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.log(System.Logger.Level.WARNING, "[{0}] Interrupted while draining queue", name);
                    break;
                }
            }
            for (Thread worker : workerThreads) {
                worker.interrupt();
            }
            int remaining = queue.size();
            if (remaining > 0) {
                failedCount.addAndGet(remaining);
                log.log(System.Logger.Level.WARNING,
                        "[{0}] Closed with {1} unprocessed tasks discarded (drain timeout {2} ms elapsed)",
                        name, remaining, config.drainTimeoutMs());
                queue.clear();
            }
            log.log(System.Logger.Level.INFO,
                    "[{0}] Closed. Drained stats: processed={1}, failed={2}, retried={3}, remaining={4}",
                    name, processedCount.get(), failedCount.get(), retriedCount.get(), queue.size());
        }
    }
}
