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
import java.util.concurrent.Executor;

/**
 * Host-provided thread provisioning SPI.
 *
 * <p>The Spector cognitive memory library NEVER creates OS carrier threads directly for dispatched work.
 * Instead, it requests an {@link Executor} from the host for a declared {@link ThreadPlane} and pool name.</p>
 *
 * <p>Implementations MUST be thread-safe. Hosts may collapse logical names onto shared pools,
 * but MUST ensure that {@link ThreadPlane#PLATFORM_WRITER} executions are serialized per structure/namespace.</p>
 */
public interface SpectorExecutorProvider {

    /**
     * Obtains an {@link Executor} for the specified execution plane and logical pool name.
     *
     * @param plane thread execution plane (VIRTUAL, PLATFORM_SHARED, PLATFORM_WRITER)
     * @param name  stable logical pool identifier (e.g. {@code "graph-writer"}, {@code "entity-extract"})
     * @return an executor appropriate for the plane
     */
    Executor executor(ThreadPlane plane, String name);

    /**
     * Cooperatively drains every executor handed out by this provider up to the specified budget.
     *
     * <p>MUST NOT unmap off-heap memory arenas. MUST be idempotent.</p>
     *
     * @param budget maximum duration allocated for draining in-flight work
     * @return drain result detailing completion status and duration
     */
    DrainResult drain(Duration budget);

    /**
     * Cooperatively drains executors matching the specified pool filter up to the allocated budget.
     *
     * @param poolFilter substring/identifier filter (e.g. namespace or pool name), or null to drain all
     * @param budget     maximum duration allocated for draining in-flight work
     * @return drain result detailing completion status and duration
     */
    default DrainResult drain(String poolFilter, Duration budget) {
        return drain(budget);
    }

    /**
     * Host-visible identity for logging, thread dumps, and JFR recordings.
     *
     * @return provider description
     */
    default String describe() {
        return getClass().getSimpleName();
    }
}
