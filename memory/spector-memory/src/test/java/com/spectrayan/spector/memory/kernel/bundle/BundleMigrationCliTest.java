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

import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.ProceduralMemory;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.TextBlobMemory;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.model.MemoryType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BundleMigrationCli} — V3 → V4 partition migration.
 */
class BundleMigrationCliTest {

    private static final int VEC_BYTES = 16;
    private static final int CAPACITY = 64;

    @TempDir
    Path tempDir;

    // ──────────────────────────────────────────────────────────────
    // (a) Skip when no partitions exist
    // ──────────────────────────────────────────────────────────────

    @Test
    void migrateAll_noPartitionsDir_skips() {
        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migrateAll(tempDir, VEC_BYTES);
        assertEquals(BundleMigrationCli.MigrationResult.Status.SKIPPED, result.status());
        assertEquals(0, result.totalPartitions());
    }

    // ──────────────────────────────────────────────────────────────
    // (b) Skip when partition has no V3 files
    // ──────────────────────────────────────────────────────────────

    @Test
    void migratePartition_emptyPartition_skips() throws IOException {
        Path partDir = createPartitionDir(0);

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migratePartition(partDir, VEC_BYTES);
        assertEquals(BundleMigrationCli.MigrationResult.Status.SKIPPED, result.status());
    }

    // ──────────────────────────────────────────────────────────────
    // (c) Skip when already migrated
    // ──────────────────────────────────────────────────────────────

    @Test
    void migratePartition_alreadyMigrated_skips() throws IOException {
        Path partDir = createPartitionDir(0);
        createV3StoreFiles(partDir, 5);

        // Pre-create bundle file
        Files.createFile(StorageLayout.partitionBundleFile(partDir));

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migratePartition(partDir, VEC_BYTES);
        assertEquals(BundleMigrationCli.MigrationResult.Status.ALREADY_MIGRATED, result.status());
    }

    // ──────────────────────────────────────────────────────────────
    // (d) Successful single partition migration
    // ──────────────────────────────────────────────────────────────

    @Test
    void migratePartition_withV3Files_createsBundleAndBackups() throws IOException {
        Path partDir = createPartitionDir(0);
        int recordCount = 3;
        createV3StoreFiles(partDir, recordCount);

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migratePartition(partDir, VEC_BYTES);

        // Assert migration succeeded
        assertEquals(BundleMigrationCli.MigrationResult.Status.MIGRATED, result.status());
        assertEquals(1, result.migratedPartitions());

        // Assert bundle file was created
        Path bundleFile = StorageLayout.partitionBundleFile(partDir);
        assertTrue(Files.exists(bundleFile), "partition.bundle should exist");
        assertTrue(Files.size(bundleFile) > 0, "partition.bundle should have content");

        // Assert V3 files were backed up
        assertTrue(Files.exists(Path.of(StorageLayout.semanticMem(partDir) + ".v3bak")),
                "semantic.mem.v3bak should exist");
        assertTrue(Files.exists(Path.of(StorageLayout.episodicMem(partDir) + ".v3bak")),
                "episodic.mem.v3bak should exist");
        assertTrue(Files.exists(Path.of(StorageLayout.proceduralMem(partDir) + ".v3bak")),
                "procedural.mem.v3bak should exist");
        assertTrue(Files.exists(Path.of(StorageLayout.textDat(partDir) + ".v3bak")),
                "text.dat.v3bak should exist");

        // Assert original V3 files are gone (moved to backup)
        assertFalse(Files.exists(StorageLayout.semanticMem(partDir)),
                "semantic.mem should have been moved");
        assertFalse(Files.exists(StorageLayout.episodicMem(partDir)),
                "episodic.mem should have been moved");

        // Verify bundle can be reopened and record counts match
        PartitionBundle bundle = PartitionBundle.Init.open(bundleFile);
        MemorySegment semSlice = bundle.regionSegment(RegionId.SEMANTIC);
        int semCount = (int) RegionPreamble.readCount(semSlice, 0);
        assertEquals(recordCount, semCount, "Semantic record count should match");

        MemorySegment epiSlice = bundle.regionSegment(RegionId.EPISODIC);
        int epiCount = (int) RegionPreamble.readCount(epiSlice, 0);
        assertEquals(recordCount, epiCount, "Episodic record count should match");

        MemorySegment strengthSlice = bundle.regionSegment(RegionId.STRENGTH);
        int strengthCount = (int) RegionPreamble.readCount(strengthSlice, 0);
        assertEquals(recordCount * 3, strengthCount, "Strength record count should match total migrated engrams");

        // Verify strength state was populated for semantic slot 0
        StrengthLayout strengthLayout = StrengthLayout.INSTANCE;
        float effImportance = strengthLayout.readEffectiveImportance(strengthSlice, RegionPreamble.PREAMBLE_BYTES);
        assertEquals(0.5f, effImportance, 1e-4f, "Effective importance should match V1 header importance");

        bundle.close();
    }

    // ──────────────────────────────────────────────────────────────
    // (e) Batch migration — migrateAll
    // ──────────────────────────────────────────────────────────────

    @Test
    void migrateAll_multiplePartitions_migratesAll() throws IOException {
        // Create partitions/ dir
        Path partitionsDir = StorageLayout.partitionsDir(tempDir);
        Files.createDirectories(partitionsDir);

        // Two partitions with V3 files
        Path part0 = createPartitionDir(0);
        createV3StoreFiles(part0, 2);

        Path part1 = createPartitionDir(1);
        createV3StoreFiles(part1, 4);

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migrateAll(tempDir, VEC_BYTES);

        assertEquals(BundleMigrationCli.MigrationResult.Status.MIGRATED, result.status());
        assertEquals(2, result.totalPartitions());
        assertEquals(2, result.migratedPartitions());
        assertEquals(0, result.skippedPartitions());

        // Both bundles should exist
        assertTrue(Files.exists(StorageLayout.partitionBundleFile(part0)));
        assertTrue(Files.exists(StorageLayout.partitionBundleFile(part1)));
    }

    // ──────────────────────────────────────────────────────────────
    // (f) Runtime migration — migrateRuntime
    // ──────────────────────────────────────────────────────────────

    @Test
    void migrateRuntime_withV3Files_createsBundleAndBackups() throws IOException {
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        // Create V3 working.mem with SMKM header
        Path workingFile = StorageLayout.workingMem(tempDir);
        try (FileChannel fc = FileChannel.open(workingFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            int stride = 128;
            long fileSize = RegionPreamble.PREAMBLE_BYTES + 100L * stride;
            fc.write(java.nio.ByteBuffer.allocate(1), fileSize - 1);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize, arena);
                RegionPreamble.write(seg, 0, 1, MemoryShape.RECORD, 1, 100, 15, stride, 0x574F524B, 1000L, 2000L);
            }
        }

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migrateRuntime(tempDir, VEC_BYTES);

        assertEquals(BundleMigrationCli.MigrationResult.Status.MIGRATED, result.status());
        assertEquals(1, result.migratedPartitions());

        Path runtimeBundleFile = StorageLayout.runtimeBundleFile(tempDir);
        assertTrue(Files.exists(runtimeBundleFile), "runtime.bundle should exist");

        // Verify working.mem.v3bak exists and working.mem was moved
        assertTrue(Files.exists(Path.of(workingFile + ".v3bak")), "working.mem.v3bak should exist");
        assertFalse(Files.exists(workingFile), "working.mem should have been moved");

        // Verify runtime bundle can be opened and contains data
        try (RuntimeBundle bundle = RuntimeBundle.Init.open(runtimeBundleFile)) {
            MemorySegment workingSlice = bundle.regionSegment(RegionId.WORKING);
            assertTrue(RegionPreamble.isValid(workingSlice, 0));
            assertEquals(15, RegionPreamble.readCount(workingSlice, 0));
        }
    }

    @Test
    void migrateRuntime_alreadyMigrated_skips() throws IOException {
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        Path runtimeBundleFile = StorageLayout.runtimeBundleFile(tempDir);
        Files.createFile(runtimeBundleFile);

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migrateRuntime(tempDir, VEC_BYTES);

        assertEquals(BundleMigrationCli.MigrationResult.Status.ALREADY_MIGRATED, result.status());
    }

    @Test
    void migrateAllWithRuntime_migratesBothRuntimeAndPartitions() throws IOException {
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        Path workingFile = StorageLayout.workingMem(tempDir);
        try (FileChannel fc = FileChannel.open(workingFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long fileSize = RegionPreamble.PREAMBLE_BYTES + 64;
            fc.write(java.nio.ByteBuffer.allocate(1), fileSize - 1);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize, arena);
                RegionPreamble.write(seg, 0, 1, MemoryShape.RECORD, 1, 1, 1, 64, 0x574F524B, 1000L, 2000L);
            }
        }

        Path part0 = createPartitionDir(0);
        createV3StoreFiles(part0, 2);

        BundleMigrationCli.MigrationResult result =
                BundleMigrationCli.migrateAllWithRuntime(tempDir, VEC_BYTES);

        assertEquals(BundleMigrationCli.MigrationResult.Status.MIGRATED, result.status());
        assertTrue(Files.exists(StorageLayout.runtimeBundleFile(tempDir)));
        assertTrue(Files.exists(StorageLayout.partitionBundleFile(part0)));
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private Path createPartitionDir(int seq) throws IOException {
        Path partitionsDir = StorageLayout.partitionsDir(tempDir);
        Files.createDirectories(partitionsDir);
        Path partDir = StorageLayout.partitionDir(tempDir, seq, 1700000000L + seq);
        Files.createDirectories(partDir);
        return partDir;
    }

    /**
     * Creates V3 store files with real SMKM headers and record data.
     */
    private void createV3StoreFiles(Path partDir, int recordCount) throws IOException {
        // Create cognitive stores using the real store constructors
        SemanticMemory semantic = new SemanticMemory(
                VEC_BYTES, CAPACITY, StorageLayout.semanticMem(partDir));
        EpisodicRecordMemory episodic = new EpisodicRecordMemory(
                StorageLayout.episodicMem(partDir), VEC_BYTES, CAPACITY);
        ProceduralMemory procedural = new ProceduralMemory(
                VEC_BYTES, CAPACITY, StorageLayout.proceduralMem(partDir));

        // Write some records using the store API
        byte[] vec = new byte[VEC_BYTES];
        for (int i = 0; i < recordCount; i++) {
            long ts = System.currentTimeMillis();
            var header = EncodingHeader.create(
                    ts, 0L, 1.0f, 0.5f, (short) 0, MemoryType.SEMANTIC);
            semantic.write(header, vec);

            header = EncodingHeader.create(
                    ts, 0L, 1.0f, 0.5f, (short) 0, MemoryType.EPISODIC);
            episodic.write(header, vec);

            header = EncodingHeader.create(
                    ts, 0L, 1.0f, 0.5f, (short) 0, MemoryType.PROCEDURAL);
            procedural.write(header, vec);
        }

        semantic.close();
        episodic.close();
        procedural.close();

        // Create text.dat with a minimal SMKM header
        TextBlobMemory text = new TextBlobMemory(
                StorageLayout.textDat(partDir), DataEncryptor.NOOP);
        text.close();
    }
}
