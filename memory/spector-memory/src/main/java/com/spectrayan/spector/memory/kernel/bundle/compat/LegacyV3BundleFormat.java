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
package com.spectrayan.spector.memory.kernel.bundle.compat;

import com.spectrayan.spector.memory.kernel.storage.StoragePaths;

import java.nio.file.Path;

/**
 * Path and file name resolvers for the legacy V3 storage layout.
 *
 * <p>In V3, runtime structures and partition tiers were stored as standalone flat files.
 * In V4 (ADR-0004), these were consolidated into {@code runtime.bundle} and
 * {@code partition.bundle}. This helper is retained to support {@link BundleMigrationCli}
 * when migrating legacy on-disk formats.</p>
 */
public final class LegacyV3BundleFormat {

    private LegacyV3BundleFormat() {}

    // ── Global V3 File Names (in runtime/) ──
    public static final String FILE_WORKING = "working.mem";
    public static final String FILE_COACTIVATION = "coactivation.tracker";
    public static final String FILE_CHECKPOINT_META = "checkpoint.meta";
    public static final String FILE_INDEX = "index.midx";
    public static final String FILE_HEBBIAN = "hebbian.graph";
    public static final String FILE_TEMPORAL = "temporal.chain";
    public static final String FILE_TEMPORAL_FACTS = "temporal-facts.tfacts";
    public static final String FILE_HYPERGRAPH = "hypergraph.hyeg";
    public static final String FILE_ENTITY_DIRECTORY = "entity-directory.edir";
    public static final String FILE_BM25 = "bm25.bidx";
    public static final String FILE_ENTITY_TYPES = "entity-types.treg";
    public static final String FILE_RELATION_TYPES = "relation-types.treg";

    // ── Partition V3 File Names (in partitions/NNN_EPOCH/) ──
    public static final String FILE_SEMANTIC = "semantic.mem";
    public static final String FILE_EPISODIC = "episodic.mem";
    public static final String FILE_PROCEDURAL = "procedural.mem";
    public static final String FILE_TEXT = "text.dat";

    // ── Global Runtime Path Resolvers ──

    public static Path workingMem(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_WORKING);
    }

    public static Path coactivationTracker(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_COACTIVATION);
    }

    public static Path checkpointMeta(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_CHECKPOINT_META);
    }

    public static Path indexMidxRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_INDEX);
    }

    public static Path hebbianGraphRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_HEBBIAN);
    }

    public static Path temporalChainRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_TEMPORAL);
    }

    public static Path temporalFactsRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_TEMPORAL_FACTS);
    }

    public static Path hyperEntityGraphRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_HYPERGRAPH);
    }

    public static Path entityDirectoryRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_ENTITY_DIRECTORY);
    }

    public static Path entityTypesRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_ENTITY_TYPES);
    }

    public static Path relationTypesRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_RELATION_TYPES);
    }

    public static Path bm25BidxRuntime(Path basePath) {
        return StoragePaths.runtimeDir(basePath).resolve(FILE_BM25);
    }

    // ── Partition File Resolvers ──

    public static Path semanticMem(Path partitionDir) {
        return partitionDir.resolve(FILE_SEMANTIC);
    }

    public static Path episodicMem(Path partitionDir) {
        return partitionDir.resolve(FILE_EPISODIC);
    }

    public static Path proceduralMem(Path partitionDir) {
        return partitionDir.resolve(FILE_PROCEDURAL);
    }

    public static Path textDat(Path partitionDir) {
        return partitionDir.resolve(FILE_TEXT);
    }
}
