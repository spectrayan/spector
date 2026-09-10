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
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;

import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        var memProps = builder.properties() != null && builder.properties().memory() != null
                ? builder.properties().memory()
                : new com.spectrayan.spector.config.properties.MemoryProperties();
        var graphProps = memProps.getGraph() != null
                ? memProps.getGraph()
                : new com.spectrayan.spector.config.properties.GraphProperties();
        var hebbianProps = graphProps.getHebbian() != null
                ? graphProps.getHebbian()
                : new com.spectrayan.spector.config.properties.GraphProperties.HebbianProperties();
        var entityProps = graphProps.getEntity() != null
                ? graphProps.getEntity()
                : new com.spectrayan.spector.config.properties.GraphProperties.EntityGraphProperties();

        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  3-Layer Cognitive Graph 
        int graphCapacity = memProps.getHebbianGraphCapacity() > 0
                ? memProps.getHebbianGraphCapacity() : memProps.getEpisodicPartitionCapacity();

        int hebbianMaxDegree = hebbianProps.getMaxDegree() > 0 ? hebbianProps.getMaxDegree() : 16;

        HebbianGraphBase hebbianGraph;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            int edgeCapacity = graphCapacity * hebbianMaxDegree;
            hebbianGraph = cortex.runtimeBundle().openHebbian(
                    graphCapacity, edgeCapacity, hebbianMaxDegree, builder.edgeImportance());
        } else {
            hebbianGraph = new HebbianGraphMemory(graphCapacity);
        }

        int temporalCapacity = memProps.getTemporalChainCapacity() > 0
                ? memProps.getTemporalChainCapacity() : graphCapacity;
        TemporalChainMemory temporalChain;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            temporalChain = com.spectrayan.spector.kernel.store.TemporalChainMemory.fromRegionRef(cortex.runtimeBundle().regionRef(RegionId.TEMPORAL_CHAIN), temporalCapacity, cortex.runtimeBundle().bundlePath(), cortex.runtimeBundle().isNew());
        } else {
            temporalChain = new TemporalChainMemory(temporalCapacity);
        }

        EntityExtractionMode extractionMode = EntityExtractionMode.NONE;
        if (builder.entityExtractor() != null) {
            extractionMode = EntityExtractionMode.CUSTOM;
        } else if (entityProps.getExtractionMode() != null && !entityProps.getExtractionMode().isBlank()) {
            try {
                extractionMode = EntityExtractionMode.valueOf(entityProps.getExtractionMode().toUpperCase(java.util.Locale.ROOT));
            } catch (Exception ignored) {}
        }

        EntityExtractor entityExtractor;
        if (extractionMode == EntityExtractionMode.LLM
                && builder.llmProvider() != null) {
            entityExtractor = new LlmEntityExtractor(
                    builder.llmProvider(),
                    entityProps.getMaxPerMemory(), entityProps.getMaxRelationsPerMemory(),
                    builder.llmGenerationOptions());
        } else if (extractionMode == EntityExtractionMode.CUSTOM
                && builder.entityExtractor() != null) {
            entityExtractor = builder.entityExtractor();
        } else {
            entityExtractor = NoOpEntityExtractor.INSTANCE;
        }

        boolean entityEnabled = extractionMode != EntityExtractionMode.NONE;

        HyperEntityGraphMemory hyperEntityGraph;
        if (entityEnabled) {
            int hyperCap = memProps.getEntityGraphCapacity();
            int hyperEdgeCap = hyperCap * 2;
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                hyperEntityGraph = com.spectrayan.spector.kernel.store.HyperEntityGraphMemory.fromRegionRef(cortex.runtimeBundle().regionRef(RegionId.HYPERGRAPH), hyperCap, hyperEdgeCap, cortex.runtimeBundle().bundlePath(), cortex.runtimeBundle().isNew());
            } else {
                hyperEntityGraph = new HyperEntityGraphMemory(hyperCap, hyperEdgeCap);
            }
        } else {
            hyperEntityGraph = null;
        }

        OntologyConfig ontConfig = builder.ontologyConfig() != null
                ? builder.ontologyConfig()
                : OntologyConfig.defaultInstance();
        String[] entitySeedTypes = ontConfig.canonicalTypes().toArray(String[]::new);

        EntityDirectory entityDirectory;
        if (entityEnabled) {
            int dirCap = memProps.getEntityGraphCapacity();
            TypeRegistryMemory entityTypeRegistry;
            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                entityTypeRegistry = cortex.runtimeBundle().openRegistry(
                        com.spectrayan.spector.kernel.region.RegionId.ENTITY_TYPES,
                        SystemMemoryId.ENTITY_TYPE, entitySeedTypes);
            } else {
                entityTypeRegistry = TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, entitySeedTypes);
            }

            if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
                entityDirectory = com.spectrayan.spector.memory.graph.EntityDirectory.fromRegionRefs(cortex.runtimeBundle().regionRef(RegionId.ENTITY_DIRECTORY), cortex.runtimeBundle().regionRef(RegionId.ENTITY_NAMES), dirCap, entityTypeRegistry, cortex.runtimeBundle().bundlePath(), cortex.runtimeBundle().isNew());
            } else {
                entityDirectory = new EntityDirectory(dirCap, entityTypeRegistry);
            }
        } else {
            entityDirectory = null;
        }

        TemporalKnowledgeGraph temporalKnowledgeGraph;
        TypeRegistryMemory predRegistry;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            predRegistry = cortex.runtimeBundle().openRegistry(
                    com.spectrayan.spector.kernel.region.RegionId.RELATION_TYPES,
                    SystemMemoryId.RELATION_TYPE, null);
        } else {
            predRegistry = new TypeRegistryMemory(SystemMemoryId.RELATION_TYPE);
        }

        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            temporalKnowledgeGraph = com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph.fromRegionRef(predRegistry, cortex.runtimeBundle().regionRef(RegionId.TEMPORAL_FACTS), cortex.runtimeBundle().bundlePath(), cortex.runtimeBundle().isNew());
        } else {
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(predRegistry);
        }

        // Auto-heal temporal chain if historical memories exist but chain is unlinked
        if (temporalChain != null && temporalChain.chainLength() == 0 && index != null && index.size() > 1) {
            try {
                backfillTemporalChain(temporalChain, index);
            } catch (Exception e) {
                log.warn("[CognitiveGraphBuilder] Failed to backfill temporal causal chain: {}", e.getMessage(), e);
            }
        }

        // ── Cognitive Graph Facade ──
        CognitiveGraphFacade graphFacade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph,
                temporalKnowledgeGraph, ontConfig, index, builder.cacheManager());

        return new CognitiveGraphs(
                hebbianGraph, temporalChain, entityExtractor, entityDirectory,
                hyperEntityGraph, temporalKnowledgeGraph, graphFacade);
    }

    private static void backfillTemporalChain(TemporalChainMemory temporalChain, MemoryIndex index) {
        List<String> orderedIds = index.orderedIds();
        if (orderedIds.size() <= 1) return;

        Map<String, Integer> idToSlot = new LinkedHashMap<>();
        Map<Integer, String> slotToId = new LinkedHashMap<>();
        index.buildGraphSlotMappings(slotToId, idToSlot);

        int prevSlot = -1;
        int linkedCount = 0;
        int nowSec = (int) (System.currentTimeMillis() / 1000);

        for (String id : orderedIds) {
            int slot = idToSlot.getOrDefault(id, -1);
            if (slot < 0 || slot >= temporalChain.capacity()) continue;

            if (prevSlot >= 0 && prevSlot < temporalChain.capacity()) {
                temporalChain.linkNodes(prevSlot, slot, 0, nowSec);
                linkedCount++;
            }
            prevSlot = slot;
        }

        if (linkedCount > 0) {
            temporalChain.flush();
            log.info("[CognitiveGraphBuilder] Backfilled {} temporal chain links across {} indexed memories",
                    linkedCount, orderedIds.size());
        }
    }
}
