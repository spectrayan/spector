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
package com.spectrayan.spector.commons.observation;

import java.util.Map;
import java.util.function.Supplier;

final class NoOpHook implements MemoryObservationHook {
    
    private static final AutoCloseable EMPTY = () -> {};

    @Override
    public AutoCloseable start(String observationSuffix, Map<String, String> tags) {
        return EMPTY;
    }

    @Override
    public <T> T observe(String suffix, Map<String, String> tags, Supplier<T> work) {
        return work.get();
    }

    @Override
    public <T> T observe(String suffix, Supplier<Map<String, String>> tagSupplier, Supplier<T> work) {
        return work.get();
    }

    @Override
    public void observe(String suffix, Map<String, String> tags, Runnable work) {
        work.run();
    }

    @Override
    public void observe(String suffix, Supplier<Map<String, String>> tagSupplier, Runnable work) {
        work.run();
    }
}
