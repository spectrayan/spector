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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.ProceduralRecordMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;

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
final class CognitiveCortexBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveCortexBuilder.class);

    private CognitiveCortexBuilder() {}

    /**
     * Immutable holder for the assembled cortex foundation. The fields mirror the
     * local variables that {@code assemble} previously threaded through the rest
     * of the method.
     */
    record CortexFoundation(
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
            WorkingRecordMemory workingStore,
            PartitionBundle partitionBundle,
            TextAppendMemory textStore
    ) {}

    static CortexFoundation build(SpectorMemoryBuilder builder) {
        boolean isDisk = builder.persistenceMode == MemoryPersistenceMode.DISK;

        //  Resolve persistence path 
        Path basePath;
        if (isDisk && builder.persistencePath != null) {
            basePath = builder.persistencePath;
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
        if (builder.quantizer != null) {
            quantizer = builder.quantizer;
        } else {
            float[] defaultMins = new float[builder.dimensions];
            float[] defaultMaxs = new float[builder.dimensions];
            java.util.Arrays.fill(defaultMins, -1.0f);
            java.util.Arrays.fill(defaultMaxs, 1.0f);
            quantizer = ScalarQuantizer.fromBounds(builder.dimensions, defaultMins, defaultMaxs);
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
        int quantizedVecBytes = builder.dimensions;

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
        CognitiveMemoryRouter cognitiveRouter;
        WorkingRecordMemory workingStore;
        PartitionBundle partitionBundle = null;
        TextAppendMemory textStore = null;
        if (isDisk && builder.persistWorkingMemory && basePath != null) {
            workingStore = new WorkingRecordMemory(quantizedVecBytes, builder.workingCapacity,
                     StorageLayout.workingMem(basePath));
        } else {
            workingStore = new WorkingRecordMemory(quantizedVecBytes, builder.workingCapacity);
        }

        boolean activeHasBundle = resolvedPartitionDir != null && Files.exists(StorageLayout.partitionBundleFile(resolvedPartitionDir));
        boolean useBundleMode = builder.useBundleMode || activeHasBundle;

        if (isDisk && basePath != null && resolvedPartitionDir != null && useBundleMode) {
            // ── V4 Bundle Mode ──
            Path bundleFile = StorageLayout.partitionBundleFile(resolvedPartitionDir);
            boolean isNew = !Files.exists(bundleFile);

            CognitiveRecordLayout cogLayout = new CognitiveRecordLayout(quantizedVecBytes);
            TextBlobLayout textLayout = new TextBlobLayout();
            long textSize = Long.getLong("spector.memory.text-segment-size", 32 * 1024 * 1024L);

            if (isNew) {
                partitionBundle = PartitionBundle.Init.mmap(
                        bundleFile,
                        builder.semanticCapacity, builder.episodicPartitionCapacity,
                        builder.proceduralCapacity, textSize,
                        quantizedVecBytes,
                        cogLayout.layoutId(), cogLayout.schemaVersion(),
                        textLayout.layoutId(), textLayout.schemaVersion());
            } else {
                partitionBundle = PartitionBundle.Init.open(bundleFile);
            }

            MemorySegment semSlice = partitionBundle.regionSegment(RegionId.SEMANTIC);
            MemorySegment epiSlice = partitionBundle.regionSegment(RegionId.EPISODIC);
            MemorySegment procSlice = partitionBundle.regionSegment(RegionId.PROCEDURAL);
            MemorySegment textSlice = partitionBundle.regionSegment(RegionId.TEXT);

            SemanticRecordMemory semanticStore = SemanticRecordMemory.fromBundle(
                    partitionBundle.arena(), semSlice,
                    builder.semanticCapacity, quantizedVecBytes, bundleFile, isNew);
            EpisodicRecordMemory episodicStore = EpisodicRecordMemory.fromBundle(
                    partitionBundle.arena(), epiSlice,
                    builder.episodicPartitionCapacity, quantizedVecBytes, bundleFile, isNew);
            ProceduralRecordMemory proceduralStore = ProceduralRecordMemory.fromBundle(
                    partitionBundle.arena(), procSlice,
                    builder.proceduralCapacity, quantizedVecBytes, bundleFile, isNew);
            textStore = TextAppendMemory.fromBundle(
                    partitionBundle.arena(), textSlice, bundleFile, isNew,
                    builder.dataEncryptor);

            cognitiveRouter = new CognitiveMemoryRouter(workingStore, episodicStore, semanticStore, proceduralStore);
            log.info("V4 bundle mode: {} ({}, {} stores)",
                    bundleFile.getFileName(), isNew ? "created" : "opened", 4);

        } else if (isDisk && basePath != null && resolvedPartitionDir != null) {
            // ── V3 Legacy Mode ──
            EpisodicRecordMemory episodicStore = new EpisodicRecordMemory(
                    StorageLayout.episodicMem(resolvedPartitionDir),
                    quantizedVecBytes, builder.episodicPartitionCapacity);
            ProceduralRecordMemory proceduralStore = new ProceduralRecordMemory(
                    quantizedVecBytes, builder.proceduralCapacity,
                    StorageLayout.proceduralMem(resolvedPartitionDir));
            SemanticRecordMemory semanticStore = new SemanticRecordMemory(
                    quantizedVecBytes, builder.semanticCapacity,
                    StorageLayout.semanticMem(resolvedPartitionDir));
            textStore = new TextAppendMemory(
                    StorageLayout.textDat(resolvedPartitionDir), builder.dataEncryptor);
            cognitiveRouter = new CognitiveMemoryRouter(workingStore, episodicStore, semanticStore, proceduralStore);
        } else {
            EpisodicRecordMemory episodicStore = new EpisodicRecordMemory(
                    quantizedVecBytes, builder.episodicPartitionCapacity);
            ProceduralRecordMemory proceduralStore = new ProceduralRecordMemory(
                    quantizedVecBytes, builder.proceduralCapacity);
            SemanticRecordMemory semanticStore = new SemanticRecordMemory(
                    quantizedVecBytes, builder.semanticCapacity);
            cognitiveRouter = new CognitiveMemoryRouter(workingStore, episodicStore, semanticStore, proceduralStore);
        }

        return new CortexFoundation(
                isDisk, useBundleMode, basePath, quantizer, namespaceManager, quantizedVecBytes,
                resolvedPartitionDir, frozenPartitionDirs, initialPartitionSeq,
                cognitiveRouter, workingStore, partitionBundle, textStore);
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
