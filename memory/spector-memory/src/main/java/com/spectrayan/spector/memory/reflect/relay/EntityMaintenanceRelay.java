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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Knowledge Graph Homeostasis & Maintenance Relay.
 *
 * <p>Maintains the entity directory and hypergraph through entity resolution, LTD
 * adjacency link decay, adjacency defragmentation, and hyperedge weight decay.</p>
 */
public final class EntityMaintenanceRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(EntityMaintenanceRelay.class);

    private static final float ENTITY_DECAY_FACTOR = SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_DECAY_FACTOR;
    private static final float ENTITY_PRUNE_THRESHOLD = SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_PRUNE_THRESHOLD;
    private static final float ENTITY_ADJ_DECAY_FACTOR = SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_ADJ_DECAY_FACTOR;
    private static final float ENTITY_ADJ_PRUNE_THRESHOLD = SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_ADJ_PRUNE_THRESHOLD;
    private static final int ENTITY_MERGE_DISTANCE = SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_MERGE_DISTANCE;

    @Override
    public boolean transmit(final ReflectSignal signal) {
        final EntityDirectory entityDirectory = signal.entityDirectory();
        final HyperEntityGraphMemory hyperEntityGraph = signal.hyperEntityGraph();

        if (entityDirectory != null && entityDirectory.entityCount() > 0) {
            try {
                // 1. Entity resolution / fuzzy merge
                int merged;
                if (signal.entityResolutionEnabled() && signal.embeddingProvider() != null && signal.textGenerator() != null) {
                    merged = entityDirectory.mergeSimilarEntities(
                            signal.embeddingProvider(), signal.textGenerator(),
                            signal.entityCosineThreshold(), signal.entityShadowMode(), signal.typeNormalizer());
                } else {
                    merged = entityDirectory.mergeSimilarEntities(ENTITY_MERGE_DISTANCE, signal.typeNormalizer());
                }
                if (merged > 0) {
                    log.debug("Entity Maintenance: merged {} similar entities", merged);
                }

                // 2. Entity->Memory Adjacency LTD Decay
                int pruned = entityDirectory.decayAdjacencyWeights(ENTITY_ADJ_DECAY_FACTOR, ENTITY_ADJ_PRUNE_THRESHOLD);
                if (pruned > 0) {
                    log.debug("Entity Maintenance: LTD pruned {} weak adjacency links", pruned);
                }

                // 3. Adjacency list defragmentation
                long reclaimed = entityDirectory.compactAdjacency();
                if (reclaimed > 0) {
                    log.debug("Entity Maintenance: defragmented adjacency lists, reclaimed {}B", reclaimed);
                }
            } catch (Exception e) {
                log.warn("Entity directory maintenance failed: {}", e.getMessage(), e);
            }
        }

        if (hyperEntityGraph != null) {
            try {
                // 4. Hyperedge decay
                int evicted = hyperEntityGraph.decayHyperedges(ENTITY_DECAY_FACTOR, ENTITY_PRUNE_THRESHOLD);
                if (evicted > 0) {
                    log.debug("Entity Maintenance: decayed and pruned {} weak hyperedges", evicted);
                }
            } catch (Exception e) {
                log.warn("HyperEntityGraph decay failed: {}", e.getMessage(), e);
            }
        }

        return true;
    }
}
