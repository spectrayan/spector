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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RuntimeBundleTest {

    private static final int LAYOUT_ID = 0x48454242; // dummy layoutId for tests
    private static final int SCHEMA_VER = 1;

    /** Creates a minimal set of runtime region specs for testing. */
    private static List<RegionSizeSpec> testSpecs() {
        return List.of(
                new RegionSizeSpec(
                        RegionId.WORKING, 4096, 100, 128, LAYOUT_ID, SCHEMA_VER, false),
                new RegionSizeSpec(
                        RegionId.COACTIVATION, 4096, 50, 64, LAYOUT_ID, SCHEMA_VER, false),
                new RegionSizeSpec(
                        RegionId.HEBBIAN, 8192, 200, 32, LAYOUT_ID, SCHEMA_VER, true),
                new RegionSizeSpec(
                        RegionId.ENTITY_DIRECTORY, 8192, 100, 64, LAYOUT_ID, SCHEMA_VER, true),
                new RegionSizeSpec(
                        RegionId.ENTITY_NAMES, 16384, 0, 0, LAYOUT_ID, SCHEMA_VER, true),
                new RegionSizeSpec(
                        RegionId.INSULA, 4096, 1, 0, LAYOUT_ID, SCHEMA_VER, false)
        );
    }

    // ── Heap Tests ──

    @Test
    void heapBundleCreatesAllRegions() {
        try (RuntimeBundle bundle = RuntimeBundle.Init.heap(testSpecs())) {
            assertThat(bundle.isNew()).isTrue();
            assertThat(bundle.bundlePath()).isNull();
            assertThat(bundle.arena()).isNotNull();

            BundleDirectory dir = bundle.directory();
            assertThat(dir.liveRegionCount()).isEqualTo(6);
            assertThat(dir.bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_RUNTIME);

            // Verify all regions are accessible
            assertThat(bundle.regionSegment(RegionId.WORKING)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.COACTIVATION)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.HEBBIAN)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.ENTITY_DIRECTORY)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.ENTITY_NAMES)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.INSULA)).isNotNull();
        }
    }

    @Test
    void heapRegionSlicesAreWritable() {
        try (RuntimeBundle bundle = RuntimeBundle.Init.heap(testSpecs())) {
            MemorySegment hebbianSlice = bundle.regionSegment(RegionId.HEBBIAN);

            // Write SMKM header
            long now = System.currentTimeMillis();
            MemoryHeader.write(hebbianSlice, 0, 1, MemoryShape.GRAPH, 1,
                    200, 0, 32, LAYOUT_ID, now, now);

            assertThat(MemoryHeader.isValid(hebbianSlice, 0)).isTrue();
            assertThat(MemoryHeader.readCount(hebbianSlice, 0)).isEqualTo(0);

            // Write data
            hebbianSlice.set(ValueLayout.JAVA_LONG, MemoryHeader.HEADER_BYTES, 0xCAFEBABEL);
            assertThat(hebbianSlice.get(ValueLayout.JAVA_LONG, MemoryHeader.HEADER_BYTES)).isEqualTo(0xCAFEBABEL);
        }
    }

    @Test
    void regionSegmentThrowsForUnknownRegion() {
        try (RuntimeBundle bundle = RuntimeBundle.Init.heap(testSpecs())) {
            assertThatThrownBy(() -> bundle.regionSegment(RegionId.SEMANTIC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SEMANTIC");
        }
    }

    @Test
    void heapGrowRegionNotSupported() {
        try (RuntimeBundle bundle = RuntimeBundle.Init.heap(testSpecs())) {
            assertThatThrownBy(() -> bundle.growRegion(RegionId.HEBBIAN))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("heap");
        }
    }

    // ── Mmap Tests ──

    @Test
    void mmapBundleCreateAndOpen(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");

        // Create
        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, testSpecs())) {
            assertThat(bundle.isNew()).isTrue();
            assertThat(bundle.bundlePath()).isEqualTo(bundlePath);
            assertThat(bundle.directory().liveRegionCount()).isEqualTo(6);
            assertThat(bundle.directory().bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_RUNTIME);

            // Write data to entity directory region
            MemorySegment edirSlice = bundle.regionSegment(RegionId.ENTITY_DIRECTORY);
            long now = System.currentTimeMillis();
            MemoryHeader.write(edirSlice, 0, 1, MemoryShape.GRAPH, 1,
                    100, 37, 64, LAYOUT_ID, now, now);
        }

        // Reopen and verify
        try (RuntimeBundle reopened = RuntimeBundle.Init.open(bundlePath)) {
            assertThat(reopened.isNew()).isFalse();
            assertThat(reopened.directory().liveRegionCount()).isEqualTo(6);
            assertThat(reopened.directory().bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_RUNTIME);

            MemorySegment edirSlice = reopened.regionSegment(RegionId.ENTITY_DIRECTORY);
            assertThat(MemoryHeader.isValid(edirSlice, 0)).isTrue();
            assertThat(MemoryHeader.readCount(edirSlice, 0)).isEqualTo(37);
        }
    }

    @Test
    void mmapRegionsAreNonOverlapping(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, testSpecs())) {
            BundleDirectory dir = bundle.directory();
            List<RegionEntry> live = dir.liveRegions();

            // All regions should be sequential and non-overlapping
            for (int i = 1; i < live.size(); i++) {
                RegionEntry prev = live.get(i - 1);
                RegionEntry curr = live.get(i);
                assertThat(curr.offset())
                        .as("Region %s should start after %s ends", curr.regionId(), prev.regionId())
                        .isGreaterThanOrEqualTo(prev.offset() + prev.allocatedSize());
            }

            // All offsets should be page-aligned
            for (RegionEntry entry : live) {
                assertThat(entry.offset() % 4096)
                        .as("Region %s should be page-aligned", entry.regionId())
                        .isEqualTo(0);
            }
        }
    }

    // ── Growth Tests ──

    @Test
    void mmapGrowRegionDoublesCapacity(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, testSpecs())) {
            // Write some data to HEBBIAN before growth
            MemorySegment hebbianBefore = bundle.regionSegment(RegionId.HEBBIAN);
            long sizeBefore = hebbianBefore.byteSize();
            long now = System.currentTimeMillis();
            MemoryHeader.write(hebbianBefore, 0, 1, MemoryShape.GRAPH, 1,
                    200, 42, 32, LAYOUT_ID, now, now);
            hebbianBefore.set(ValueLayout.JAVA_LONG, MemoryHeader.HEADER_BYTES, 0xDEADBEEFL);

            // Grow
            bundle.growRegion(RegionId.HEBBIAN);

            // Verify new region is larger
            MemorySegment hebbianAfter = bundle.regionSegment(RegionId.HEBBIAN);
            assertThat(hebbianAfter.byteSize()).isGreaterThan(sizeBefore);

            // Verify data was preserved
            assertThat(MemoryHeader.isValid(hebbianAfter, 0)).isTrue();
            assertThat(MemoryHeader.readCount(hebbianAfter, 0)).isEqualTo(42);
            assertThat(hebbianAfter.get(ValueLayout.JAVA_LONG, MemoryHeader.HEADER_BYTES)).isEqualTo(0xDEADBEEFL);

            // Other regions should still be accessible
            assertThat(bundle.regionSegment(RegionId.WORKING)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.COACTIVATION)).isNotNull();
            assertThat(bundle.regionSegment(RegionId.ENTITY_DIRECTORY)).isNotNull();
        }
    }

    @Test
    void mmapGrowRegionSurvivesReopen(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");

        long sizeAfterGrow;
        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, testSpecs())) {
            // Write data
            MemorySegment hebbianSlice = bundle.regionSegment(RegionId.HEBBIAN);
            long now = System.currentTimeMillis();
            MemoryHeader.write(hebbianSlice, 0, 1, MemoryShape.GRAPH, 1,
                    200, 99, 32, LAYOUT_ID, now, now);

            // Grow
            bundle.growRegion(RegionId.HEBBIAN);
            sizeAfterGrow = bundle.regionSegment(RegionId.HEBBIAN).byteSize();
        }

        // Reopen and verify the grown region persists
        try (RuntimeBundle reopened = RuntimeBundle.Init.open(bundlePath)) {
            MemorySegment hebbianSlice = reopened.regionSegment(RegionId.HEBBIAN);
            assertThat(hebbianSlice.byteSize()).isEqualTo(sizeAfterGrow);
            assertThat(MemoryHeader.isValid(hebbianSlice, 0)).isTrue();
            assertThat(MemoryHeader.readCount(hebbianSlice, 0)).isEqualTo(99);
        }
    }

    // ── BundleManager Tests ──

    @Test
    void bundleManagerUsageAndGrowth() {
        try (RuntimeBundle bundle = RuntimeBundle.Init.heap(testSpecs())) {
            BundleManager mgr = new BundleManager(bundle, 0.80f);

            // Initial usage should be 0
            assertThat(mgr.regionUsage(RegionId.HEBBIAN)).isEqualTo(0f);
            assertThat(mgr.needsGrowth(RegionId.HEBBIAN)).isFalse();

            // growIfNeeded should return false (below threshold)
            // Note: growRegion fails for heap bundles, so we just test the threshold logic
            assertThat(mgr.growIfNeeded(RegionId.HEBBIAN)).isFalse();
        }
    }
}
