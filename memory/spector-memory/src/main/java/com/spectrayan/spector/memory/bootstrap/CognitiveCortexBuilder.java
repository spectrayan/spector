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
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.ContinuityRecordMemory;
import com.spectrayan.spector.memory.cortex.EpisodicLogMemory;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.ProceduralMemory;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.cortex.insula.InsularCortex;
import com.spectrayan.spector.memory.kernel.layout.InsularLayout;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.bundle.BundleLayoutCalculator;
import com.spectrayan.spector.memory.kernel.bundle.BundleMigrationCli;
import com.spectrayan.spector.memory.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import com.spectrayan.spector.memory.kernel.bundle.RegionSizeSpec;
import com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.memory.kernel.codec.MigrationResult;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;
import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.memory.kernel.layout.TemporalLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.persist.PartitionManager;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the storage/cortex foundation for a {@code DefaultSpectorMemory}:
 * persistence-path resolution, the scalar quantizer, the namespace manager,
 * partition-layout discovery (including the #443 open-all-partitions handling),
 * and the four cognitive tier stores wired into a {@link CognitiveMemoryRouter}.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. Behaviour, object identities, and disk/in-memory
 * branches are unchanged.</p>
 *
 * @since 1.1.0
 */
public final class CognitiveCortexBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveCortexBuilder.class);

    private CognitiveCortexBuilder() {}

    /**
     * Immutable holder for the assembled cortex foundation. The fields mirror the
     * local variables that {@code assemble} previously threaded through the rest
     * of the method.
     */
    public record CortexFoundation(
            boolean isDisk,
            boolean useBundleMode,
            Path basePath,
            ScalarQuantizer quantizer,
            SpectorNamespaceManager namespaceManager,
            int quantizedVecBytes,
            Path resolvedPartitionDir,
            List<Path> frozenPartitionDirs,
            int initialPartitionSeq,
            CognitiveMemoryRouter cognitiveRouter,
            WorkingMemory workingStore,
            PartitionBundle partitionBundle,
            TextAppendMemory textStore,
            RuntimeBundle runtimeBundle,
            InsularCortex insularCortex,
            ContinuityRecordMemory continuityMemory,
            EpisodicLogMemory episodicLogStore
    ) {}

    public static CortexFoundation build(SpectorMemoryBuilder builder) {
        boolean isDisk = builder.persistenceMode() == MemoryPersistenceMode.DISK;

        //  Resolve persistence path 
        Path basePath;
        if (isDisk && builder.persistencePath() != null) {
            basePath = builder.persistencePath();
        } else if (isDisk) {
            try {
                basePath = createSecureTempDirectory("spector-memory-");
                log.warn("DISK persistence mode with no explicit path  --  using secure temp directory: {}", basePath);
            } catch (java.io.IOException e) {
                throw new SpectorValidationException(ErrorCode.INTERNAL_ERROR,
                        "Failed to create secure temp directory", e);
            }
        } else {
            basePath = null;
        }

        //  Quantizer 
        ScalarQuantizer quantizer;
        if (builder.quantizer() != null) {
            quantizer = builder.quantizer();
        } else {
            float[] defaultMins = new float[builder.dimensions()];
            float[] defaultMaxs = new float[builder.dimensions()];
            java.util.Arrays.fill(defaultMins, -1.0f);
            java.util.Arrays.fill(defaultMaxs, 1.0f);
            quantizer = ScalarQuantizer.fromBounds(builder.dimensions(), defaultMins, defaultMaxs);
        }

        //  Namespace Manager 
        SpectorNamespaceManager namespaceManager;
        if (isDisk && basePath != null) {
            namespaceManager = new SpectorNamespaceManager(basePath);
            log.info("NamespaceManager initialized: {} namespaces discovered", namespaceManager.count());
        } else {
            namespaceManager = null;
        }

        //  Partition layout 
        int quantizedVecBytes = builder.dimensions();

        Path resolvedPartitionDir = null;
        // #443 Phase 2: open ALL partitions on load. The newest is active/writable; every
        // older partition dir is opened read-only (frozen) so recall fan-out + direct-resolve
        // work across partitions after restart.
        List<Path> frozenPartitionDirs = List.of();
        if (isDisk && basePath != null) {
            try {
                createDirectoriesSecure(StorageLayout.runtimeDir(basePath));
                createDirectoriesSecure(StorageLayout.partitionsDir(basePath));
                List<Path> allPartitions = PartitionManager.discoverAllPartitions(basePath);
                resolvedPartitionDir = allPartitions.get(allPartitions.size() - 1); // newest = active
                if (allPartitions.size() > 1) {
                    frozenPartitionDirs = List.copyOf(allPartitions.subList(0, allPartitions.size() - 1));
                }
                log.info("Active partition: {} ({} frozen partition(s) to open)",
                        resolvedPartitionDir.getFileName(), frozenPartitionDirs.size());
            } catch (java.io.IOException e) {
                log.error("Failed to initialize partition layout: {}", e.getMessage(), e);
            }
        }

        // #443: sequence of the active (newest) partition.
        final int initialPartitionSeq = resolvedPartitionDir != null
                ? StorageLayout.parsePartitionSeqNo(resolvedPartitionDir.getFileName().toString())
                : 0;

        //  Cognitive Memory stores 
        boolean useBundleMode = isDisk && basePath != null;
        CognitiveMemoryRouter cognitiveRouter;
        WorkingMemory workingStore = new WorkingMemory(quantizedVecBytes, builder.workingCapacity());
        PartitionBundle partitionBundle = null;
        TextAppendMemory textStore = null;
        RuntimeBundle runtimeBundle = null;
        InsularCortex insularCortex = null;
        ContinuityRecordMemory continuityMemory = null;

        if (isDisk && basePath != null && resolvedPartitionDir != null) {
            // ── V4 Runtime Bundle & Insular Cortex ──
            Path runtimeBundleFile = StorageLayout.runtimeBundleFile(basePath);
            boolean isNewRuntime = !Files.exists(runtimeBundleFile);
            List<RegionSizeSpec> specs = getRuntimeBundleSpecs(builder, quantizedVecBytes);
            if (!isNewRuntime) {
                try {
                    runtimeBundle = RuntimeBundle.Init.open(runtimeBundleFile);
                    if (runtimeBundle.directory().findRegion(RegionId.WORKING) == null) {
                        log.info("Outdated runtime bundle detected (missing WORKING region), recreating...");
                        runtimeBundle.close();
                        Files.deleteIfExists(runtimeBundleFile);
                        isNewRuntime = true;
                    }
                } catch (Exception e) {
                    log.warn("Failed to open existing runtime bundle: {}, recreating...", e.getMessage());
                    try {
                        Files.deleteIfExists(runtimeBundleFile);
                    } catch (java.io.IOException ioEx) {
                        log.warn("Failed to delete outdated runtime bundle file: {}", ioEx.getMessage());
                    }
                    isNewRuntime = true;
                }
            }
            if (isNewRuntime) {
                // Auto-detect V3 runtime files and attempt auto-migration
                try {
                    com.spectrayan.spector.memory.kernel.bundle.BundleMigrationCli.MigrationResult migrationResult =
                            com.spectrayan.spector.memory.kernel.bundle.BundleMigrationCli.migrateRuntime(basePath, builder.dimensions());
                    if (migrationResult.status() == com.spectrayan.spector.memory.kernel.bundle.BundleMigrationCli.MigrationResult.Status.MIGRATED) {
                        log.info("Successfully auto-migrated V3 runtime files to runtime.bundle");
                        runtimeBundle = RuntimeBundle.Init.open(runtimeBundleFile);
                        isNewRuntime = false;
                    }
                } catch (Exception e) {
                    log.warn("Auto-migration of V3 runtime files encountered an issue: {}", e.getMessage());
                }
            }
            if (isNewRuntime) {
                runtimeBundle = RuntimeBundle.Init.mmap(runtimeBundleFile, specs);
            }

            MemorySegment workingSlice = runtimeBundle.regionSegment(RegionId.WORKING);
            boolean isWorkingNew = !com.spectrayan.spector.memory.kernel.RegionPreamble.isValid(workingSlice, 0L);
            workingStore = WorkingMemory.fromBundle(runtimeBundle.arena(), workingSlice,
                    quantizedVecBytes, builder.workingCapacity(),
                    runtimeBundleFile, isWorkingNew);

            MemorySegment insulaSlice = runtimeBundle.regionSegment(RegionId.INSULA);
            insularCortex = InsularCortex.fromBundle(runtimeBundle.arena(), insulaSlice, isNewRuntime);

            MemorySegment continuitySlice = runtimeBundle.optionalRegionSegment(RegionId.CONTINUITY);
            if (continuitySlice != null) {
                continuityMemory = ContinuityRecordMemory.fromBundle(runtimeBundle.arena(), continuitySlice, isNewRuntime);
            }

            // ── V4 Partition Bundle ──
            Path bundleFile = StorageLayout.partitionBundleFile(resolvedPartitionDir);
            if (!Files.exists(bundleFile)) {
                try {
                    com.spectrayan.spector.memory.kernel.bundle.BundleMigrationCli.migratePartition(resolvedPartitionDir, quantizedVecBytes);
                } catch (Exception e) {
                    log.debug("Partition auto-migration check: {}", e.getMessage());
                }
            }
            boolean isNew = !Files.exists(bundleFile);

            CognitiveRecordLayout cogLayout = new CognitiveRecordLayout(quantizedVecBytes);
            TextBlobLayout textLayout = new TextBlobLayout();
            long textSize = Long.getLong("spector.memory.text-segment-size", 32 * 1024 * 1024L);
            long episodicSize = Long.getLong("spector.memory.episodic-segment-size",
                    (long) builder.episodicPartitionCapacity() * cogLayout.stride());

            try {
                if (isNew) {
                    partitionBundle = PartitionBundle.Init.mmap(
                            bundleFile,
                            builder.semanticCapacity(), episodicSize,
                            builder.proceduralCapacity(), textSize,
                            quantizedVecBytes,
                            cogLayout.layoutId(), cogLayout.schemaVersion(),
                            textLayout.layoutId(), textLayout.schemaVersion());
                } else {
                    partitionBundle = PartitionBundle.Init.open(bundleFile);
                }
            } catch (Exception e) {
                throw new SpectorValidationException(ErrorCode.INTERNAL_ERROR,
                        "Failed to initialize partition bundle: " + bundleFile, e);
            }

            MemorySegment semSlice = partitionBundle.regionSegment(RegionId.SEMANTIC);
            MemorySegment epiSlice = partitionBundle.regionSegment(RegionId.EPISODIC);
            MemorySegment procSlice = partitionBundle.regionSegment(RegionId.PROCEDURAL);
            MemorySegment textSlice = partitionBundle.regionSegment(RegionId.TEXT);

            SemanticMemory semanticStore = SemanticMemory.fromBundle(
                    partitionBundle.arena(), semSlice,
                    builder.semanticCapacity(), quantizedVecBytes, bundleFile, isNew);
            EpisodicRecordMemory episodicStore = EpisodicRecordMemory.fromBundle(
                    partitionBundle.arena(), epiSlice,
                    builder.episodicPartitionCapacity(), quantizedVecBytes, bundleFile, isNew);
            EpisodicLogMemory episodicLogStore = EpisodicLogMemory.fromBundle(
                    partitionBundle.arena(), epiSlice, bundleFile, isNew);
            ProceduralMemory proceduralStore = ProceduralMemory.fromBundle(
                    partitionBundle.arena(), procSlice,
                    builder.proceduralCapacity(), quantizedVecBytes, bundleFile, isNew);
            textStore = TextAppendMemory.fromBundle(
                    partitionBundle.arena(), textSlice, bundleFile, isNew,
                    builder.dataEncryptor());

            StrengthMemory strengthStore = partitionBundle.hasRegion(RegionId.STRENGTH)
                    ? StrengthMemory.fromBundle(partitionBundle.arena(), partitionBundle.regionSegment(RegionId.STRENGTH),
                            builder.semanticCapacity(), builder.episodicPartitionCapacity(), builder.proceduralCapacity(), bundleFile)
                    : null;

            cognitiveRouter = new CognitiveMemoryRouter(workingStore, episodicStore, semanticStore, proceduralStore, episodicLogStore, strengthStore);
            log.info("V4 bundle mode: {} ({}, {} stores, episodic=log-structured)",
                    bundleFile.getFileName(), isNew ? "created" : "opened", 4);

        } else {
            EpisodicRecordMemory episodicStore = new EpisodicRecordMemory(
                    quantizedVecBytes, builder.episodicPartitionCapacity());
            EpisodicLogMemory episodicLogStore = new EpisodicLogMemory(
                    (long) builder.episodicPartitionCapacity() * 256L); // ~256B avg per turn
            ProceduralMemory proceduralStore = new ProceduralMemory(
                    quantizedVecBytes, builder.proceduralCapacity());
            SemanticMemory semanticStore = new SemanticMemory(
                    quantizedVecBytes, builder.semanticCapacity());

            StrengthMemory strengthStore = StrengthMemory.heap(
                    builder.semanticCapacity(), builder.episodicPartitionCapacity(), builder.proceduralCapacity());

            cognitiveRouter = new CognitiveMemoryRouter(workingStore, episodicStore, semanticStore, proceduralStore, episodicLogStore, strengthStore);
        }

        if (insularCortex == null) {
            insularCortex = InsularCortex.heap();
        }

        if (continuityMemory == null) {
            continuityMemory = ContinuityRecordMemory.heap(1000);
        }

        EpisodicLogMemory episodicLogStore = cognitiveRouter.episodicLog();

        return new CortexFoundation(
                isDisk, useBundleMode, basePath, quantizer, namespaceManager, quantizedVecBytes,
                resolvedPartitionDir, frozenPartitionDirs, initialPartitionSeq,
                cognitiveRouter, workingStore, partitionBundle, textStore,
                runtimeBundle, insularCortex, continuityMemory, episodicLogStore);
    }

    private static List<RegionSizeSpec> getRuntimeBundleSpecs(SpectorMemoryBuilder builder, int quantizedVecBytes) {
        int workingCap = builder.workingCapacity();
        int pairCap = builder.coactivationPairCapacity();
        int edgeCap = builder.coactivationEdgeCapacity();

        int graphCapacity = builder.hebbianGraphCapacity() > 0
                ? builder.hebbianGraphCapacity() : builder.episodicPartitionCapacity();

        int temporalCapacity = builder.temporalChainCapacity() > 0
                ? builder.temporalChainCapacity() : graphCapacity;

        int hyperCap = builder.entityGraphCapacity();
        int hyperEdgeCap = hyperCap * 2;

        long tkgInitialSize = builder.temporalFactsInitialSize();
        int indexMidxCapacity = builder.indexMidxCapacity();
        long indexIdplSize = builder.indexIdplSize();
        int typeRegistryCapacity = builder.typeRegistryCapacity();
        long typeRegistrySize = builder.typeRegistrySize();
        long insulaSize = builder.insulaSize();

        // BM25 region sizing: header(24) + docIds(~48B/doc) + docLengths(4B/doc) + terms+postings(~1400B/doc)
        long bm25InitialSize = Math.max(4L * 1024 * 1024, 24 + 1500L * builder.episodicPartitionCapacity());

        return List.of(
                new RegionSizeSpec(
                        RegionId.WORKING,
                        com.spectrayan.spector.memory.kernel.RegionPreamble.PREAMBLE_BYTES + (long) new com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout(quantizedVecBytes).recordStride() * workingCap,
                        workingCap,
                        new com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout(quantizedVecBytes).recordStride(),
                        new com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout(quantizedVecBytes).layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout(quantizedVecBytes).schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.COACTIVATION,
                        64 + 8 + 32L * pairCap + 40L * edgeCap,
                        pairCap,
                        0,
                        new com.spectrayan.spector.memory.kernel.layout.CoActivationLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.CoActivationLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.INDEX_MIDX,
                        64 + (long) indexMidxCapacity * new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().recordStride(),
                        indexMidxCapacity,
                        new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().recordStride(),
                        new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.INDEX_IDPL,
                        indexIdplSize,
                        1,
                        1,
                        0,
                        1,
                        false
                ),
                new RegionSizeSpec(
                        RegionId.HEBBIAN,
                        64 + 16 + (long) (graphCapacity + 1) * Integer.BYTES + (long) graphCapacity * (builder.hebbianMaxDegree() > 0 ? builder.hebbianMaxDegree() : 16) * 12L,
                        graphCapacity,
                        0,
                        new com.spectrayan.spector.memory.kernel.layout.HebbianLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.HebbianLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.TEMPORAL_CHAIN,
                        64 + 24L * temporalCapacity,
                        temporalCapacity,
                        24,
                        new com.spectrayan.spector.memory.kernel.layout.TemporalLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.TemporalLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.TEMPORAL_FACTS,
                        64 + tkgInitialSize,
                        1,
                        0,
                        new com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_DIRECTORY,
                        64 + 16 + 64L * hyperCap,
                        hyperCap,
                        64,
                        new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_NAMES,
                        64 + 16 + 8L * hyperCap * 64 + 32L * hyperCap, // adjacency + name index space
                        1,
                        8,
                        new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                        true  // growable — name index may exceed initial allocation
                ),
                new RegionSizeSpec(
                        RegionId.HYPERGRAPH,
                        64 + 16 + 48L * hyperEdgeCap + 128L * hyperEdgeCap,
                        hyperCap,
                        48,
                        new com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_TYPES,
                        typeRegistrySize,
                        typeRegistryCapacity,
                        0,
                        new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.RELATION_TYPES,
                        typeRegistrySize,
                        typeRegistryCapacity,
                        0,
                        new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().layoutId(),
                        new com.spectrayan.spector.memory.kernel.layout.RegistryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.INSULA,
                        insulaSize,
                        1,
                        0,
                        InsularLayout.LAYOUT_ID,
                        InsularLayout.SCHEMA_VERSION,
                        false
                ),
                new RegionSizeSpec(
                        RegionId.CONTINUITY,
                        ContinuityLayout.DATA_START + (long) 1000 * ContinuityLayout.RECORD_STRIDE,
                        1000,
                        ContinuityLayout.RECORD_STRIDE,
                        ContinuityLayout.LAYOUT_ID,
                        ContinuityLayout.SCHEMA_VERSION,
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
                        bm25InitialSize,
                        1,
                        0,
                        0x42494458,  // "BIDX" magic
                        1,
                        true  // growable — term/posting lists grow dynamically
                )
        );
    }

    private static void createDirectoriesSecure(Path path) throws java.io.IOException {
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            java.nio.file.attribute.FileAttribute<java.util.Set<java.nio.file.attribute.PosixFilePermission>> attrs =
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            java.nio.file.Files.createDirectories(path, attrs);
        } else {
            java.nio.file.Files.createDirectories(path);
        }
    }

    private static Path createSecureTempDirectory(String prefix) throws java.io.IOException {
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            java.nio.file.attribute.FileAttribute<java.util.Set<java.nio.file.attribute.PosixFilePermission>> attrs =
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            return java.nio.file.Files.createTempDirectory(prefix, attrs);
        } else {
            Path tempDir = java.nio.file.Files.createTempDirectory(prefix);
            java.io.File file = tempDir.toFile();
            boolean readable = file.setReadable(true, true);
            boolean writable = file.setWritable(true, true);
            boolean executable = file.setExecutable(true, true);
            if (!readable || !writable || !executable) {
                log.warn("Could not set strict file permissions on temporary directory: {}", tempDir);
            }
            return tempDir;
        }
    }
}
