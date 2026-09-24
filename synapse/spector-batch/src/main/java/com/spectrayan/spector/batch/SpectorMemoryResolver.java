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
package com.spectrayan.spector.batch;

import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Functional strategy interface for resolving, staging, and promoting a {@link SpectorMemory} instance
 * by namespace name (ADR-0045, memory-portability R2.7, design §D5).
 */
@FunctionalInterface
public interface SpectorMemoryResolver {

    /**
     * Resolves the {@link SpectorMemory} instance associated with the specified namespace.
     *
     * @param namespace target namespace identifier
     * @return active {@link SpectorMemory} instance, or {@code null} if not found
     */
    SpectorMemory resolve(String namespace);

    /**
     * Creates or initializes a staging {@link SpectorMemory} instance for atomic import (design §D5).
     *
     * @param stagingNamespace staging namespace identifier
     * @param targetNamespace  target destination namespace identifier
     * @return active staging {@link SpectorMemory} instance, or {@code null} to fallback to resolve
     */
    default SpectorMemory createStaging(String stagingNamespace, String targetNamespace) {
        return null;
    }

    /**
     * Atomically promotes the staging namespace to the target namespace upon successful import completion.
     *
     * @param stagingNamespace staging namespace identifier
     * @param targetNamespace  target destination namespace identifier
     */
    default void promote(String stagingNamespace, String targetNamespace) {}

    /**
     * Discards the staging namespace upon import failure or abort.
     *
     * @param stagingNamespace staging namespace identifier
     */
    default void discard(String stagingNamespace) {}
}

