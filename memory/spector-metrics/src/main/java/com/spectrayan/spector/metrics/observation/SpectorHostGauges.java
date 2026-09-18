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
package com.spectrayan.spector.metrics.observation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Host-level meter binder for process and OS gauges (RAM pinning, page faults).
 *
 * <p>Unlike engine census gauges which carry the {@code spector.namespace} tag,
 * these gauges represent host/process resources and are registered once per JVM (ADR-0083).</p>
 */
public class SpectorHostGauges implements MeterBinder {

    private static final SpectorHostGauges INSTANCE = new SpectorHostGauges();
    private final AtomicBoolean bound = new AtomicBoolean(false);

    public static SpectorHostGauges instance() {
        return INSTANCE;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!bound.compareAndSet(false, true)) {
            return;
        }

        // Pinned Bytes Gauge (RAM usage verification)
        Gauge.builder("spector.memory.pinned.bytes", com.spectrayan.spector.commons.concurrent.MemoryPinning::pinnedBytes)
                .description("Total off-heap memory bytes pinned in RAM")
                .register(registry);

        // Soft & Hard Page Fault Gauges (Linux container tracking)
        Gauge.builder("spector.memory.page.faults", () -> readPageFaults()[0])
                .tag("type", "soft")
                .description("Soft page faults (minor faults) on Linux")
                .register(registry);

        Gauge.builder("spector.memory.page.faults", () -> readPageFaults()[1])
                .tag("type", "hard")
                .description("Hard page faults (major faults) on Linux")
                .register(registry);
    }

    /** Reset registration state (for unit testing). */
    void resetForTesting() {
        bound.set(false);
    }

    private static long[] readPageFaults() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("/proc/self/stat");
            if (java.nio.file.Files.exists(path)) {
                String content = java.nio.file.Files.readString(path);
                int lastParen = content.lastIndexOf(')');
                if (lastParen != -1 && lastParen + 2 < content.length()) {
                    String rest = content.substring(lastParen + 2);
                    String[] tokens = rest.split("\\s+");
                    if (tokens.length > 11) {
                        return new long[]{Long.parseLong(tokens[9]), Long.parseLong(tokens[11])};
                    }
                }
            }
        } catch (Exception ignored) {}
        return new long[]{0L, 0L};
    }
}
