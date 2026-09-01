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

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;
import com.spectrayan.spector.memory.kernel.layout.TemporalLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BundleRegionIntegrityIntegrationTest — Multi-Region Off-Heap Layout Verification")
class BundleRegionIntegrityIntegrationTest {

    @Test
    @DisplayName("RuntimeBundle multi-region allocation, header validation, and persistence round-trip")
    void testRuntimeBundleIntegrityAndReload(@TempDir Path tempDir) {
        Path bundleFile = tempDir.resolve("runtime_test.bundle");

        int entityCap = 100;
        int graphCap = 100;
        int temporalCap = 100;

        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(
                        RegionId.ENTITY_DIRECTORY,
                        EntityDirectoryLayout.DATA_START + (long) entityCap * EntityDirectoryLayout.ENTITY_NODE_BYTES,
                        entityCap,
                        EntityDirectoryLayout.ENTITY_NODE_BYTES,
                        new EntityDirectoryLayout().layoutId(),
                        new EntityDirectoryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_NAMES,
                        EntityDirectoryLayout.DATA_START + (long) entityCap * 64L * EntityDirectoryLayout.ADJ_ENTRY_BYTES,
                        1,
                        EntityDirectoryLayout.ADJ_ENTRY_BYTES,
                        new EntityDirectoryLayout().layoutId(),
                        new EntityDirectoryLayout().schemaVersion(),
                        true
                ),
                new RegionSizeSpec(
                        RegionId.HEBBIAN,
                        HebbianLayout.DATA_START + ((long) (graphCap + 1) * Integer.BYTES) + (1000L * HebbianLayout.EDGE_BYTES),
                        graphCap,
                        HebbianLayout.EDGE_BYTES,
                        new HebbianLayout().layoutId(),
                        new HebbianLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.TEMPORAL_CHAIN,
                        MemoryHeader.HEADER_BYTES + ((long) temporalCap * new TemporalLayout().recordStride()),
                        temporalCap,
                        new TemporalLayout().recordStride(),
                        new TemporalLayout().layoutId(),
                        new TemporalLayout().schemaVersion(),
                        false
                )
        );

        long now = System.currentTimeMillis();

        // 1. Create and populate RuntimeBundle
        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundleFile, specs)) {
            assertThat(bundle.directory().liveRegionCount()).isEqualTo(4);

            // Populate EntityDirectory region
            MemorySegment edirSlice = bundle.regionSegment(RegionId.ENTITY_DIRECTORY);
            MemoryHeader.write(edirSlice, 0L, new EntityDirectoryLayout().schemaVersion(), MemoryShape.GRAPH, 0,
                    entityCap, 1, EntityDirectoryLayout.ENTITY_NODE_BYTES, new EntityDirectoryLayout().layoutId(),
                    now, now);
            edirSlice.set(ValueLayout.JAVA_INT, EntityDirectoryLayout.DATA_START + EntityDirectoryLayout.ENT_OFF_TYPE, 7);
            edirSlice.set(ValueLayout.JAVA_LONG, EntityDirectoryLayout.DATA_START + EntityDirectoryLayout.ENT_OFF_NAME_HASH, 0xABCDEFFEDCBA0123L);

            // Populate Hebbian region
            MemorySegment hebbSlice = bundle.regionSegment(RegionId.HEBBIAN);
            MemoryHeader.write(hebbSlice, 0L, new HebbianLayout().schemaVersion(), MemoryShape.GRAPH, 0,
                    graphCap, 1, HebbianLayout.EDGE_BYTES, new HebbianLayout().layoutId(),
                    now, now);
            hebbSlice.set(ValueLayout.JAVA_INT, HebbianLayout.DATA_START + HebbianLayout.EDGE_OFF_NEIGHBOR, 42);
            hebbSlice.set(ValueLayout.JAVA_FLOAT, HebbianLayout.DATA_START + HebbianLayout.EDGE_OFF_WEIGHT, 0.99f);

            // Populate Temporal region
            MemorySegment tempSlice = bundle.regionSegment(RegionId.TEMPORAL_CHAIN);
            MemoryHeader.write(tempSlice, 0L, new TemporalLayout().schemaVersion(), MemoryShape.CHAIN, 0,
                    temporalCap, 1, new TemporalLayout().recordStride(), new TemporalLayout().layoutId(),
                    now, now);
            tempSlice.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES, -1);
            tempSlice.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + 4, 1);
            tempSlice.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + 8, 9001);
        }

        // 2. Reopen RuntimeBundle and verify 100% data fidelity
        try (RuntimeBundle bundle = RuntimeBundle.Init.open(bundleFile)) {
            assertThat(bundle.directory().liveRegionCount()).isEqualTo(4);

            // Verify EntityDirectory
            MemorySegment edirSlice = bundle.regionSegment(RegionId.ENTITY_DIRECTORY);
            assertThat(MemoryHeader.isValid(edirSlice, 0L)).isTrue();
            assertThat(MemoryHeader.readLayoutId(edirSlice, 0L)).isEqualTo(new EntityDirectoryLayout().layoutId());
            assertThat(edirSlice.get(ValueLayout.JAVA_INT, EntityDirectoryLayout.DATA_START + EntityDirectoryLayout.ENT_OFF_TYPE)).isEqualTo(7);
            assertThat(edirSlice.get(ValueLayout.JAVA_LONG, EntityDirectoryLayout.DATA_START + EntityDirectoryLayout.ENT_OFF_NAME_HASH)).isEqualTo(0xABCDEFFEDCBA0123L);

            // Verify Hebbian
            MemorySegment hebbSlice = bundle.regionSegment(RegionId.HEBBIAN);
            assertThat(MemoryHeader.isValid(hebbSlice, 0L)).isTrue();
            assertThat(MemoryHeader.readLayoutId(hebbSlice, 0L)).isEqualTo(new HebbianLayout().layoutId());
            assertThat(hebbSlice.get(ValueLayout.JAVA_INT, HebbianLayout.DATA_START + HebbianLayout.EDGE_OFF_NEIGHBOR)).isEqualTo(42);
            assertThat(hebbSlice.get(ValueLayout.JAVA_FLOAT, HebbianLayout.DATA_START + HebbianLayout.EDGE_OFF_WEIGHT)).isEqualTo(0.99f);

            // Verify Temporal
            MemorySegment tempSlice = bundle.regionSegment(RegionId.TEMPORAL_CHAIN);
            assertThat(MemoryHeader.isValid(tempSlice, 0L)).isTrue();
            assertThat(MemoryHeader.readLayoutId(tempSlice, 0L)).isEqualTo(new TemporalLayout().layoutId());
            assertThat(tempSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES)).isEqualTo(-1);
            assertThat(tempSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + 4)).isEqualTo(1);
            assertThat(tempSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + 8)).isEqualTo(9001);
        }
    }

    @Test
    @DisplayName("PartitionBundle 4-tier cognitive region allocation, layout framing, and reload")
    void testPartitionBundleIntegrityAndReload(@TempDir Path tempDir) {
        Path bundleFile = tempDir.resolve("partition_test.bundle");

        int quantizedVecBytes = 64;
        int semanticCapacity = 50;
        long episodicSize = 4096;
        int proceduralCapacity = 20;
        long textSize = 8192;

        int cogLayoutId = 0x434F4752; // 'COGR'
        int cogSchemaVersion = 2;
        int textLayoutId = 0x54455854; // 'TEXT'
        int textSchemaVersion = 1;

        // 1. Create and populate PartitionBundle
        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                bundleFile,
                semanticCapacity, episodicSize,
                proceduralCapacity, textSize,
                quantizedVecBytes,
                cogLayoutId, cogSchemaVersion,
                textLayoutId, textSchemaVersion)) {

            assertThat(bundle.directory().liveRegionCount()).isEqualTo(5);

            MemorySegment textSlice = bundle.regionSegment(RegionId.TEXT);
            textSlice.set(ValueLayout.JAVA_BYTE, 0, (byte) 'S');
            textSlice.set(ValueLayout.JAVA_BYTE, 1, (byte) 'P');
            textSlice.set(ValueLayout.JAVA_BYTE, 2, (byte) 'E');
            textSlice.set(ValueLayout.JAVA_BYTE, 3, (byte) 'C');
        }

        // 2. Reopen and verify
        try (PartitionBundle bundle = PartitionBundle.Init.open(bundleFile)) {
            assertThat(bundle.directory().liveRegionCount()).isEqualTo(5);

            MemorySegment textSlice = bundle.regionSegment(RegionId.TEXT);
            assertThat(textSlice.get(ValueLayout.JAVA_BYTE, 0)).isEqualTo((byte) 'S');
            assertThat(textSlice.get(ValueLayout.JAVA_BYTE, 1)).isEqualTo((byte) 'P');
            assertThat(textSlice.get(ValueLayout.JAVA_BYTE, 2)).isEqualTo((byte) 'E');
            assertThat(textSlice.get(ValueLayout.JAVA_BYTE, 3)).isEqualTo((byte) 'C');
        }
    }
}
