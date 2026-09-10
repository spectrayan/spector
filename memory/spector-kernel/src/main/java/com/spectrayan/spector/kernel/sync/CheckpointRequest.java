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
package com.spectrayan.spector.kernel.sync;

import com.spectrayan.spector.kernel.region.RegionId;
import java.util.Objects;
import java.util.Set;

/**
 * Request parameter object specifying checkpoint scope and durability policy (R9.1, R9.2).
 *
 * @param walHwm the WAL high-water mark sequence to persist (0 if none)
 * @param regions specific regions to flush, or empty set to flush all dirty regions
 * @param flushAll whether to force flush all memory bundles and graphs
 * @param fsync whether to force write changes to physical storage
 */
public record CheckpointRequest(
        long walHwm,
        Set<RegionId> regions,
        boolean flushAll,
        boolean fsync
) {
    public static final CheckpointRequest DEFAULT = new CheckpointRequest(0L, Set.of(), true, true);

    public CheckpointRequest {
        regions = regions != null ? Set.copyOf(regions) : Set.of();
    }

    public CheckpointRequest(Set<RegionId> regions, boolean fsync) {
        this(0L, regions, regions == null || regions.isEmpty(), fsync);
    }

    public static CheckpointRequest all() {
        return DEFAULT;
    }

    public static CheckpointRequest of(Set<RegionId> regions, boolean fsync) {
        return new CheckpointRequest(0L, regions, false, fsync);
    }

    public static CheckpointRequest of(long walHwm, boolean flushAll, boolean fsync) {
        return new CheckpointRequest(walHwm, Set.of(), flushAll, fsync);
    }
}
