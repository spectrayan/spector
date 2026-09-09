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

import java.util.List;

/**
 * Functional handler responsible for executing a batch of scoped task payloads under lock amortization.
 *
 * @param <T> payload type
 */
@FunctionalInterface
public interface BatchTaskHandler<T> {

    /**
     * Processes a drained batch of scoped tasks.
     *
     * @param tasks list of scoped tasks in the drained batch
     * @throws Exception if batch execution fails
     */
    void handleBatch(List<ScopedTask<T>> tasks) throws Exception;
}
