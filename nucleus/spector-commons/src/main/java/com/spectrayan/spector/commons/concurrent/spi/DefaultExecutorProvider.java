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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Built-in default {@link SpectorExecutorProvider} implementation used when no host provider is explicitly installed.
 *
 * <ul>
 *   <li>{@link ThreadPlane#VIRTUAL}: Thread-per-task executor using virtual threads.</li>
 *   <li>{@link ThreadPlane#PLATFORM_SHARED}: Fixed platform thread pool sized to {@code clamp(N_CPU - 1, 2, 8)}.</li>
 *   <li>{@link ThreadPlane#PLATFORM_WRITER}: Single-thread platform executor per pool name ensuring serial writes.</li>
 * </ul>
 */
public class DefaultExecutorProvider extends AbstractExecutorProvider {

    public static final DefaultExecutorProvider INSTANCE = new DefaultExecutorProvider();

    @Override
    protected Executor createExecutor(ThreadPlane plane, String name) {
        String poolName = name != null && !name.isBlank() ? name : "default";
        return switch (plane) {
            case VIRTUAL -> Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("spector-vt-" + poolName + "-", 0).factory()
            );
            case PLATFORM_SHARED -> {
                int nCores = Runtime.getRuntime().availableProcessors();
                int poolSize = Math.max(2, Math.min(8, nCores - 1));
                ThreadFactory factory = Thread.ofPlatform().daemon().name("spector-pool-shared-" + poolName + "-", 0).factory();
                yield Executors.newFixedThreadPool(poolSize, factory);
            }
            case PLATFORM_WRITER -> {
                ThreadFactory factory = Thread.ofPlatform().daemon().name("spector-pool-writer-" + poolName + "-", 0).factory();
                yield new java.util.concurrent.ThreadPoolExecutor(
                        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                        new java.util.concurrent.LinkedBlockingQueue<>(), factory);
            }
        };
    }
}
