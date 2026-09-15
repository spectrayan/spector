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
package com.spectrayan.spector.commons.pathway;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base implementation of {@link ContextualSignal} and {@link TraceableSignal}.
 */
public abstract class AbstractSignal implements ContextualSignal, TraceableSignal {

    private PathwayContext context;
    private final List<RelayTrace> traces = new ArrayList<>();
    private final Object tracesLock = new Object();

    @Override
    public final PathwayContext context() {
        return context;
    }

    @Override
    public final void bind(final PathwayContext ctx) {
        this.context = Objects.requireNonNull(ctx, "context cannot be null");
    }

    @Override
    public boolean isTraceEnabled() {
        return context != null && context.traceEnabled();
    }

    @Override
    public void recordTrace(final RelayTrace trace) {
        if (!isTraceEnabled() || trace == null) {
            return;
        }
        synchronized (tracesLock) {
            traces.add(trace);
        }
    }

    @Override
    public List<RelayTrace> traces() {
        synchronized (tracesLock) {
            return List.copyOf(traces);
        }
    }
}
