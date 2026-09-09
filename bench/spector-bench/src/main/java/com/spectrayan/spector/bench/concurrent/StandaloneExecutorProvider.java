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
import com.spectrayan.spector.commons.concurrent.spi.DefaultExecutorProvider;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone benchmark and CLI execution provider extending {@link DefaultExecutorProvider}.
 *
 * <p>Standardizes thread naming, tracks task submission metrics across planes,
 * and provides monitoring capabilities for latency/SLA tracking in benchmarks.</p>
 */
public class StandaloneExecutorProvider extends DefaultExecutorProvider {

    private final AtomicLong virtualTasks = new AtomicLong(0);
    private final AtomicLong sharedTasks = new AtomicLong(0);
    private final AtomicLong writerTasks = new AtomicLong(0);

    public StandaloneExecutorProvider() {
        super();
    }

    /**
     * Installs this provider globally in {@link SpectorExecutors}.
     *
     * @return this provider
     */
    public StandaloneExecutorProvider install() {
        SpectorExecutors.install(this);
        return this;
    }

    @Override
    public Executor executor(ThreadPlane plane, String name) {
        Executor delegate = super.executor(plane, name);
        return task -> {
            switch (plane) {
                case VIRTUAL -> virtualTasks.incrementAndGet();
                case PLATFORM_SHARED -> sharedTasks.incrementAndGet();
                case PLATFORM_WRITER -> writerTasks.incrementAndGet();
            }
            delegate.execute(task);
        };
    }

    public long virtualTaskCount() {
        return virtualTasks.get();
    }

    public long sharedTaskCount() {
        return sharedTasks.get();
    }

    public long writerTaskCount() {
        return writerTasks.get();
    }

    public void resetMetrics() {
        virtualTasks.set(0);
        sharedTasks.set(0);
        writerTasks.set(0);
    }

    @Override
    public String describe() {
        return "StandaloneExecutorProvider[virtualTasks=" + virtualTasks.get()
                + ", sharedTasks=" + sharedTasks.get()
                + ", writerTasks=" + writerTasks.get() + "]";
    }
}
