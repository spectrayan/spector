/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
