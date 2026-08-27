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

import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quartz {@link ThreadPool} SPI implementation that delegates all job executions to a supplied
 * {@link Executor} (by default, {@link ConcurrentTasks#virtualExecutor()}).
 *
 * <h3>Purpose</h3>
 * <p>Standard Quartz ships with platform-thread based pools (e.g. {@code SimpleThreadPool}).
 * This SPI bridge connects Quartz directly to Spector's native Java 25 virtual thread concurrency
 * framework, ensuring all background cognitive and maintenance jobs run asynchronously without thread exhaustion.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Explicit construction with custom executor or ConcurrentTasks:
 * var pool = new VirtualThreadPool(ConcurrentTasks.virtualExecutor());
 *
 * // Or instantiated reflectively by Quartz DirectSchedulerFactory:
 * DirectSchedulerFactory.getInstance().createScheduler("sched", "ID", pool, new RAMJobStore());
 * }</pre>
 *
 * @since 1.4.0
 */
public final class VirtualThreadPool implements ThreadPool {

    private static final System.Logger log = System.getLogger(VirtualThreadPool.class.getName());

    private final Executor executor;
    private final AtomicLong threadOrdinal = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String instanceName = "spector-quartz";

    /**
     * Creates a {@code VirtualThreadPool} delegating to the specified {@link Executor}.
     *
     * @param executor the target executor to execute jobs on (non-null)
     */
    public VirtualThreadPool(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Default no-arg constructor required by Quartz reflective initialization.
     * Delegates to {@link ConcurrentTasks#virtualExecutor()}.
     */
    public VirtualThreadPool() {
        this(ConcurrentTasks.virtualExecutor());
    }

    @Override
    public boolean runInThread(Runnable runnable) {
        if (runnable == null) {
            return false;
        }

        try {
            long ordinal = threadOrdinal.incrementAndGet();
            executor.execute(() -> {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    log.log(System.Logger.Level.ERROR,
                            "Uncaught error in Quartz virtual task execution [{0}-run-{1}]: {2}",
                            instanceName, ordinal, t.getMessage(), t);
                }
            });
            return true;
        } catch (Throwable t) {
            log.log(System.Logger.Level.ERROR,
                    "Failed to submit Quartz runnable to virtual thread executor for [{0}]: {1}",
                    instanceName, t.getMessage(), t);
            return false;
        }
    }

    @Override
    public int blockForAvailableThreads() {
        // Virtual threads are lightweight and virtually unconstrained
        return 1000;
    }

    @Override
    public int getPoolSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void initialize() throws SchedulerConfigException {
        running.set(true);
        log.log(System.Logger.Level.DEBUG, "VirtualThreadPool initialized for scheduler [{0}]", instanceName);
    }

    @Override
    public void shutdown(boolean waitForJobsToComplete) {
        running.set(false);
        log.log(System.Logger.Level.DEBUG, "VirtualThreadPool shut down for scheduler [{0}]", instanceName);
    }

    @Override
    public void setInstanceId(String schedInstId) {
        // Instance ID retained for Quartz SPI contract
    }

    @Override
    public void setInstanceName(String schedName) {
        if (schedName != null && !schedName.isBlank()) {
            this.instanceName = schedName;
        }
    }

    /**
     * Returns the underlying {@link Executor} backing this thread pool.
     */
    public Executor executor() {
        return executor;
    }

    /**
     * Returns the configured scheduler instance name.
     */
    public String instanceName() {
        return instanceName;
    }

    /**
     * Returns whether this thread pool is initialized and active.
     */
    public boolean isRunning() {
        return running.get();
    }
}
