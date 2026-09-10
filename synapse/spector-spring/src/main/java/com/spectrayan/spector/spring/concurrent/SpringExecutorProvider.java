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
package com.spectrayan.spector.spring.concurrent;

import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import com.spectrayan.spector.commons.concurrent.spi.AbstractExecutorProvider;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring Boot host implementation of {@link com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider}.
 *
 * <p>Delegates task execution across the three execution planes to Spring-managed executor beans:
 * <ul>
 *     <li>{@link ThreadPlane#VIRTUAL} &rarr; Spring {@link AsyncTaskExecutor} configured with virtual threads</li>
 *     <li>{@link ThreadPlane#PLATFORM_SHARED} &rarr; Sized {@link ThreadPoolTaskExecutor} for CPU-intensive shared compute</li>
 *     <li>{@link ThreadPlane#PLATFORM_WRITER} &rarr; Single-threaded {@link ThreadPoolTaskExecutor} for serializing native mutations</li>
 * </ul>
 *
 * <p>Supports cooperative draining across Spring task executors without prematurely shutting down Spring-managed beans.</p>
 */
public class SpringExecutorProvider extends AbstractExecutorProvider implements org.springframework.beans.factory.DisposableBean {

    private final ThreadPoolTaskExecutor sharedPool;
    private final ThreadPoolTaskExecutor writerPool;
    private final AsyncTaskExecutor virtualExecutor;
    private final boolean writerPerNamespace;

    public SpringExecutorProvider(
            ThreadPoolTaskExecutor sharedPool,
            ThreadPoolTaskExecutor writerPool,
            AsyncTaskExecutor virtualExecutor) {
        this(sharedPool, writerPool, virtualExecutor, false);
    }

    public SpringExecutorProvider(
            ThreadPoolTaskExecutor sharedPool,
            ThreadPoolTaskExecutor writerPool,
            AsyncTaskExecutor virtualExecutor,
            boolean writerPerNamespace) {
        this.sharedPool = Objects.requireNonNull(sharedPool, "sharedPool must not be null");
        this.writerPool = Objects.requireNonNull(writerPool, "writerPool must not be null");
        this.virtualExecutor = Objects.requireNonNull(virtualExecutor, "virtualExecutor must not be null");
        this.writerPerNamespace = writerPerNamespace;
    }

    @Override
    protected Executor createExecutor(ThreadPlane plane, String name) {
        return switch (plane) {
            case VIRTUAL -> {
                if (name != null && !name.isBlank() && !"default".equalsIgnoreCase(name)) {
                    var ex = new SimpleAsyncTaskExecutor("spector-vt-" + name + "-");
                    ex.setVirtualThreads(true);
                    yield ex;
                }
                yield virtualExecutor;
            }
            case PLATFORM_SHARED -> sharedPool;
            case PLATFORM_WRITER -> {
                if (writerPerNamespace && name != null && !name.isBlank() && !"default".equalsIgnoreCase(name)) {
                    yield Executors.newSingleThreadExecutor(
                            Thread.ofPlatform().daemon().name("spector-pool-writer-" + name + "-", 0).factory()
                    );
                }
                yield writerPool;
            }
        };
    }

    @Override
    protected ThreadPoolExecutor extractThreadPoolExecutor(Executor executor) {
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe;
        }
        if (executor instanceof ThreadPoolTaskExecutor taskExecutor) {
            try {
                return taskExecutor.getThreadPoolExecutor();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public ThreadPoolTaskExecutor getSharedPool() {
        return sharedPool;
    }

    public ThreadPoolTaskExecutor getWriterPool() {
        return writerPool;
    }

    public AsyncTaskExecutor getVirtualExecutor() {
        return virtualExecutor;
    }

    public boolean isWriterPerNamespace() {
        return writerPerNamespace;
    }

    @Override
    public String describe() {
        return "SpringExecutorProvider[shared=" + sharedPool.getThreadNamePrefix()
                + ", writer=" + writerPool.getThreadNamePrefix()
                + ", virtual=" + virtualExecutor.getClass().getSimpleName()
                + ", writerPerNamespace=" + writerPerNamespace + "]";
    }

    @Override
    public void close() {
        super.close();
        if (com.spectrayan.spector.commons.concurrent.SpectorExecutors.current() == this) {
            com.spectrayan.spector.commons.concurrent.SpectorExecutors.reset();
        }
    }

    @Override
    public void destroy() {
        close();
    }
}
