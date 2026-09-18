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
package com.spectrayan.spector.memory.pathway.reflect.spi.local;

import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectBackpressurePolicy;

/**
 * No-op backpressure policy introducing zero pauses or restrictions.
 * Used as default in unit tests and benchmarks.
 *
 * @since 1.5.0
 */
public final class NoopBackpressurePolicy implements ReflectBackpressurePolicy {

    public static final NoopBackpressurePolicy INSTANCE = new NoopBackpressurePolicy();

    private NoopBackpressurePolicy() {
    }

    @Override
    public void beforeSession(SessionWorkItem item) {
        // No pause
    }

    @Override
    public void onProviderFailure(Throwable t) {
        // No tracking
    }

    @Override
    public boolean shouldAbortSweep() {
        return false;
    }
}
