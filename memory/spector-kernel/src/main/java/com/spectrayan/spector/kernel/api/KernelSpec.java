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
package com.spectrayan.spector.kernel.api;

import com.spectrayan.spector.kernel.region.RegionId;

import java.util.Set;

/**
 * Plain record specifying kernel sizing and options (R4.4).
 *
 * <p>Built by the composition root from configuration properties. The kernel reads no
 * configuration directly, preserving the strict isolation of the storage substrate.</p>
 */
public record KernelSpec(
        int dimensions,
        int workingCapacity,
        int partitionCapacity,
        long maxPartitionBytes,
        boolean bm25Enabled,
        boolean spladeEnabled,
        boolean colbertEnabled,
        int colbertMaxEntries,
        int colbertMaxTokens,
        Set<RegionId> optionalRegions
) {
    public static KernelSpec standard(int dimensions) {
        return new KernelSpec(
                dimensions,
                10_000,
                100_000,
                100 * 1024 * 1024L,
                false,
                false,
                false,
                0,
                0,
                Set.of()
        );
    }
}
