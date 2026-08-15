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
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot migration utility that converts V3 partition stores (individual
 * {@code semantic.mem}, {@code episodic.mem}, {@code procedural.mem}, {@code text.dat})
 * into the V4 {@code partition.bundle} format (ADR-0004).
 *
 * <p>The migration is non-destructive: original {@code .mem} files are preserved as
 * {@code .mem.v3bak} backups. A fidelity check verifies that the bundle contains
 * the same record counts as the source stores before declaring success.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Programmatic — migrate all partitions under a namespace:
 * BundleMigrationCli.migrateAll(namespacePath);
 *
 * // Programmatic — migrate a single partition:
 * BundleMigrationCli.migratePartition(partitionDir, 768);
 *
 * // CLI:
 * java -cp spector-memory.jar BundleMigrationCli /path/to/namespace --dimensions 768
 * }</pre>
 *
 * @since 1.1.0
 * @see PartitionBundle
 * @see StorageLayout
 */
public final class BundleMigrationCli {

    private static final Logger log = LoggerFactory.getLogger(BundleMigrationCli.class);

    /** Default vector dimensions if not specified. */
    private static final int DEFAULT_DIMENSIONS = 768;

    /** Backup suffix appended to original V3 files after successful migration. */
    private static final String BACKUP_SUFFIX = ".v3bak";

    private BundleMigrationCli() {} // utility class

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════

    /**
     * Migrates all V3 runtime state and partition stores under the given namespace/base path to V4 bundles.
     *
     * @param basePath   the root persistence path
     * @param dimensions embedding vector dimensions
     * @return aggregate migration result
     */
    public static MigrationResult migrateAllWithRuntime(Path basePath, int dimensions) {
        MigrationResult runtimeResult = migrateRuntime(basePath, dimensions);
        MigrationResult partitionsResult = migrateAll(basePath, dimensions);

        int total = runtimeResult.totalPartitions() + partitionsResult.totalPartitions();
        int migrated = runtimeResult.migratedPartitions() + partitionsResult.migratedPartitions();
        int skipped = runtimeResult.skippedPartitions() + partitionsResult.skippedPartitions();

        return new MigrationResult(
                migrated > 0 ? MigrationResult.Status.MIGRATED : MigrationResult.Status.SKIPPED,
                total, migrated, skipped, null);
    }

    /**
     * Migrates V3 standalone runtime stores (working, coactivation, index, hebbian,
     * temporal, entity directory, hypergraph, registries, bm25, checkpoint) into
     * a unified V4 {@code runtime.bundle} file.
     *
     * @param basePath   the root persistence path (contains {@code runtime/} dir)
     * @param dimensions embedding vector dimensions
     * @return migration result for runtime state
     */
    public static MigrationResult migrateRuntime(Path basePath, int dimensions) {
        Path runtimeDir = StorageLayout.runtimeDir(basePath);
        if (!Files.isDirectory(runtimeDir)) {
            log.info("BundleMigration: no runtime/ directory at {} — skipping", basePath);
            return new MigrationResult(MigrationResult.Status.SKIPPED, 1, 0, 1, "No runtime/ directory");
        }

        Path runtimeBundleFile = StorageLayout.runtimeBundleFile(basePath);
        if (Files.exists(runtimeBundleFile)) {
            log.info("BundleMigration: runtime.bundle already exists at {} — skipping", runtimeDir);
            return new MigrationResult(MigrationResult.Status.ALREADY_MIGRATED, 1, 0, 1, "runtime.bundle already present");
        }

        Path workingFile = StorageLayout.workingMem(basePath);
        Path coactFile = StorageLayout.coactivationTracker(basePath);
        Path indexFile = StorageLayout.indexMidxRuntime(basePath);
        Path hebbianFile = StorageLayout.hebbianGraphRuntime(basePath);
        Path temporalFile = StorageLayout.temporalChainRuntime(basePath);
        Path tfactsFile = StorageLayout.temporalFactsRuntime(basePath);
        Path edirFile = StorageLayout.entityDirectoryRuntime(basePath);
        Path hyegFile = StorageLayout.hyperEntityGraphRuntime(basePath);
        Path etypesFile = StorageLayout.entityTypesRuntime(basePath);
        Path rtypesFile = StorageLayout.relationTypesRuntime(basePath);
        Path bm25File = StorageLayout.bm25BidxRuntime(basePath);
        Path ckptFile = StorageLayout.checkpointMeta(basePath);

        boolean hasAny = Files.exists(workingFile) || Files.exists(coactFile) || Files.exists(indexFile)
                || Files.exists(hebbianFile) || Files.exists(temporalFile) || Files.exists(tfactsFile)
                || Files.exists(edirFile) || Files.exists(hyegFile) || Files.exists(etypesFile)
                || Files.exists(rtypesFile) || Files.exists(bm25File) || Files.exists(ckptFile);

        if (!hasAny) {
            log.info("BundleMigration: no V3 runtime store files in {} — skipping", runtimeDir);
            return new MigrationResult(MigrationResult.Status.SKIPPED, 1, 0, 1, "No V3 runtime store files found");
        }

        log.info("BundleMigration: migrating runtime directory {} (dim={})", runtimeDir, dimensions);

        try (Arena readArena = Arena.ofConfined()) {
            V3StoreInfo working = readV3Store(readArena, workingFile, "working");
            V3StoreInfo coact = readV3Store(readArena, coactFile, "coactivation");
            V3StoreInfo index = readV3Store(readArena, indexFile, "index");
            V3StoreInfo hebbian = readV3Store(readArena, hebbianFile, "hebbian");
            V3StoreInfo temporal = readV3Store(readArena, temporalFile, "temporal");
            V3StoreInfo tfacts = readV3Store(readArena, tfactsFile, "temporal-facts");
            V3StoreInfo edir = readV3Store(readArena, edirFile, "entity-directory");
            V3StoreInfo hyeg = readV3Store(readArena, hyegFile, "hypergraph");
            V3StoreInfo etypes = readV3Store(readArena, etypesFile, "entity-types");
            V3StoreInfo rtypes = readV3Store(readArena, rtypesFile, "relation-types");
            V3StoreInfo ckpt = readV3Store(readArena, ckptFile, "checkpoint");

            CognitiveRecordLayout cogLayout = new CognitiveRecordLayout(dimensions);
            int workingCap = Math.max(working.count, 1000);
            int graphCap = Math.max(hebbian.count, 10000);
            int temporalCap = Math.max(temporal.count, 10000);
            int hyperCap = Math.max(edir.count, 1000);
            int hyperEdgeCap = hyperCap * 2;
            int pairCap = 10000;
            int edgeCap = 20000;

            List<RegionSizeSpec> specs = List.of(
                    new RegionSizeSpec(
                            RegionId.WORKING,
                            MemoryHeader.HEADER_BYTES + (long) cogLayout.recordStride() * workingCap,
                            workingCap,
                            cogLayout.recordStride(),
                            cogLayout.layoutId(),
                            cogLayout.schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.COACTIVATION,
                            coact.segment != null ? Math.max(64 + 8 + 32L * pairCap + 40L * edgeCap, coact.segment.byteSize()) : 64 + 8 + 32L * pairCap + 40L * edgeCap,
                            pairCap,
                            0,
                            new com.spectrayan.spector.memory.kernel.layout.CoActivationLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.CoActivationLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.INDEX_MIDX,
                            index.segment != null ? Math.max(64 + 10000L * 32, index.segment.byteSize()) : 64 + 10000L * 32,
                            10000,
                            new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().recordStride(),
                            new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.INDEX_IDPL,
                            1024 * 1024L,
                            1,
                            1,
                            0,
                            1,
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.HEBBIAN,
                            hebbian.segment != null ? Math.max(64 + 8 + 24L * graphCap + 12L * graphCap * 16, hebbian.segment.byteSize()) : 64 + 8 + 24L * graphCap + 12L * graphCap * 16,
                            graphCap,
                            0,
                            new com.spectrayan.spector.memory.kernel.layout.HebbianLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.HebbianLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.TEMPORAL_CHAIN,
                            temporal.segment != null ? Math.max(64 + 24L * temporalCap, temporal.segment.byteSize()) : 64 + 24L * temporalCap,
                            temporalCap,
                            24,
                            new com.spectrayan.spector.memory.kernel.layout.TemporalLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.TemporalLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.TEMPORAL_FACTS,
                            tfacts.segment != null ? Math.max(64 + 4L * 1024 * 1024, tfacts.segment.byteSize()) : 64 + 4L * 1024 * 1024,
                            1,
                            0,
                            new com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.ENTITY_DIRECTORY,
                            edir.segment != null ? Math.max(64 + 16 + 64L * hyperCap, edir.segment.byteSize()) : 64 + 16 + 64L * hyperCap,
                            hyperCap,
                            64,
                            new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.ENTITY_NAMES,
                            64 + 16 + 8L * hyperCap * 16 + 32L * hyperCap,
                            1,
                            8,
                            new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                            true
                    ),
                    new RegionSizeSpec(
                            RegionId.HYPERGRAPH,
                            hyeg.segment != null ? Math.max(64 + 16 + 48L * hyperEdgeCap + 128L * hyperEdgeCap, hyeg.segment.byteSize()) : 64 + 16 + 48L * hyperEdgeCap + 128L * hyperEdgeCap,
                            hyperCap,
                            48,
                            new com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.ENTITY_TYPES,
                            etypes.segment != null ? Math.max(64 + 1024 * 64L, etypes.segment.byteSize()) : 64 + 1024 * 64L,
                            1024,
                            0,
                            new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.RELATION_TYPES,
                            rtypes.segment != null ? Math.max(64 + 1024 * 64L, rtypes.segment.byteSize()) : 64 + 1024 * 64L,
                            1024,
                            0,
                            new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().layoutId(),
                            new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().schemaVersion(),
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.INSULA,
                            4096L,
                            1,
                            0,
                            com.spectrayan.spector.memory.insula.InsularLayout.LAYOUT_ID,
                            com.spectrayan.spector.memory.insula.InsularLayout.SCHEMA_VERSION,
                            false
                    ),
                    new RegionSizeSpec(
                            RegionId.CHECKPOINT,
                            128L * 1024,
                            1,
                            0,
                            0x434B5054,
                            1,
                            true
                    ),
                    new RegionSizeSpec(
                            RegionId.BM25,
                            4L * 1024 * 1024,
                            1,
                            0,
                            0x42494458,
                            1,
                            true
                    )
            );

            RuntimeBundle bundle = RuntimeBundle.Init.mmap(runtimeBundleFile, specs);

            copyRuntimeRegionData(working, bundle, RegionId.WORKING, "working");
            copyRuntimeRegionData(coact, bundle, RegionId.COACTIVATION, "coactivation");
            copyRuntimeRegionData(index, bundle, RegionId.INDEX_MIDX, "index");
            copyRuntimeRegionData(hebbian, bundle, RegionId.HEBBIAN, "hebbian");
            copyRuntimeRegionData(temporal, bundle, RegionId.TEMPORAL_CHAIN, "temporal");
            copyRuntimeRegionData(tfacts, bundle, RegionId.TEMPORAL_FACTS, "temporal-facts");
            copyRuntimeRegionData(edir, bundle, RegionId.ENTITY_DIRECTORY, "entity-directory");
            copyRuntimeRegionData(hyeg, bundle, RegionId.HYPERGRAPH, "hypergraph");
            copyRuntimeRegionData(etypes, bundle, RegionId.ENTITY_TYPES, "entity-types");
            copyRuntimeRegionData(rtypes, bundle, RegionId.RELATION_TYPES, "relation-types");
            copyRuntimeRegionData(ckpt, bundle, RegionId.CHECKPOINT, "checkpoint");

            if (Files.exists(bm25File)) {
                try {
                    com.spectrayan.spector.index.BM25Index loaded = com.spectrayan.spector.index.BM25Index.load(bm25File);
                    if (loaded != null) {
                        MemorySegment bm25Slice = bundle.regionSegment(RegionId.BM25);
                        loaded.saveToRegion(bm25Slice);
                        log.info("BundleMigration: migrated BM25 index with {} docs", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("BundleMigration: failed to load/migrate BM25 index: {}", e.getMessage());
                }
            }

            bundle.close();

            backupV3File(workingFile);
            backupV3File(coactFile);
            backupV3File(indexFile);
            backupV3File(hebbianFile);
            backupV3File(temporalFile);
            backupV3File(tfactsFile);
            backupV3File(edirFile);
            backupV3File(hyegFile);
            backupV3File(etypesFile);
            backupV3File(rtypesFile);
            backupV3File(bm25File);
            backupV3File(ckptFile);

            log.info("BundleMigration: ✓ runtime directory {} migrated to runtime.bundle", runtimeDir);
            return new MigrationResult(MigrationResult.Status.MIGRATED, 1, 1, 0, null);

        } catch (MigrationException e) {
            try {
                Files.deleteIfExists(runtimeBundleFile);
            } catch (IOException ignored) {}
            throw e;
        } catch (Exception e) {
            try {
                Files.deleteIfExists(runtimeBundleFile);
            } catch (IOException ignored) {}
            throw new MigrationException("Runtime migration failed for " + basePath, e);
        }
    }

    /**
     * Migrates all V3 partitions under the given namespace/base path to V4 bundles.
     *
     * @param basePath   the root persistence path (contains {@code partitions/} dir)
     * @param dimensions embedding vector dimensions (used to compute cognitive stride)
     * @return aggregate migration result
     */
    public static MigrationResult migrateAll(Path basePath, int dimensions) {
        Path partitionsDir = StorageLayout.partitionsDir(basePath);
        if (!Files.isDirectory(partitionsDir)) {
            log.info("BundleMigration: no partitions/ directory at {} — skipping", basePath);
            return new MigrationResult(MigrationResult.Status.SKIPPED, 0, 0, 0,
                    "No partitions/ directory found");
        }

        List<Path> partitionDirs;
        try {
            partitionDirs = PartitionManager_discoverAllPartitions(partitionsDir);
        } catch (IOException e) {
            throw new MigrationException("Failed to discover partitions at " + partitionsDir, e);
        }

        if (partitionDirs.isEmpty()) {
            log.info("BundleMigration: no partition directories found under {}", partitionsDir);
            return new MigrationResult(MigrationResult.Status.SKIPPED, 0, 0, 0,
                    "No partition directories");
        }

        int total = partitionDirs.size();
        int migrated = 0;
        int skipped = 0;

        for (Path dir : partitionDirs) {
            MigrationResult r = migratePartition(dir, dimensions);
            if (r.status() == MigrationResult.Status.MIGRATED) {
                migrated++;
            } else {
                skipped++;
            }
        }

        log.info("BundleMigration: completed {} partitions — {} migrated, {} skipped",
                total, migrated, skipped);
        return new MigrationResult(
                migrated > 0 ? MigrationResult.Status.MIGRATED : MigrationResult.Status.SKIPPED,
                total, migrated, skipped, null);
    }

    /**
     * Migrates a single V3 partition directory to V4 bundle format.
     *
     * <p>Steps:
     * <ol>
     *   <li>Guard: skip if {@code partition.bundle} already exists</li>
     *   <li>Open each V3 file as read-only mmap</li>
     *   <li>Read record counts from SMKM headers</li>
     *   <li>Create a new {@link PartitionBundle} with matching capacities</li>
     *   <li>Bulk-copy each region's data (header + records)</li>
     *   <li>Fidelity check: verify record counts match</li>
     *   <li>Backup original files with {@code .v3bak} suffix</li>
     * </ol>
     * </p>
     *
     * @param partitionDir the partition directory (e.g., {@code partitions/000_1234567890})
     * @param dimensions   embedding vector dimensions
     * @return migration result for this partition
     * @throws MigrationException if migration or fidelity check fails
     */
    public static MigrationResult migratePartition(Path partitionDir, int dimensions) {
        Path bundleFile = StorageLayout.partitionBundleFile(partitionDir);

        // Guard: already migrated
        if (Files.exists(bundleFile)) {
            log.info("BundleMigration: partition.bundle already exists at {} — skipping",
                    partitionDir.getFileName());
            return new MigrationResult(MigrationResult.Status.ALREADY_MIGRATED, 1, 0, 1,
                    "partition.bundle already present");
        }

        // V3 source files
        Path semanticFile = StorageLayout.semanticMem(partitionDir);
        Path episodicFile = StorageLayout.episodicMem(partitionDir);
        Path proceduralFile = StorageLayout.proceduralMem(partitionDir);
        Path textFile = StorageLayout.textDat(partitionDir);

        // Guard: no V3 files at all
        boolean hasAny = Files.exists(semanticFile) || Files.exists(episodicFile)
                || Files.exists(proceduralFile) || Files.exists(textFile);
        if (!hasAny) {
            log.info("BundleMigration: no V3 store files in {} — skipping", partitionDir.getFileName());
            return new MigrationResult(MigrationResult.Status.SKIPPED, 1, 0, 1,
                    "No V3 store files found");
        }

        log.info("BundleMigration: migrating partition {} (dim={})", partitionDir.getFileName(), dimensions);

        // Read V3 files as mmap segments and extract metadata
        try (Arena readArena = Arena.ofConfined()) {
            V3StoreInfo semantic = readV3Store(readArena, semanticFile, "semantic");
            V3StoreInfo episodic = readV3Store(readArena, episodicFile, "episodic");
            V3StoreInfo procedural = readV3Store(readArena, proceduralFile, "procedural");
            V3StoreInfo text = readV3Store(readArena, textFile, "text");

            // Compute capacities from V3 store metadata
            CognitiveRecordLayout cogLayout = new CognitiveRecordLayout(dimensions);
            TextBlobLayout textLayout = new TextBlobLayout();

            // Compute capacities from V3 file sizes (not record count).
            // V3 files are allocated as HEADER_BYTES + capacity * stride, so back-calculate.
            int cogStride = cogLayout.stride();
            int semanticCap = fileCapacity(semantic, cogStride);
            int episodicCap = fileCapacity(episodic, cogStride);
            long episodicBytes = episodic.segment != null
                    ? Math.max(episodic.segment.byteSize() - MemoryHeader.HEADER_BYTES, (long) episodicCap * cogStride)
                    : (long) episodicCap * cogStride;
            int proceduralCap = fileCapacity(procedural, cogStride);
            long textBytes = text.segment != null
                    ? Math.max(text.segment.byteSize() - MemoryHeader.HEADER_BYTES, 1024)
                    : 1024;

            // Create the V4 bundle
            PartitionBundle bundle = PartitionBundle.Init.mmap(
                    bundleFile,
                    semanticCap, episodicBytes, proceduralCap, textBytes,
                    dimensions,
                    cogLayout.layoutId(), cogLayout.schemaVersion(),
                    textLayout.layoutId(), textLayout.schemaVersion());

            // Bulk-copy V3 data into V4 bundle regions
            copyRegionData(semantic, bundle, RegionId.SEMANTIC, "semantic");
            copyRegionData(episodic, bundle, RegionId.EPISODIC, "episodic");
            copyRegionData(procedural, bundle, RegionId.PROCEDURAL, "procedural");
            copyRegionData(text, bundle, RegionId.TEXT, "text");

            // Fidelity check
            assertFidelity(bundle, semantic, episodic, procedural, text, partitionDir);

            // Close bundle (flushes directory to disk)
            bundle.close();

            // Backup V3 files
            backupV3File(semanticFile);
            backupV3File(episodicFile);
            backupV3File(proceduralFile);
            backupV3File(textFile);

            log.info("BundleMigration: ✓ partition {} migrated — sem={}, epi={}, proc={}, text={}B",
                    partitionDir.getFileName(), semantic.count, episodic.count,
                    procedural.count, textBytes);

            return new MigrationResult(MigrationResult.Status.MIGRATED, 1, 1, 0, null);

        } catch (MigrationException e) {
            // Clean up partial bundle file on failure
            try {
                Files.deleteIfExists(bundleFile);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw e;
        } catch (Exception e) {
            // Clean up partial bundle file on failure
            try {
                Files.deleteIfExists(bundleFile);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw new MigrationException("Migration failed for partition " + partitionDir, e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL
    // ══════════════════════════════════════════════════════════════

    /**
     * Holds metadata and mmap segment for a V3 store file.
     */
    private record V3StoreInfo(Path path, MemorySegment segment, int count, long dataSize) {
        boolean exists() { return segment != null; }
    }

    /**
     * Back-calculates the original capacity from a V3 store file's size.
     * V3 files are allocated as {@code HEADER_BYTES + capacity * stride}.
     */
    private static int fileCapacity(V3StoreInfo info, int stride) {
        if (!info.exists() || stride <= 0) return 1;
        long dataBytes = info.segment.byteSize() - MemoryHeader.HEADER_BYTES;
        return Math.max((int) (dataBytes / stride), 1);
    }

    /**
     * Opens a V3 store file read-only, reads its SMKM header, and extracts
     * the record count.
     */
    private static V3StoreInfo readV3Store(Arena arena, Path file, String name) {
        if (!Files.exists(file)) {
            log.debug("BundleMigration: V3 {} not found at {} — will create empty region", name, file);
            return new V3StoreInfo(file, null, 0, 0);
        }

        try {
            long fileSize = Files.size(file);
            if (fileSize < MemoryHeader.HEADER_BYTES) {
                log.warn("BundleMigration: V3 {} file too small ({}B) — treating as empty", name, fileSize);
                return new V3StoreInfo(file, null, 0, 0);
            }

            MemorySegment mapped;
            try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ)) {
                mapped = fc.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);
            }

            int count = (int) MemoryHeader.readCount(mapped, 0);
            long dataSize = fileSize - MemoryHeader.HEADER_BYTES;

            log.info("BundleMigration: V3 {} — {} records, {}KB data",
                    name, count, dataSize / 1024);
            return new V3StoreInfo(file, mapped, count, dataSize);

        } catch (IOException e) {
            throw new MigrationException("Failed to read V3 " + name + " at " + file, e);
        }
    }

    /**
     * Copies V3 store data (full segment including header) into the corresponding
     * V4 bundle region, byte-for-byte.
     */
    private static void copyRegionData(V3StoreInfo source, PartitionBundle bundle,
                                        RegionId regionId, String name) {
        if (!source.exists()) {
            log.debug("BundleMigration: skipping {} — no source data", name);
            return;
        }

        MemorySegment targetSlice = bundle.regionSegment(regionId);
        long sourceSize = source.segment.byteSize();
        long targetSize = targetSlice.byteSize();

        if (sourceSize > targetSize) {
            throw new MigrationException(String.format(
                    "V3 %s data (%dKB) exceeds V4 region capacity (%dKB) — "
                            + "increase capacity or re-run with larger dimensions",
                    name, sourceSize / 1024, targetSize / 1024));
        }

        // Bulk copy: header + all records
        MemorySegment.copy(source.segment, 0, targetSlice, 0, sourceSize);
        log.debug("BundleMigration: copied {} — {}KB into bundle region", name, sourceSize / 1024);
    }

    /**
     * Copies V3 runtime store data into the corresponding V4 runtime bundle region.
     */
    private static void copyRuntimeRegionData(V3StoreInfo source, RuntimeBundle bundle,
                                               RegionId regionId, String name) {
        if (!source.exists()) {
            log.debug("BundleMigration: skipping runtime {} — no source data", name);
            return;
        }

        MemorySegment targetSlice = bundle.regionSegment(regionId);
        long sourceSize = source.segment.byteSize();
        long targetSize = targetSlice.byteSize();

        if (sourceSize > targetSize) {
            throw new MigrationException(String.format(
                    "V3 runtime %s data (%dKB) exceeds V4 region capacity (%dKB)",
                    name, sourceSize / 1024, targetSize / 1024));
        }

        MemorySegment.copy(source.segment, 0, targetSlice, 0, sourceSize);
        log.debug("BundleMigration: copied runtime {} — {}KB into bundle region", name, sourceSize / 1024);
    }

    /**
     * Verifies round-trip fidelity by reading record counts from the bundle
     * and comparing against the original V3 counts.
     */
    private static void assertFidelity(PartitionBundle bundle,
                                        V3StoreInfo semantic, V3StoreInfo episodic,
                                        V3StoreInfo procedural, V3StoreInfo text,
                                        Path partitionDir) {
        assertRegionCount(bundle, RegionId.SEMANTIC, semantic.count, "semantic", partitionDir);
        assertRegionCount(bundle, RegionId.EPISODIC, episodic.count, "episodic", partitionDir);
        assertRegionCount(bundle, RegionId.PROCEDURAL, procedural.count, "procedural", partitionDir);
        // Text uses append-log; count check is best-effort
        if (text.exists()) {
            assertRegionCount(bundle, RegionId.TEXT, text.count, "text", partitionDir);
        }

        log.info("BundleMigration: fidelity check passed for {}", partitionDir.getFileName());
    }

    private static void assertRegionCount(PartitionBundle bundle, RegionId regionId,
                                           int expectedCount, String name, Path partitionDir) {
        MemorySegment slice = bundle.regionSegment(regionId);
        int actual = (int) MemoryHeader.readCount(slice, 0);
        if (actual != expectedCount) {
            throw new MigrationException(String.format(
                    "Record count mismatch for %s in %s: expected=%d, actual=%d",
                    name, partitionDir.getFileName(), expectedCount, actual));
        }
    }

    /**
     * Backs up a V3 file by renaming it with a {@code .v3bak} suffix.
     */
    private static void backupV3File(Path file) {
        if (!Files.exists(file)) return;
        Path backup = file.resolveSibling(file.getFileName().toString() + BACKUP_SUFFIX);
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            log.debug("BundleMigration: backed up {} → {}", file.getFileName(), backup.getFileName());
        } catch (IOException e) {
            log.warn("BundleMigration: failed to backup {} — leaving in place: {}",
                    file.getFileName(), e.getMessage());
        }
    }

    /**
     * Discovers partition directories sorted by sequence number (ascending).
     * Extracted from PartitionManager.discoverAllPartitions to avoid circular dependency.
     */
    private static List<Path> PartitionManager_discoverAllPartitions(Path partitionsDir) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)
                        && StorageLayout.isPartitionDir(dir.getFileName().toString())) {
                    dirs.add(dir);
                }
            }
        }
        dirs.sort((a, b) -> {
            int seqA = StorageLayout.parsePartitionSeqNo(a.getFileName().toString());
            int seqB = StorageLayout.parsePartitionSeqNo(b.getFileName().toString());
            return Integer.compare(seqA, seqB);
        });
        return dirs;
    }

    // ══════════════════════════════════════════════════════════════
    // RESULT / EXCEPTION TYPES
    // ══════════════════════════════════════════════════════════════

    /**
     * Result of a V3 → V4 bundle migration operation.
     *
     * @param status             outcome status
     * @param totalPartitions    number of partitions discovered
     * @param migratedPartitions number of partitions successfully migrated
     * @param skippedPartitions  number of partitions skipped (already migrated or empty)
     * @param message            optional diagnostic message
     */
    public record MigrationResult(Status status, int totalPartitions,
                                   int migratedPartitions, int skippedPartitions,
                                   String message) {
        public enum Status { MIGRATED, SKIPPED, ALREADY_MIGRATED }
    }

    /**
     * Exception thrown when migration fails.
     */
    public static class MigrationException extends RuntimeException {
        public MigrationException(String message) { super(message); }
        public MigrationException(String message, Throwable cause) { super(message, cause); }
    }

    // ══════════════════════════════════════════════════════════════
    // CLI ENTRY POINT
    // ══════════════════════════════════════════════════════════════

    /**
     * Standalone entry point for offline V3 → V4 migration.
     *
     * <p>Usage: {@code BundleMigrationCli <namespace-directory> [--dimensions N]}</p>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: BundleMigrationCli <namespace-directory> [--dimensions N]");
            System.err.println();
            System.err.println("  Converts V3 partition stores (.mem files) to V4 partition bundles.");
            System.err.println("  Original files are preserved as .v3bak backups.");
            System.err.println();
            System.err.println("Options:");
            System.err.println("  --dimensions N  Embedding vector dimensions (default: " + DEFAULT_DIMENSIONS + ")");
            System.err.println("  --dry-run       Show what would be migrated without making changes");
            System.exit(1);
        }

        Path namespacePath = Path.of(args[0]);
        int dimensions = DEFAULT_DIMENSIONS;
        boolean dryRun = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--dimensions" -> {
                    if (i + 1 < args.length) {
                        dimensions = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("Error: --dimensions requires a value");
                        System.exit(1);
                    }
                }
                case "--dry-run" -> dryRun = true;
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
                }
            }
        }

        if (!Files.isDirectory(namespacePath)) {
            System.err.println("Error: not a directory: " + namespacePath);
            System.exit(1);
        }

        if (dryRun) {
            dryRunReport(namespacePath, dimensions);
            return;
        }

        try {
            MigrationResult result = migrateAll(namespacePath, dimensions);
            System.out.printf("Migration result: %s (%d/%d partitions migrated, %d skipped)%n",
                    result.status(), result.migratedPartitions(),
                    result.totalPartitions(), result.skippedPartitions());
        } catch (MigrationException e) {
            System.err.println("Migration failed: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            System.exit(2);
        }
    }

    /**
     * Dry-run report: lists partitions and their migration eligibility.
     */
    private static void dryRunReport(Path basePath, int dimensions) {
        Path partitionsDir = StorageLayout.partitionsDir(basePath);
        if (!Files.isDirectory(partitionsDir)) {
            System.out.println("No partitions/ directory found at " + basePath);
            return;
        }

        List<Path> dirs;
        try {
            dirs = PartitionManager_discoverAllPartitions(partitionsDir);
        } catch (IOException e) {
            System.err.println("Failed to discover partitions: " + e.getMessage());
            return;
        }

        System.out.printf("Dry run: %d partition(s) found (dimensions=%d)%n%n", dirs.size(), dimensions);
        System.out.printf("%-25s  %-10s  %-12s  %-12s  %-12s  %-12s%n",
                "Partition", "Status", "Semantic", "Episodic", "Procedural", "Text");
        System.out.println("-".repeat(90));

        for (Path dir : dirs) {
            Path bundleFile = StorageLayout.partitionBundleFile(dir);
            String status;
            if (Files.exists(bundleFile)) {
                status = "MIGRATED";
            } else {
                boolean hasAny = Files.exists(StorageLayout.semanticMem(dir))
                        || Files.exists(StorageLayout.episodicMem(dir))
                        || Files.exists(StorageLayout.proceduralMem(dir))
                        || Files.exists(StorageLayout.textDat(dir));
                status = hasAny ? "PENDING" : "EMPTY";
            }

            System.out.printf("%-25s  %-10s  %-12s  %-12s  %-12s  %-12s%n",
                    dir.getFileName(),
                    status,
                    fileSize(StorageLayout.semanticMem(dir)),
                    fileSize(StorageLayout.episodicMem(dir)),
                    fileSize(StorageLayout.proceduralMem(dir)),
                    fileSize(StorageLayout.textDat(dir)));
        }
    }

    private static String fileSize(Path file) {
        try {
            if (Files.exists(file)) {
                long size = Files.size(file);
                if (size > 1024 * 1024) return String.format("%.1fMB", size / (1024.0 * 1024.0));
                if (size > 1024) return String.format("%.0fKB", size / 1024.0);
                return size + "B";
            }
        } catch (IOException ignored) {}
        return "—";
    }
}
