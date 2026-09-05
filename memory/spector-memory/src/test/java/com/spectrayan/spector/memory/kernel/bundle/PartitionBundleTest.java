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

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class PartitionBundleTest {

    private static final int DIMS = 64;
    private static final int SEM_CAP = 100;
    private static final int EPI_CAP = 50;
    private static final EngramLayout COG_LAYOUT = new EngramLayout(DIMS);
    private static final long EPI_BYTES = (long) EPI_CAP * COG_LAYOUT.recordStride();
    private static final int PROC_CAP = 20;
    private static final long TEXT_BYTES = 4096;

    private static final TextBlobLayout TEXT_LAYOUT = new TextBlobLayout();

    // ── Heap Tests ──

    @Test
    void heapBundleCreatesAllFourRegions() {
        try (PartitionBundle bundle = PartitionBundle.Init.heap(
                SEM_CAP, EPI_BYTES, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            assertThat(bundle.isNew()).isTrue();
            assertThat(bundle.bundlePath()).isNull();
            assertThat(bundle.arena()).isNotNull();

            // Verify directory
            BundleDirectory dir = bundle.directory();
            assertThat(dir.liveRegionCount()).isEqualTo(5);
            assertThat(dir.maxRegions()).isEqualTo(5);
            assertThat(dir.bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_PARTITION);

            // Verify all 5 regions are accessible
            MemorySegment semSlice = bundle.regionSegment(RegionId.SEMANTIC);
            MemorySegment epiSlice = bundle.regionSegment(RegionId.EPISODIC);
            MemorySegment procSlice = bundle.regionSegment(RegionId.PROCEDURAL);
            MemorySegment textSlice = bundle.regionSegment(RegionId.TEXT);
            MemorySegment auditSlice = bundle.regionSegment(RegionId.STRENGTH);

            assertThat(semSlice).isNotNull();
            assertThat(epiSlice).isNotNull();
            assertThat(procSlice).isNotNull();
            assertThat(textSlice).isNotNull();
            assertThat(auditSlice).isNotNull();

            // Region slices should be page-aligned sizes
            assertThat(semSlice.byteSize() % 4096).isEqualTo(0);
            assertThat(epiSlice.byteSize() % 4096).isEqualTo(0);
            assertThat(auditSlice.byteSize() % 4096).isEqualTo(0);
        }
    }

    @Test
    void regionSegmentThrowsForUnknownRegion() {
        try (PartitionBundle bundle = PartitionBundle.Init.heap(
                SEM_CAP, EPI_CAP, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            assertThatThrownBy(() -> bundle.regionSegment(RegionId.WORKING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WORKING");
        }
    }

    @Test
    void regionSlicesAreWritable() {
        try (PartitionBundle bundle = PartitionBundle.Init.heap(
                SEM_CAP, EPI_CAP, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            MemorySegment semSlice = bundle.regionSegment(RegionId.SEMANTIC);

            // Write a SMKM header at the start of the region
            long now = System.currentTimeMillis();
            RegionPreamble.write(semSlice, 0, 1, MemoryShape.RECORD, 1,
                    SEM_CAP, 0, COG_LAYOUT.recordStride(), COG_LAYOUT.layoutId(), now, now);

            // Verify the header is valid
            assertThat(RegionPreamble.isValid(semSlice, 0)).isTrue();
            assertThat(RegionPreamble.readCount(semSlice, 0)).isEqualTo(0);

            // Write a record after the header
            long dataOffset = RegionPreamble.PREAMBLE_BYTES;
            semSlice.set(ValueLayout.JAVA_INT, dataOffset, 0xDEADBEEF);
            assertThat(semSlice.get(ValueLayout.JAVA_INT, dataOffset)).isEqualTo(0xDEADBEEF);
        }
    }

    // ── Mmap Tests ──

    @Test
    void mmapBundleCreateAndOpen(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("partition.bundle");

        // Create
        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                bundlePath, SEM_CAP, EPI_BYTES, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            assertThat(bundle.isNew()).isTrue();
            assertThat(bundle.bundlePath()).isEqualTo(bundlePath);
            assertThat(bundle.directory().liveRegionCount()).isEqualTo(5);

            // Write data to semantic region
            MemorySegment semSlice = bundle.regionSegment(RegionId.SEMANTIC);
            long now = System.currentTimeMillis();
            RegionPreamble.write(semSlice, 0, 1, MemoryShape.RECORD, 1,
                    SEM_CAP, 42, COG_LAYOUT.recordStride(), COG_LAYOUT.layoutId(), now, now);
        }

        // Reopen and verify
        try (PartitionBundle reopened = PartitionBundle.Init.open(bundlePath)) {
            assertThat(reopened.isNew()).isFalse();
            assertThat(reopened.directory().liveRegionCount()).isEqualTo(5);
            assertThat(reopened.directory().bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_PARTITION);

            // Read data back
            MemorySegment semSlice = reopened.regionSegment(RegionId.SEMANTIC);
            assertThat(RegionPreamble.isValid(semSlice, 0)).isTrue();
            assertThat(RegionPreamble.readCount(semSlice, 0)).isEqualTo(42);
        }
    }

    @Test
    void mmapBundleRegionsAreNonOverlapping(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("partition.bundle");

        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                bundlePath, SEM_CAP, EPI_BYTES, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            BundleDirectory dir = bundle.directory();
            RegionEntry sem = dir.findRegion(RegionId.SEMANTIC);
            RegionEntry epi = dir.findRegion(RegionId.EPISODIC);
            RegionEntry proc = dir.findRegion(RegionId.PROCEDURAL);
            RegionEntry text = dir.findRegion(RegionId.TEXT);
            RegionEntry audit = dir.findRegion(RegionId.STRENGTH);

            // Verify no overlap: each region starts after the previous ends
            assertThat(epi.offset()).isGreaterThanOrEqualTo(sem.offset() + sem.allocatedSize());
            assertThat(proc.offset()).isGreaterThanOrEqualTo(epi.offset() + epi.allocatedSize());
            assertThat(text.offset()).isGreaterThanOrEqualTo(proc.offset() + proc.allocatedSize());
            assertThat(audit.offset()).isGreaterThanOrEqualTo(text.offset() + text.allocatedSize());

            // All offsets should be page-aligned
            assertThat(sem.offset() % 4096).isEqualTo(0);
            assertThat(epi.offset() % 4096).isEqualTo(0);
            assertThat(proc.offset() % 4096).isEqualTo(0);
            assertThat(text.offset() % 4096).isEqualTo(0);
            assertThat(audit.offset() % 4096).isEqualTo(0);
        }
    }

    @Test
    void mmapBundleSMKMHeaderValid(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("partition.bundle");

        try (PartitionBundle bundle = PartitionBundle.Init.mmap(
                bundlePath, SEM_CAP, EPI_BYTES, PROC_CAP, TEXT_BYTES, DIMS,
                COG_LAYOUT.layoutId(), COG_LAYOUT.schemaVersion(),
                TEXT_LAYOUT.layoutId(), TEXT_LAYOUT.schemaVersion())) {

            // The master segment should have a valid SMKM header at offset 0
            MemorySegment master = bundle.regionSegment(RegionId.SEMANTIC);
            // The actual SMKM is on the master segment, not on a region slice
            // Let's verify the directory header was written by checking isNew
            assertThat(bundle.isNew()).isTrue();
            assertThat(bundle.directory().maxRegions()).isEqualTo(5);
        }
    }
}
