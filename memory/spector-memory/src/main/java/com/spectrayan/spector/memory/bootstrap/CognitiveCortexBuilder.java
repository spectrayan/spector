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

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.kernel.store.ContinuityMemory;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.store.ProceduralMemory;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.TextBlobMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.kernel.layout.InsularLayout;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.bundle.BundleFileLayoutCalculator;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.migration.MigrationResult;
import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.kernel.layout.EntityDirectoryLayout;
import com.spectrayan.spector.kernel.layout.HebbianLayout;
import com.spectrayan.spector.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.kernel.layout.RegistryLayout;
import com.spectrayan.spector.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.kernel.layout.TemporalLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.persist.PartitionManager;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.config.SpectorPropertyConstants;

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
            TextBlobMemory textStore,
            RuntimeBundle runtimeBundle,
            InsulaMemory insularCortex,
            ContinuityMemory continuityMemory,
            EpisodicMemory episodicStore,
            ProvenanceMemory provenanceMemory
    ) {}

    public static CortexFoundation build(SpectorMemoryBuilder builder) {
        var memProps = builder.properties() != null && builder.properties().memory() != null
                ? builder.properties().memory()
                : new com.spectrayan.spector.config.properties.MemoryProperties();
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
            float[] defaultMins = new float[memProps.getDimensions()];
            float[] defaultMaxs = new float[memProps.getDimensions()];
            java.util.Arrays.fill(defaultMins, -1.0f);
            java.util.Arrays.fill(defaultMaxs, 1.0f);
            quantizer = ScalarQuantizer.fromBounds(memProps.getDimensions(), defaultMins, defaultMaxs);
        }

        // ── Namespace Manager (R12.1: Hoisted/injected process-wide, never constructed per bind) ──
        SpectorNamespaceManager namespaceManager = builder.namespaceManager();

        //  Partition layout 
        int quantizedVecBytes = memProps.getDimensions();

        Path resolvedPartitionDir = null;
        // #443 Phase 2: open ALL partitions on load. The newest is active/writable; every
        // older partition dir is opened read-only (frozen) so recall fan-out + direct-resolve
        // work across partitions after restart.
        List<Path> frozenPartitionDirs = List.of();
        if (isDisk && basePath != null) {
            try {
                createDirectoriesSecure(StoragePaths.runtimeDir(basePath));
                createDirectoriesSecure(StoragePaths.partitionsDir(basePath));
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
                ? StoragePaths.parsePartitionSeqNo(resolvedPartitionDir.getFileName().toString())
                : 0;

        //  Cognitive Memory stores 
        boolean useBundleMode = isDisk && basePath != null;
        CognitiveMemoryRouter cognitiveRouter;
        WorkingMemory workingStore = new WorkingMemory(quantizedVecBytes, memProps.getWorkingCapacity());
        PartitionBundle partitionBundle = null;
        TextBlobMemory textStore = null;
        RuntimeBundle runtimeBundle = null;
        InsulaMemory insularCortex = null;
        ContinuityMemory continuityMemory = null;
        ProvenanceMemory provenanceMemory = null;

        if (isDisk && basePath != null && resolvedPartitionDir != null) {
            // ── V4 Runtime Bundle & Insular Cortex ──
            Path runtimeBundleFile = StoragePaths.runtimeBundleFile(basePath);
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
                // Auto-detect V3 runtime files and attempt auto-migration if CLI is present
                try {
                    Class<?> cliClazz = Class.forName("com.spectrayan.spector.cli.BundleMigrationCli");
                    var method = cliClazz.getMethod("migrateRuntime", Path.class, int.class);
                    Object migrationResult = method.invoke(null, basePath, memProps.getDimensions());
                    if (migrationResult != null && migrationResult.toString().contains("MIGRATED")) {
                        log.info("Successfully auto-migrated V3 runtime files to runtime.bundle");
                        runtimeBundle = RuntimeBundle.Init.open(runtimeBundleFile);
                        isNewRuntime = false;
                    }
                } catch (ClassNotFoundException ignored) {
                    // spector-cli offline tool not present on classpath
                } catch (Exception e) {
                    log.warn("Auto-migration of V3 runtime files encountered an issue: {}", e.getMessage());
                }
            }
            if (isNewRuntime) {
                runtimeBundle = RuntimeBundle.Init.mmap(runtimeBundleFile, specs);
            }

            workingStore = runtimeBundle.openWorking(quantizedVecBytes, memProps.getWorkingCapacity());
            insularCortex = InsulaMemory.fromRegionRef(runtimeBundle.regionRef(RegionId.INSULA), runtimeBundle.isNew());
            continuityMemory = ContinuityMemory.fromRegionRef(runtimeBundle.regionRef(RegionId.CONTINUITY), runtimeBundle.isNew());
            provenanceMemory = runtimeBundle.hasRegion(RegionId.PROVENANCE) ? ProvenanceMemory.fromRegionRef(runtimeBundle.regionRef(RegionId.PROVENANCE), runtimeBundle.bundlePath()) : null;

            // ── V4 Partition Bundle ──
            Path bundleFile = StoragePaths.partitionBundleFile(resolvedPartitionDir);
            if (!Files.exists(bundleFile)) {
                try {
                    Class<?> cliClazz = Class.forName("com.spectrayan.spector.cli.BundleMigrationCli");
                    var method = cliClazz.getMethod("migratePartition", Path.class, int.class);
                    method.invoke(null, resolvedPartitionDir, quantizedVecBytes);
                } catch (ClassNotFoundException ignored) {
                    // spector-cli offline tool not present on classpath
                } catch (Exception e) {
                    log.debug("Partition auto-migration check: {}", e.getMessage());
                }
            }
            boolean isNew = !Files.exists(bundleFile);

            EngramLayout cogLayout = new EngramLayout(quantizedVecBytes);
            TextBlobLayout textLayout = new TextBlobLayout();
            long textSize = memProps.getTextSegmentSize() > 0 ? memProps.getTextSegmentSize() : SpectorPropertyConstants.DEFAULT_MEMORY_TEXT_SEGMENT_SIZE;
            long episodicSize = memProps.getEpisodicSegmentSize() > 0 ? memProps.getEpisodicSegmentSize() :
                    ((long) memProps.getEpisodicPartitionCapacity() * cogLayout.stride());

            try {
                if (isNew) {
                    partitionBundle = PartitionBundle.Init.mmap(
                            bundleFile,
                            memProps.getSemanticCapacity(), episodicSize,
                            memProps.getProceduralCapacity(), textSize,
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

            SemanticMemory semanticStore = partitionBundle.openSemantic(
                    memProps.getSemanticCapacity(), quantizedVecBytes);
            EpisodicMemory episodicStore = partitionBundle.openEpisodic(
                    memProps.getEpisodicPartitionCapacity());
            ProceduralMemory proceduralStore = partitionBundle.openProcedural(
                    memProps.getProceduralCapacity(), quantizedVecBytes);
            textStore = partitionBundle.openText(builder.dataEncryptor());
            StrengthMemory strengthStore = partitionBundle.openStrength(
                    memProps.getSemanticCapacity(), memProps.getEpisodicPartitionCapacity(),
                    memProps.getProceduralCapacity(), "partition-audit");

            cognitiveRouter = new CognitiveMemoryRouter(workingStore, semanticStore, proceduralStore, episodicStore, strengthStore);
            log.info("V4 bundle mode: {} ({}, {} stores, episodic=log-structured)",
                    bundleFile.getFileName(), isNew ? "created" : "opened", 4);

        } else {
            EpisodicMemory episodicStore = EpisodicMemory.heap(
                    memProps.getEpisodicPartitionCapacity(),
                    (long) memProps.getEpisodicPartitionCapacity() * 256L); // ~256B avg per turn
            ProceduralMemory proceduralStore = new ProceduralMemory(
                    quantizedVecBytes, memProps.getProceduralCapacity());
            SemanticMemory semanticStore = new SemanticMemory(
                    quantizedVecBytes, memProps.getSemanticCapacity());

            StrengthMemory strengthStore = StrengthMemory.heap(
                    memProps.getSemanticCapacity(), memProps.getEpisodicPartitionCapacity(), memProps.getProceduralCapacity());

            cognitiveRouter = new CognitiveMemoryRouter(workingStore, semanticStore, proceduralStore, episodicStore, strengthStore);
        }

        if (insularCortex == null) {
            insularCortex = InsulaMemory.heap();
        }

        if (continuityMemory == null) {
            continuityMemory = ContinuityMemory.heap(1000);
        }

        if (provenanceMemory == null) {
            provenanceMemory = ProvenanceMemory.heap(memProps.getProvenanceCapacity());
        }

        EpisodicMemory episodicStore = cognitiveRouter.episodic();

        return new CortexFoundation(
                isDisk, useBundleMode, basePath, quantizer, namespaceManager, quantizedVecBytes,
                resolvedPartitionDir, frozenPartitionDirs, initialPartitionSeq,
                cognitiveRouter, workingStore, partitionBundle, textStore,
                runtimeBundle, insularCortex, continuityMemory, episodicStore, provenanceMemory);
    }

    private static List<RegionSizeSpec> getRuntimeBundleSpecs(SpectorMemoryBuilder builder, int quantizedVecBytes) {
        var memProps = builder.properties() != null && builder.properties().memory() != null
                ? builder.properties().memory()
                : new com.spectrayan.spector.config.properties.MemoryProperties();
        int workingCap = memProps.getWorkingCapacity();
        int pairCap = memProps.getCoactivationPairCapacity();
        int edgeCap = memProps.getCoactivationEdgeCapacity();

        int graphCapacity = memProps.getHebbianGraphCapacity() > 0
                ? memProps.getHebbianGraphCapacity() : memProps.getEpisodicPartitionCapacity();

        int temporalCapacity = memProps.getTemporalChainCapacity() > 0
                ? memProps.getTemporalChainCapacity() : graphCapacity;

        int hyperCap = memProps.getEntityGraphCapacity();
        int hyperEdgeCap = hyperCap * 2;

        long tkgInitialSize = memProps.getTemporalFactsInitialSize();
        int indexMidxCapacity = memProps.getIndexMidxCapacity();
        long indexIdplSize = memProps.getIndexIdplSize();
        int typeRegistryCapacity = memProps.getTypeRegistryCapacity();
        long typeRegistrySize = memProps.getTypeRegistrySize();
        long insulaSize = memProps.getInsulaSize();

        // BM25 region sizing: header(24) + docIds(~48B/doc) + docLengths(4B/doc) + terms+postings(~1400B/doc)
        long bm25InitialSize = Math.max(4L * 1024 * 1024, 24 + 1500L * memProps.getEpisodicPartitionCapacity());

        int hebbianMaxDegree = (memProps.getGraph() != null && memProps.getGraph().getHebbian() != null)
                ? memProps.getGraph().getHebbian().getMaxDegree() : 16;
        if (hebbianMaxDegree <= 0) {
            hebbianMaxDegree = 16;
        }

        return List.of(
                new RegionSizeSpec(
                        RegionId.WORKING,
                        com.spectrayan.spector.kernel.region.RegionPreamble.PREAMBLE_BYTES + (long) new com.spectrayan.spector.kernel.layout.EngramLayout(quantizedVecBytes).recordStride() * workingCap,
                        workingCap,
                        new com.spectrayan.spector.kernel.layout.EngramLayout(quantizedVecBytes).recordStride(),
                        new com.spectrayan.spector.kernel.layout.EngramLayout(quantizedVecBytes).layoutId(),
                        new com.spectrayan.spector.kernel.layout.EngramLayout(quantizedVecBytes).schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.COACTIVATION,
                        64 + 8 + 32L * pairCap + 40L * edgeCap,
                        pairCap,
                        0,
                        new com.spectrayan.spector.kernel.layout.CoActivationLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.CoActivationLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.INDEX_MIDX,
                        64 + (long) indexMidxCapacity * new com.spectrayan.spector.kernel.layout.IndexEntryLayout().recordStride(),
                        indexMidxCapacity,
                        new com.spectrayan.spector.kernel.layout.IndexEntryLayout().recordStride(),
                        new com.spectrayan.spector.kernel.layout.IndexEntryLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.IndexEntryLayout().schemaVersion(),
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
                        64 + 16 + (long) (graphCapacity + 1) * Integer.BYTES + (long) graphCapacity * hebbianMaxDegree * 12L,
                        graphCapacity,
                        0,
                        new com.spectrayan.spector.kernel.layout.HebbianLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.HebbianLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.TEMPORAL_CHAIN,
                        64 + 24L * temporalCapacity,
                        temporalCapacity,
                        24,
                        new com.spectrayan.spector.kernel.layout.TemporalLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.TemporalLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.TEMPORAL_FACTS,
                        64 + tkgInitialSize,
                        1,
                        0,
                        new com.spectrayan.spector.kernel.layout.TemporalFactLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.TemporalFactLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_DIRECTORY,
                        64 + 16 + 64L * hyperCap,
                        hyperCap,
                        64,
                        new com.spectrayan.spector.kernel.layout.EntityDirectoryLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_NAMES,
                        64 + 16 + 8L * hyperCap * 64 + 32L * hyperCap, // adjacency + name index space
                        1,
                        8,
                        new com.spectrayan.spector.kernel.layout.EntityDirectoryLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.EntityDirectoryLayout().schemaVersion(),
                        true  // growable — name index may exceed initial allocation
                ),
                new RegionSizeSpec(
                        RegionId.HYPERGRAPH,
                        64 + 16 + 48L * hyperEdgeCap + 128L * hyperEdgeCap,
                        hyperCap,
                        48,
                        new com.spectrayan.spector.kernel.layout.HyperEntityLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.HyperEntityLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.ENTITY_TYPES,
                        typeRegistrySize,
                        typeRegistryCapacity,
                        0,
                        new com.spectrayan.spector.kernel.layout.RegistryLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.RegistryLayout().schemaVersion(),
                        false
                ),
                new RegionSizeSpec(
                        RegionId.RELATION_TYPES,
                        typeRegistrySize,
                        typeRegistryCapacity,
                        0,
                        new com.spectrayan.spector.kernel.layout.RegistryLayout().layoutId(),
                        new com.spectrayan.spector.kernel.layout.RegistryLayout().schemaVersion(),
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
                ),
                new RegionSizeSpec(
                        RegionId.PROVENANCE,
                        64 + (long) memProps.getProvenanceCapacity() * ProvenanceLayout.RECORD_STRIDE,
                        memProps.getProvenanceCapacity(),
                        ProvenanceLayout.RECORD_STRIDE,
                        ProvenanceLayout.LAYOUT_ID,
                        ProvenanceLayout.SCHEMA_VERSION,
                        false
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
