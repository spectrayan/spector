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

import com.spectrayan.spector.memory.SpectorMemory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class SpectorMemoryGauges implements MeterBinder {
    
    private final SpectorMemory memory;
    
    public SpectorMemoryGauges(SpectorMemory memory) {
        this.memory = memory;
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // Pinned Bytes Gauge (RAM usage verification)
        Gauge.builder("spector.memory.pinned.bytes", com.spectrayan.spector.commons.concurrent.MemoryPinning::pinnedBytes)
                .description("Total off-heap memory bytes pinned in RAM")
                .register(registry);

        // Gauges
        Gauge.builder("spector.memory.count", memory, SpectorMemory::totalMemories)
                .description("Total number of memories across all tiers")
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
    
    private static long[] readPageFaults() {
        try {
            java.nio.file.Path path = java.nio.file.Path.of("/proc/self/stat");
            if (java.nio.file.Files.exists(path)) {
                String content = java.nio.file.Files.readString(path);
                int lastParen = content.lastIndexOf(')');
                if (lastParen != -1 && lastParen + 2 < content.length()) {
                    String rest = content.substring(lastParen + 2);
                    String[] tokens = rest.split("\\s+");
                    if (tokens.length > 9) {
                        long soft = Long.parseLong(tokens[7]);
                        long hard = Long.parseLong(tokens[9]);
                        return new long[]{soft, hard};
                    }
                }
            }
        } catch (Exception e) {
            // safe fallback
        }
        return new long[]{0L, 0L};
    }
}
