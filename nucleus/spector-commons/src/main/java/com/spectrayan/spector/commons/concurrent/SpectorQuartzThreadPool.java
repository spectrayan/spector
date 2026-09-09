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

import com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider;
import org.quartz.JobDetail;
import org.quartz.SchedulerConfigException;
import org.quartz.spi.ThreadPool;
import org.quartz.spi.TriggerFiredBundle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quartz {@link ThreadPool} SPI implementation that routes jobs onto designated {@link ThreadPlane} executors
 * provided by {@link SpectorExecutorProvider}.
 *
 * <p>Unlike legacy virtual thread pools, this pool prevents unbounded virtual thread dispatch of native write
 * operations (e.g. checkpoints and sleep consolidation) by inspecting {@link OnPlane} annotations and
 * {@code JobDataMap} entries.</p>
 */
public final class SpectorQuartzThreadPool implements ThreadPool {

    private static final System.Logger log = System.getLogger(SpectorQuartzThreadPool.class.getName());

    public static final String JOB_DATA_PLANE = "spector.threadPlane";
    public static final String JOB_DATA_POOL = "spector.poolName";

    private static final VarHandle FIRED_BUNDLE_HANDLE;

    static {
        VarHandle handle = null;
        try {
            Class<?> shellClass = Class.forName("org.quartz.core.JobRunShell");
            Field bundleField = shellClass.getDeclaredField("firedTriggerBundle");
            bundleField.setAccessible(true);
            handle = MethodHandles.lookup().unreflectVarHandle(bundleField);
        } catch (Throwable t) {
            log.log(System.Logger.Level.DEBUG, "Could not reflect firedTriggerBundle from JobRunShell: {0}", t.getMessage());
        }
        FIRED_BUNDLE_HANDLE = handle;
    }

    private final SpectorExecutorProvider provider;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String instanceName = "spector-quartz";

    /**
     * Reflective constructor required by Quartz scheduler factories.
     * Uses {@link SpectorExecutors#current()}.
     */
    public SpectorQuartzThreadPool() {
        this(null);
    }

    /**
     * Constructs a {@code SpectorQuartzThreadPool} with an explicit {@link SpectorExecutorProvider}.
     *
     * @param provider the executor provider (nullable, falls back to {@link SpectorExecutors#current()})
     */
    public SpectorQuartzThreadPool(SpectorExecutorProvider provider) {
        this.provider = provider;
    }

    private SpectorExecutorProvider activeProvider() {
        return provider != null ? provider : SpectorExecutors.current();
    }

    @Override
    public boolean runInThread(Runnable runnable) {
        if (!running.get() || runnable == null) {
            return false;
        }

        ThreadPlane plane = ThreadPlane.VIRTUAL;
        String pool = "quartz";

        if (runnable instanceof PlaneAware planeAware) {
            plane = planeAware.plane();
            pool = planeAware.poolName();
        } else {
            JobDetail detail = extractJobDetail(runnable);
            if (detail != null) {
                if (detail.getJobDataMap() != null && detail.getJobDataMap().containsKey(JOB_DATA_PLANE)) {
                    Object planeObj = detail.getJobDataMap().get(JOB_DATA_PLANE);
                    if (planeObj instanceof ThreadPlane tp) {
                        plane = tp;
                    } else if (planeObj instanceof String str) {
                        try {
                            plane = ThreadPlane.valueOf(str.trim());
                        } catch (IllegalArgumentException ignored) {}
                    }
                } else if (detail.getJobClass() != null && detail.getJobClass().isAnnotationPresent(OnPlane.class)) {
                    OnPlane annotation = detail.getJobClass().getAnnotation(OnPlane.class);
                    plane = annotation.value();
                    pool = annotation.pool();
                }

                if (detail.getJobDataMap() != null && detail.getJobDataMap().containsKey(JOB_DATA_POOL)) {
                    String customPool = detail.getJobDataMap().getString(JOB_DATA_POOL);
                    if (customPool != null && !customPool.isBlank()) {
                        pool = customPool;
                    }
                }
            }
        }

        inFlight.incrementAndGet();
        try {
            activeProvider().executor(plane, pool).execute(() -> {
                try {
                    runnable.run();
                } finally {
                    inFlight.decrementAndGet();
                }
            });
            return true;
        } catch (Throwable t) {
            inFlight.decrementAndGet();
            log.log(System.Logger.Level.ERROR,
                    "Failed to dispatch Quartz job to plane [{0}:{1}]: {2}",
                    plane, pool, t.getMessage(), t);
            return false;
        }
    }

    private JobDetail extractJobDetail(Runnable runnable) {
        if (FIRED_BUNDLE_HANDLE != null && runnable != null) {
            try {
                Object bundleObj = FIRED_BUNDLE_HANDLE.get(runnable);
                if (bundleObj instanceof TriggerFiredBundle bundle) {
                    return bundle.getJobDetail();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @Override
    public int blockForAvailableThreads() {
        // Writer plane is single-threaded; report 1 so Quartz throttles misfires appropriately
        return 1;
    }

    @Override
    public int getPoolSize() {
        return Math.max(1, inFlight.get() + 1);
    }

    @Override
    public void initialize() throws SchedulerConfigException {
        running.set(true);
        log.log(System.Logger.Level.DEBUG, "SpectorQuartzThreadPool initialized for [{0}]", instanceName);
    }

    @Override
    public void shutdown(boolean waitForJobsToComplete) {
        running.set(false);
        if (waitForJobsToComplete) {
            long deadlineNanos = System.nanoTime() + 10_000_000_000L; // 10 second fallback wait
            while (inFlight.get() > 0 && System.nanoTime() < deadlineNanos) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.log(System.Logger.Level.DEBUG, "SpectorQuartzThreadPool shutdown completed for [{0}] (inFlight={1})",
                instanceName, inFlight.get());
    }

    @Override
    public void setInstanceId(String schedInstId) {}

    @Override
    public void setInstanceName(String schedName) {
        if (schedName != null && !schedName.isBlank()) {
            this.instanceName = schedName;
        }
    }

    /**
     * Returns the current number of in-flight Quartz jobs.
     */
    public int inFlightCount() {
        return inFlight.get();
    }

    /**
     * Returns whether this thread pool is running and accepting new jobs.
     */
    public boolean isRunning() {
        return running.get();
    }
}
