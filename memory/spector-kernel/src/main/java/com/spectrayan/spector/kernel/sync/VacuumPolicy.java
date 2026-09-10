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

import com.spectrayan.spector.kernel.api.MemoryType;
import java.util.Set;

/**
 * Configuration policy for memory vacuum and tombstone compaction (R9.1, R9.2).
 *
 * @param tier primary target memory tier
 * @param tombstoneThreshold fraction of tombstoned records required to trigger compaction [0.0..1.0]
 * @param targetTiers memory tiers subject to vacuum
 */
public record VacuumPolicy(
        MemoryType tier,
        float tombstoneThreshold,
        Set<MemoryType> targetTiers
) {
    public static final float DEFAULT_THRESHOLD = 0.20f;
    public static final VacuumPolicy DEFAULT = new VacuumPolicy(MemoryType.SEMANTIC, DEFAULT_THRESHOLD, Set.of(MemoryType.SEMANTIC));

    public VacuumPolicy {
        targetTiers = targetTiers != null ? Set.copyOf(targetTiers) : Set.of();
    }

    public VacuumPolicy(float tombstoneThreshold, Set<MemoryType> targetTiers) {
        this(targetTiers != null && !targetTiers.isEmpty() ? targetTiers.iterator().next() : MemoryType.SEMANTIC,
             tombstoneThreshold, targetTiers);
    }

    public static VacuumPolicy standard() {
        return DEFAULT;
    }

    public static VacuumPolicy of(float threshold, Set<MemoryType> tiers) {
        return new VacuumPolicy(threshold, tiers);
    }

    public static VacuumPolicy of(MemoryType tier, float threshold) {
        return new VacuumPolicy(tier, threshold, Set.of(tier));
    }
}
