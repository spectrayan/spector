/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.assembly;

import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads (or creates) the {@link MemoryIndex} and applies the #443 colocated
 * partition provenance / active-partition-seq seeding.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. The load-priority resolution, the v6-vs-v5
 * provenance logging, and {@code setActivePartitionSeq} are unchanged.</p>
 *
 * @since 1.1.0
 */
public final class MemoryIndexBuilder {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndexBuilder.class);

    private MemoryIndexBuilder() {}

    public static MemoryIndex build(CognitiveCortexBuilder.CortexFoundation cortex) {
        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  Memory Index 
        MemoryIndex index;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment midxSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.INDEX_MIDX);
            java.lang.foreign.MemorySegment idplSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.INDEX_IDPL);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(midxSlice, 0L);
            index = com.spectrayan.spector.memory.index.IndexRecordMemory.fromBundle(
                    cortex.runtimeBundle().arena(), midxSlice, idplSlice,
                    cortex.runtimeBundle().bundlePath(), isNew);
        } else {
            index = new MemoryIndex();
        }

        // #443 Phase 2: colocated-partition provenance on load.
        //  • v6 index → each record carries its persisted colocatedPartition (register()
        //    already keyed the reverse index by it). Do NOT overwrite — just record the
        //    active seq for the legacy partition-unaware reverse-lookup overload.
        //  • v5/legacy index → no persisted partition; records default to partition 0
        //    (ADR-0002: v5 loads with colocatedPartition=0; multi-partition v5 data is
        //    unrecoverable and is not auto-healed). For the common single-partition store,
        //    partition 0 IS the active partition, so this is correct.
        if (index.size() > 0 && index.isColocatedPartitionPersisted()) {
            log.info("Loaded a v6 index with persisted colocated partitions ({} records)", index.size());
        }
        index.setActivePartitionSeq(cortex.initialPartitionSeq());

        return index;
    }
}
