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

import com.spectrayan.spector.commons.concurrent.ThreadPlane;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Base implementation of {@link SpectorExecutorProvider} providing executor memoization
 * and cooperative shutdown/await draining of created {@link ExecutorService} instances.
 */
public abstract class AbstractExecutorProvider implements SpectorExecutorProvider, AutoCloseable {

    private static final System.Logger log = System.getLogger(AbstractExecutorProvider.class.getName());

    protected final ConcurrentHashMap<String, TrackedExecutor> executors = new ConcurrentHashMap<>();

    /**
     * Decorator that tracks in-flight tasks for cooperative draining across any underlying executor type.
     */
    public static final class TrackedExecutor implements Executor {
        private final Executor delegate;
        private final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger(0);

        public TrackedExecutor(Executor delegate) {
            this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        }

        public Executor delegate() {
            return delegate;
        }

        public int inFlight() {
            return inFlight.get();
        }

        @Override
        public void execute(Runnable command) {
            inFlight.incrementAndGet();
            try {
                delegate.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (Throwable t) {
                inFlight.decrementAndGet();
                throw t;
            }
        }
    }

    @Override
    public Executor executor(ThreadPlane plane, String name) {
        String poolName = name != null && !name.isBlank() ? name : "default";
        String key = plane.name() + ":" + poolName;
        return executors.compute(key, (k, existing) -> {
            if (existing != null && existing.delegate() instanceof ExecutorService service && service.isShutdown()) {
                return new TrackedExecutor(createExecutor(plane, poolName));
            }
            return existing != null ? existing : new TrackedExecutor(createExecutor(plane, poolName));
        });
    }

    /**
     * Factory method invoked to instantiate an executor when not already cached.
     *
     * @param plane target plane
     * @param name  normalized pool name
     * @return newly created executor
     */
    protected abstract Executor createExecutor(ThreadPlane plane, String name);

    /**
     * Extracts underlying {@link ThreadPoolExecutor} if supported by the executor implementation.
     *
     * @param executor delegate executor
     * @return underlying ThreadPoolExecutor or null
     */
    protected ThreadPoolExecutor extractThreadPoolExecutor(Executor executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe;
        }
        return null;
    }

    @Override
    public DrainResult drain(Duration budget) {
        return drain(null, budget);
    }

    @Override
    public DrainResult drain(String poolFilter, Duration budget) {
        long startNanos = System.nanoTime();
        long budgetNanos = budget.toNanos();
        boolean allCompleted = true;
        int remaining = 0;

        for (java.util.Map.Entry<String, TrackedExecutor> entry : executors.entrySet()) {
            if (poolFilter != null && !poolFilter.isBlank() && !entry.getKey().contains(poolFilter)) {
                continue;
            }
            TrackedExecutor tracked = entry.getValue();
            ThreadPoolExecutor tpe = extractThreadPoolExecutor(tracked.delegate());

            while (tracked.inFlight() > 0 || (tpe != null && (tpe.getActiveCount() > 0 || !tpe.getQueue().isEmpty()))) {
                long elapsedNanos = System.nanoTime() - startNanos;
                if (elapsedNanos >= budgetNanos) {
                    allCompleted = false;
                    remaining += tracked.inFlight() + (tpe != null ? tpe.getQueue().size() : 0);
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    allCompleted = false;
                    remaining += tracked.inFlight() + (tpe != null ? tpe.getQueue().size() : 0);
                    break;
                }
            }
        }

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        return new DrainResult(allCompleted, remaining, waited);
    }

    @Override
    public void close() {
        for (TrackedExecutor tracked : executors.values()) {
            Executor executor = tracked.delegate();
            if (executor instanceof ExecutorService service && !service.isShutdown()) {
                service.shutdown();
                try {
                    if (!service.awaitTermination(2, TimeUnit.SECONDS)) {
                        service.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    service.shutdownNow();
                }
            }
        }
        executors.clear();
    }
}
