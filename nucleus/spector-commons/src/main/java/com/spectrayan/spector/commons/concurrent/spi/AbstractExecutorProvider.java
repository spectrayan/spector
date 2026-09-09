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

    protected final ConcurrentHashMap<String, Executor> executors = new ConcurrentHashMap<>();

    @Override
    public Executor executor(ThreadPlane plane, String name) {
        String poolName = name != null && !name.isBlank() ? name : "default";
        String key = plane.name() + ":" + poolName;
        return executors.compute(key, (k, existing) -> {
            if (existing instanceof ExecutorService service && service.isShutdown()) {
                return createExecutor(plane, poolName);
            }
            return existing != null ? existing : createExecutor(plane, poolName);
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

        for (java.util.Map.Entry<String, Executor> entry : executors.entrySet()) {
            if (poolFilter != null && !poolFilter.isBlank() && !entry.getKey().contains(poolFilter)) {
                continue;
            }
            Executor executor = entry.getValue();
            if (executor instanceof ThreadPoolExecutor tpe) {
                while (tpe.getActiveCount() > 0 || !tpe.getQueue().isEmpty()) {
                    long elapsedNanos = System.nanoTime() - startNanos;
                    if (elapsedNanos >= budgetNanos) {
                        allCompleted = false;
                        remaining += tpe.getActiveCount() + tpe.getQueue().size();
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        allCompleted = false;
                        remaining += tpe.getActiveCount() + tpe.getQueue().size();
                        break;
                    }
                }
            }
        }

        Duration waited = Duration.ofNanos(System.nanoTime() - startNanos);
        return new DrainResult(allCompleted, remaining, waited);
    }

    @Override
    public void close() {
        for (Executor executor : executors.values()) {
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
