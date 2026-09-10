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
package com.spectrayan.spector.kernel.scan;

import com.spectrayan.spector.kernel.api.MemoryType;

import java.util.EnumSet;

/**
 * Fused filter-then-scan service across cognitive memory tiers (R7.1, R7.1b, R7.1c).
 *
 * <p>Evaluates Phases 1-4 before Phase 5 SIMD L2 vector distance; non-surviving slots
 * skip SIMD distance calculations entirely.</p>
 */
public interface ScanService {

    /**
     * Executes a filter-then-scan over the requested memory tiers.
     *
     * @param tiers   set of memory tiers to scan
     * @param query   query vector
     * @param filter  primitive gate inputs for Phases 1-4
     * @param visitor receiver for Phase 5 survivors
     */
    void scan(EnumSet<MemoryType> tiers, float[] query, ScanFilter filter, SlotVisitor visitor);

    /**
     * Executes a filter-then-scan over the requested memory tiers with calibrated quantization scales.
     */
    default void scan(EnumSet<MemoryType> tiers, float[] query, float[] mins, float[] scales, ScanFilter filter, SlotVisitor visitor) {
        scan(tiers, query, filter, visitor);
    }
}
