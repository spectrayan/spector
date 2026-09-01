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
package com.spectrayan.spector.memory.assembly;

import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;
import com.spectrayan.spector.memory.graph.OntologyConfig;
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
public final class CognitiveGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(CognitiveGraphBuilder.class);

    private CognitiveGraphBuilder() {}

    /** Immutable holder for the assembled cognitive graphs and their facade. */
    public record CognitiveGraphs(
            HebbianGraphBase hebbianGraph,
            TemporalChainMemory temporalChain,
            EntityExtractor entityExtractor,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            CognitiveGraphFacade graphFacade
    ) {}

    public static CognitiveGraphs build(SpectorMemoryBuilder builder,
                                 CognitiveCortexBuilder.CortexFoundation cortex,
                                 MemoryIndex index) {
        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  3-Layer Cognitive Graph 
        int graphCapacity = builder.hebbianGraphCapacity > 0
                ? builder.hebbianGraphCapacity : builder.episodicPartitionCapacity;

        HebbianGraphBase hebbianGraph;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.HEBBIAN);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
            int edgeCapacity = builder.hebbianMaxDegree > 0
                    ? graphCapacity * builder.hebbianMaxDegree
                    : graphCapacity * 16;
            hebbianGraph = HebbianGraphMemory.fromBundle(
                    cortex.runtimeBundle().arena(), regionSlice, graphCapacity, edgeCapacity,
                    builder.hebbianMaxDegree, builder.edgeImportance,
                    cortex.runtimeBundle().bundlePath(), isNew);
        } else {
            hebbianGraph = new HebbianGraphMemory(graphCapacity);
        }

        int temporalCapacity = builder.temporalChainCapacity > 0
                ? builder.temporalChainCapacity : graphCapacity;
        TemporalChainMemory temporalChain;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.TEMPORAL_CHAIN);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
            temporalChain = TemporalChainMemory.fromBundle(
                    cortex.runtimeBundle().arena(), regionSlice, temporalCapacity,
                    cortex.runtimeBundle().bundlePath(), isNew);
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
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.HYPERGRAPH);
                boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
                hyperEntityGraph = HyperEntityGraphMemory.fromBundle(
                        cortex.runtimeBundle().arena(), regionSlice, hyperCap, hyperEdgeCap,
                        cortex.runtimeBundle().bundlePath(), isNew);
            } else {
                hyperEntityGraph = new HyperEntityGraphMemory(hyperCap, hyperEdgeCap);
            }
        } else {
            hyperEntityGraph = null;
        }

        OntologyConfig ontConfig = builder.ontologyConfig != null
                ? builder.ontologyConfig
                : OntologyConfig.defaultInstance();
        String[] entitySeedTypes = ontConfig.canonicalTypes().toArray(String[]::new);

        EntityDirectory entityDirectory;
        if (entityEnabled) {
            int dirCap = builder.entityGraphCapacity;
            TypeRegistryMemory entityTypeRegistry;
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.ENTITY_TYPES);
                boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
                entityTypeRegistry = TypeRegistryMemory.fromBundle(
                        SystemMemoryId.ENTITY_TYPE, cortex.runtimeBundle().arena(), regionSlice,
                        cortex.runtimeBundle().bundlePath(), isNew,
                        entitySeedTypes);
            } else {
                entityTypeRegistry = TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, entitySeedTypes);
            }

            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                java.lang.foreign.MemorySegment entitySlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.ENTITY_DIRECTORY);
                java.lang.foreign.MemorySegment adjSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.ENTITY_NAMES);
                boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(entitySlice, 0L);
                entityDirectory = EntityDirectory.fromBundle(
                        cortex.runtimeBundle().arena(), entitySlice, adjSlice,
                        dirCap, entityTypeRegistry,
                        cortex.runtimeBundle().bundlePath(), isNew);
            } else {
                entityDirectory = new EntityDirectory(dirCap, entityTypeRegistry);
            }
        } else {
            entityDirectory = null;
        }

        TemporalKnowledgeGraph temporalKnowledgeGraph;
        TypeRegistryMemory predRegistry;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.RELATION_TYPES);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
            predRegistry = TypeRegistryMemory.fromBundle(
                    SystemMemoryId.RELATION_TYPE, cortex.runtimeBundle().arena(), regionSlice,
                    cortex.runtimeBundle().bundlePath(), isNew);
        } else {
            predRegistry = new TypeRegistryMemory(SystemMemoryId.RELATION_TYPE);
        }

        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.TEMPORAL_FACTS);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
            temporalKnowledgeGraph = TemporalKnowledgeGraph.fromBundle(
                    predRegistry, cortex.runtimeBundle().arena(), regionSlice,
                    cortex.runtimeBundle().bundlePath(), isNew);
        } else {
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(predRegistry);
        }

        //  Cognitive Graph Facade 
        CognitiveGraphFacade graphFacade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph,
                temporalKnowledgeGraph, ontConfig, index, builder.cacheManager);

        return new CognitiveGraphs(
                hebbianGraph, temporalChain, entityExtractor, entityDirectory,
                hyperEntityGraph, temporalKnowledgeGraph, graphFacade);
    }
}
