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
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.kernel.region.RegionId;
import com.spectrayan.spector.memory.kernel.region.RegionSizeSpec;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.cortex.ProceduralMemory;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.TextBlobMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.kernel.region.RegionPreamble;
import com.spectrayan.spector.memory.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Failing staleness test suite required by Task 2.6 (Req: R2.3, R2.4).
 *
 * <p>Demonstrates the two fundamental staleness hazards present prior to {@code RegionRef}
 * and {@code RegionOpener}:</p>
 * <ol>
 *   <li><b>grow-then-access</b>: Growing a runtime region unmaps the arena, causing any held
 *       segment handle to throw {@link IllegalStateException}.</li>
 *   <li><b>roll-while-held</b>: Rolling a partition leaves existing held store handles pointing
 *       to the frozen previous bundle rather than rebinding to the new active partition.</li>
 * </ol>
 */
@DisplayName("Region Staleness & Invalidation Tests (R2.3, R2.4)")
class RegionStalenessTest {

    private static final int DIMS = 8;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);

    @Test
    @DisplayName("(a) grow-then-access: held handle reads and writes remapped slice after growRegion")
    void growThenAccessReadsRemappedSlice(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            // Write initial data to BM25
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            long baseOffset = RegionPreamble.PREAMBLE_BYTES;
            bm25Ref.resolve().set(ValueLayout.JAVA_LONG, baseOffset, 0x1122334455667788L);

            // Hold a memory handle created before growth
            // With RegionRef, this rebinds dynamically on growth/remap.
            RegionRef heldRef = bm25Ref;

            // Grow the runtime region
            bundle.growRegion(RegionId.BM25);

            // Assert that reads through held handle land on the remapped slice and do not crash
            long readVal = heldRef.resolve().get(ValueLayout.JAVA_LONG, baseOffset);
            assertThat(readVal).isEqualTo(0x1122334455667788L);
        }
    }

    @Test
    @DisplayName("(b) roll-while-held: active store handle does not continue reading previous bundle after partition roll")
    void rollWhileHeldDoesNotReadPreviousBundle(@TempDir Path tempDir) throws Exception {
        Path basePath = tempDir.resolve("spector-data");
        int quantizedVecBytes = DIMS;
        int semanticCap = 10;
        int episodicCap = 10;
        int proceduralCap = 10;

        TextBlobLayout textLayout = new TextBlobLayout();

        // Initialize Partition 0
        Path part0Dir = basePath.resolve("partitions").resolve("000_1000000");
        java.nio.file.Files.createDirectories(part0Dir);
        PartitionBundle part0Bundle = PartitionBundle.Init.heap(
                semanticCap, 4096, proceduralCap, 4096, quantizedVecBytes,
                LAYOUT.layoutId(), LAYOUT.schemaVersion(),
                textLayout.layoutId(), textLayout.schemaVersion());

        WorkingMemory working = new WorkingMemory(quantizedVecBytes, 10);
        SemanticMemory sem0 = part0Bundle.openSemantic(semanticCap, quantizedVecBytes);
        ProceduralMemory proc0 = part0Bundle.openProcedural(proceduralCap, quantizedVecBytes);
        EpisodicMemory epi0 = part0Bundle.openEpisodic(episodicCap);
        TextBlobMemory text0 = part0Bundle.openText(DataEncryptor.NOOP);

        CognitiveMemoryRouter router0 = new CognitiveMemoryRouter(working, sem0, proc0, epi0);

        MemoryIndex index = new MemoryIndex();
        HebbianGraphMemory hebbian = new HebbianGraphMemory(64);
        TemporalChainMemory temporal = new TemporalChainMemory(64);
        RememberPathway cognitiveTarget = mock(RememberPathway.class);

        PartitionManager pm = new PartitionManager(
                basePath, quantizedVecBytes, semanticCap, episodicCap, proceduralCap,
                router0, part0Dir, text0, 0, List.of(),
                index, hebbian, temporal, cognitiveTarget, DataEncryptor.NOOP,
                true, part0Bundle);

        // Caller holds a handle to the active semantic store
        SemanticMemory heldSemantic = pm.activeRouter().semantic();

        // Roll the partition to partition 1
        pm.rollPartition();

        // Ingest a record into the newly rolled active partition
        EncodingHeader header = EncodingHeader.create(System.currentTimeMillis(), 0L, 1.0f, 0.5f, (short) 0, MemoryType.SEMANTIC);
        pm.activeRouter().semantic().append(header, new byte[quantizedVecBytes]);

        // Assert that the caller holding the active semantic handle observes the new active partition
        // and does NOT continue reading from the previous frozen partition (which has count 0).
        assertThat(heldSemantic.size())
                .as("held store handle must reflect the active partition, not continue reading previous bundle")
                .isEqualTo(1);
    }
}
