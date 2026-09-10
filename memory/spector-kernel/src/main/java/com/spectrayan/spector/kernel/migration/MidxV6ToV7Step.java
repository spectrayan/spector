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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.migration.FormatId;
import com.spectrayan.spector.kernel.migration.InPlaceHeaderStep;
import com.spectrayan.spector.kernel.migration.MigrationContext;
import com.spectrayan.spector.kernel.layout.IndexEntryLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migration step for MIDX v6 → v7 (SMKM container, header-only upgrade).
 *
 * <p>V7 stores the monotonic {@code graphSlotHighWater} in the previously-reserved
 * 4 bytes at header offset 60. The slot stride and record layout are unchanged
 * (48 bytes). On upgrade, the high-water mark is computed from the existing
 * entry count and written into the reserved field.</p>
 *
 * <p><b>Graph cold-start:</b> Existing Hebbian, Temporal Chain, Entity Directory,
 * and Hypergraph edge sets were indexed using the old broken slot IDs. After this
 * migration, those edges reference invalidated coordinates. The caller (typically
 * {@code CognitiveGraphBuilder}) must detect the v6→v7 migration and clear/rebuild
 * the graph structures. This step emits a {@code WARN} log to signal the condition.</p>
 *
 * @see IndexEntryMemory
 * @see IndexEntryLayout
 */
public final class MidxV6ToV7Step extends InPlaceHeaderStep {

    private static final Logger log = LoggerFactory.getLogger(MidxV6ToV7Step.class);

    static final FormatId FROM = FormatId.smkm(6);
    static final FormatId TO = FormatId.smkm(7);

    @Override
    public FormatId from() {
        return FROM;
    }

    @Override
    public FormatId to() {
        return TO;
    }

    @Override
    protected void rewriteHeader(MemorySegment mapped, MigrationContext ctx) {
        // Read existing entry count to compute initial high-water mark
        long entryCount = RegionPreamble.readCount(mapped, 0L);
        int highWater = (int) Math.max(0, entryCount);

        // Bump schema version from 6 → 7
        mapped.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, 7); // OFFSET_SCHEMA_VERSION = 4

        // Write graphSlotHighWater into the reserved field (offset 60, outside CRC range)
        mapped.set(ValueLayout.JAVA_INT_UNALIGNED, 60L, highWater);

        // Recompute header CRC (covers bytes [0..55])
        RegionPreamble.writeCount(mapped, 0L, entryCount); // re-stamps CRC

        log.warn("MIDX v6→v7: graph slot high-water mark set to {} from {} entries. "
                + "Graph state (Hebbian, Temporal, Entity, Hypergraph) must be rebuilt — "
                + "co-activation history from prior indexing is lost. (#497)",
                highWater, entryCount);
    }
}
