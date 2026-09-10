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
import com.spectrayan.spector.kernel.bundle.RegionLease;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.store.AbstractEngramMemory;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.store.StrengthMemory;

import java.lang.foreign.MemorySegment;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link ScanService} holding region leases across scans (R2.7, R2.9, R7.1b).
 */
public class DefaultScanService implements ScanService {

    private final Map<MemoryType, EngramRegion> regions;
    private final StrengthMemory strengthMemory;
    private final int partitionSeq;

    public DefaultScanService(Map<MemoryType, EngramRegion> regions, StrengthMemory strengthMemory, int partitionSeq) {
        this.regions = new EnumMap<>(regions);
        this.strengthMemory = strengthMemory;
        this.partitionSeq = partitionSeq;
    }

    public DefaultScanService(Map<MemoryType, EngramRegion> regions, StrengthMemory strengthMemory) {
        this(regions, strengthMemory, 0);
    }

    @Override
    public void scan(EnumSet<MemoryType> tiers, float[] query, ScanFilter filter, SlotVisitor visitor) {
        scan(tiers, query, null, null, filter, visitor);
    }

    @Override
    public void scan(EnumSet<MemoryType> tiers, float[] query, float[] mins, float[] scales, ScanFilter filter, SlotVisitor visitor) {
        Objects.requireNonNull(tiers, "tiers cannot be null");
        Objects.requireNonNull(query, "query cannot be null");
        Objects.requireNonNull(filter, "filter cannot be null");
        Objects.requireNonNull(visitor, "visitor cannot be null");

        for (MemoryType tier : tiers) {
            if (tier == MemoryType.EPISODIC) {
                // Episodic tier is variable-length and scanned via cursor above the line
                continue;
            }

            EngramRegion region = regions.get(tier);
            if (region == null || region.visibleCount() <= 0) {
                continue;
            }

            if (region.layout() instanceof FixedEngramLayout layout) {
                scanTier(tier, region, layout, query, mins, scales, filter, visitor);
            }
        }
    }

    private void scanTier(MemoryType tier, EngramRegion region, FixedEngramLayout layout,
                          float[] query, float[] mins, float[] scales, ScanFilter filter, SlotVisitor visitor) {
        RegionRef engramRef = (region instanceof AbstractEngramMemory<?> aem) ? aem.regionRef() : null;
        RegionRef strengthRef = (strengthMemory != null && tier != MemoryType.WORKING) ? strengthMemory.regionRef() : null;

        RegionLease engramLease = engramRef != null ? engramRef.lease() : null;
        RegionLease strengthLease = strengthRef != null ? strengthRef.lease() : null;

        try {
            MemorySegment segment = engramLease != null ? engramLease.slab() : ((AbstractEngramMemory<?>) region).segment();
            int count = region.visibleCount();
            long baseOffset = region.dataOffset();

            SlabScanner.scan(segment, count, layout, query, mins, scales, filter,
                    strengthMemory, tier, baseOffset, partitionSeq, visitor);
        } finally {
            try {
                if (engramLease != null) {
                    engramLease.close();
                }
            } finally {
                if (strengthLease != null) {
                    strengthLease.close();
                }
            }
        }
    }
}
