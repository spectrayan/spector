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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes region sizes from raw primitive sizes.
 */
public final class BundleFileLayoutCalculator {
    
    private BundleFileLayoutCalculator() {} // static utility
    
    /**
     * Computes a complete bundle layout from region specs.
     * Returns (BundleDirectory, totalFileSize).
     */
    public static BundleComputedLayout compute(int bundleMagic, List<RegionSizeSpec> specs) {
        int maxRegions = specs.size();
        long dataStart = BundleDirectory.dataStartOffset(maxRegions);
        long cursor = dataStart;
        List<RegionEntry> entries = new ArrayList<>();
        for (RegionSizeSpec spec : specs) {
            long regionTotal = alignToPage(RegionPreamble.PREAMBLE_BYTES + spec.dataBytes());
            short flags = (short) (RegionEntry.FLAG_LIVE | (spec.growable() ? RegionEntry.FLAG_GROWABLE : 0));
            entries.add(new RegionEntry(spec.regionId(), flags, cursor, regionTotal,
                    0, spec.capacity(), spec.stride(), spec.layoutId(), spec.schemaVersion()));
            cursor += regionTotal;
        }
        long totalFileSize = cursor;
        return new BundleComputedLayout(
            new BundleDirectory(bundleMagic, maxRegions, entries),
            totalFileSize
        );
    }
    
    public record BundleComputedLayout(BundleDirectory directory, long totalFileSize) {}
    
    /** Rounds up to the nearest 4KB page boundary. */
    public static long alignToPage(long bytes) {
        return (bytes + 4095L) & ~4095L;
    }
}
