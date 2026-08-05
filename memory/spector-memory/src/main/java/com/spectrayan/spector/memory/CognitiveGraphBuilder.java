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

import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;
import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the 3-layer cognitive graph: the Hebbian co-activation graph, the
 * temporal chain, the entity extractor + entity graph, the (optional) hyper-entity
 * graph, the temporal knowledge graph, and the {@link CognitiveGraphFacade} over
 * them.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. Load-priority resolution, up-front codec
 * migrations (Hebbian/Temporal) and the in-class-migration path for the entity /
 * hyper-entity graphs are unchanged.</p>
 *
 * <p>Ordering note: the {@code CognitiveGraphFacade} was previously constructed
 * later in {@code assemble} (after the recall pipeline). It is a pure reference
 * holder over the graphs + index — both of which already exist at this point — and
 * the graph references it captures are never reassigned afterwards, so building it
 * here is byte-for-byte equivalent.</p>
 *
 * @since 1.1.0
 */
final class CognitiveGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveGraphBuilder.class);

    private CognitiveGraphBuilder() {}

    /** Immutable holder for the assembled cognitive graphs and their facade. */
    record CognitiveGraphs(
            HebbianGraphBase hebbianGraph,
            TemporalChainMemory temporalChain,
            EntityExtractor entityExtractor,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            CognitiveGraphFacade graphFacade
    ) {}

    static CognitiveGraphs build(SpectorMemoryBuilder builder,
                                 CognitiveCortexBuilder.CortexFoundation cortex,
                                 MemoryIndex index) {
        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  3-Layer Cognitive Graph 
        int graphCapacity = builder.hebbianGraphCapacity > 0
                ? builder.hebbianGraphCapacity : builder.episodicPartitionCapacity;

        HebbianGraphBase hebbianGraph;
        if (isDisk && basePath != null) {
            Path runtimeGraph = StorageLayout.hebbianGraphRuntime(basePath);
            Path legacyGraph = basePath.resolve(StorageLayout.FILE_HEBBIAN);
            Path v2Graph = resolvedPartitionDir != null
                    ? StorageLayout.hebbianGraph(resolvedPartitionDir) : null;
            Path loadFrom = MigrationPathResolver.getNewerPath(runtimeGraph, v2Graph, legacyGraph);
            if (loadFrom == null) {
                loadFrom = legacyGraph;
            }
            // #435: run the codec migration up front (matching the Temporal/HyperEntity
            // pattern). HebbianGraphCodec migrates legacy HGPH and interim HCSR containers
            // to the kernel SMKM CSR format, which HebbianGraphMemory.load() reads natively.
            // This is safe now that load() understands the codec's SMKM output (the exact
            // bug #432 guarded against). load() also self-heals if this is skipped.
            if (loadFrom != null) {
                try {
                    com.spectrayan.spector.memory.kernel.codec.Codecs.ensureCurrent(
                            com.spectrayan.spector.memory.kernel.codec.Codecs.defaultRegistry(),
                            SystemMemoryId.HEBBIAN_CSR.id(),
                            new com.spectrayan.spector.memory.kernel.layout.HebbianLayout(),
                            loadFrom, null, null);
                } catch (Exception e) {
                    log.warn("Operation failed: Codec validation for HebbianLayout", e);
                }
            }
            hebbianGraph = HebbianGraphMemory.load(loadFrom, graphCapacity,
                    builder.hebbianMaxDegree, builder.edgeImportance);
        } else {
            hebbianGraph = new HebbianGraphMemory(graphCapacity);
        }

        int temporalCapacity = builder.temporalChainCapacity > 0
                ? builder.temporalChainCapacity : graphCapacity;
        TemporalChainMemory temporalChain;
        if (isDisk && basePath != null) {
            Path runtimeChain = StorageLayout.temporalChainRuntime(basePath);
            Path legacyChain = basePath.resolve(StorageLayout.FILE_TEMPORAL);
            Path v2Chain = resolvedPartitionDir != null
                    ? StorageLayout.temporalChain(resolvedPartitionDir) : null;
            Path loadFrom = MigrationPathResolver.getNewerPath(runtimeChain, v2Chain, legacyChain);
            if (loadFrom == null) {
                loadFrom = legacyChain;
            }
            try {
                com.spectrayan.spector.memory.kernel.codec.Codecs.ensureCurrent(
                        com.spectrayan.spector.memory.kernel.codec.Codecs.defaultRegistry(),
                        SystemMemoryId.TEMPORAL_CHAIN.id(),
                        new com.spectrayan.spector.memory.kernel.layout.TemporalLayout(),
                        loadFrom, null, null);
            } catch (Exception e) {
                log.warn("Operation failed: Codec validation for TemporalLayout", e);
            }
            temporalChain = new TemporalChainMemory(loadFrom, temporalCapacity);
        } else {
            temporalChain = new TemporalChainMemory(temporalCapacity);
        }

        EntityExtractor entityExtractor;
        if (builder.entityExtractionMode == EntityExtractionMode.LLM
                && builder.LlmProvider != null) {
            entityExtractor = new LlmEntityExtractor(
                    builder.LlmProvider,
                    builder.maxEntitiesPerMemory, builder.maxRelationsPerMemory,
                    builder.llmGenerationOptions);
        } else if (builder.entityExtractionMode == EntityExtractionMode.CUSTOM
                && builder.entityExtractor != null) {
            entityExtractor = builder.entityExtractor;
        } else {
            entityExtractor = NoOpEntityExtractor.INSTANCE;
        }

        boolean entityEnabled = builder.entityExtractionMode != EntityExtractionMode.NONE;
        

        HyperEntityGraphMemory hyperEntityGraph;
        if (entityEnabled) {
            int hyperCap = builder.entityGraphCapacity;
            int hyperEdgeCap = hyperCap * 2;
            if (isDisk && basePath != null) {
                Path runtimeHyper = StorageLayout.hyperEntityGraphRuntime(basePath);
                Path v2Hyper = resolvedPartitionDir != null
                        ? StorageLayout.hyperEntityGraph(resolvedPartitionDir) : null;
                Path loadFrom = MigrationPathResolver.getNewerPath(runtimeHyper, v2Hyper, null);
                if (loadFrom == null) {
                    loadFrom = runtimeHyper;
                }
                // #435: no Codecs.ensureCurrent for HyperEntity — HyperEntityGraphMemory.load()
                // is the single in-class migration authority (SMKM v2 open / legacy HYEG + hybrid
                // migrate / present-but-unreadable throw), mirroring EntityGraphMemory.

                if (java.nio.file.Files.exists(loadFrom)) {
                    hyperEntityGraph = HyperEntityGraphMemory.load(loadFrom, hyperCap, hyperEdgeCap);
                } else {
                    hyperEntityGraph = new HyperEntityGraphMemory(hyperCap, hyperEdgeCap);
                }
            } else {
                hyperEntityGraph = new HyperEntityGraphMemory(hyperCap, hyperEdgeCap);
            }
        } else {
            hyperEntityGraph = null;
        }

        // ── EntityDirectory (ADR-0003 #455): identity companion. P1 = behavior-preserving mirror. ──
        // Shares EntityGraph's entity-type registry instance so entityType(id) resolves identically
        // and no divergent registry is written (the standalone-registry split lands in P2, along with
        // the directory becoming the .treg persistence authority). Load entity-directory.edir if it
        // exists; otherwise derive the directory in-memory from the loaded EntityGraphMemory so no
        // user action is needed while the binary graph is still present.
        EntityDirectory entityDirectory;
        if (entityEnabled) {
            int dirCap = builder.entityGraphCapacity;
            TypeRegistryMemory entityTypeRegistry = TypeRegistryMemory.seeded("entity-type", com.spectrayan.spector.memory.graph.EntityType.SEED);
            if (isDisk && basePath != null) {
                Path edir = StorageLayout.entityDirectoryRuntime(basePath);
                if (java.nio.file.Files.exists(edir)) {
                    entityDirectory = EntityDirectory.load(edir, dirCap, entityTypeRegistry,
                            builder.dataEncryptor);
                } else {
                    entityDirectory = new EntityDirectory(edir, dirCap, entityTypeRegistry);
                    entityDirectory.setDataEncryptor(builder.dataEncryptor);
                }
            } else {
                entityDirectory = new EntityDirectory(dirCap, entityTypeRegistry);
            }
        } else {
            entityDirectory = null;
        }


        TemporalKnowledgeGraph temporalKnowledgeGraph;
        TypeRegistryMemory predRegistry = new TypeRegistryMemory("relation-type");
        if (isDisk && basePath != null) {
            Path runtimeTkg = StorageLayout.temporalFactsRuntime(basePath);
            long initialSize = 16L * 1024 * 1024; // 16MB
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(runtimeTkg, initialSize, predRegistry);
        } else {
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(predRegistry);
        }

        //  Cognitive Graph Facade 
        CognitiveGraphFacade graphFacade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, index);

        return new CognitiveGraphs(
                hebbianGraph, temporalChain, entityExtractor, entityDirectory,
                hyperEntityGraph, temporalKnowledgeGraph, graphFacade);
    }
}
