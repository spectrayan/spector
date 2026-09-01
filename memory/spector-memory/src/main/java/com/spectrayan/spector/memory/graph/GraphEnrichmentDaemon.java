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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supervised background daemon for asynchronous graph enrichment and entity extraction.
 *
 * <h3>Motivation</h3>
 * <p>Extracting structured entities and causal/topological relationships via LLMs
 * takes 300ms–2000ms per memory. Running this synchronously during high-throughput
 * ingestion causes queue saturation and connection timeouts. {@code GraphEnrichmentDaemon}
 * acts as an offline, background worker (analogous to {@code CheckpointDaemon} and
 * {@code ReflectDaemon}) that continuously inspects unenriched memories and populates
 * the entity directory, hypergraph, and temporal knowledge graph asynchronously.</p>
 *
 * @since 1.1.0
 */
public final class GraphEnrichmentDaemon {

    private static final Logger log = LoggerFactory.getLogger(GraphEnrichmentDaemon.class);

    private final MemoryIndex index;
    private final EntityExtractor entityExtractor;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;

    private volatile CognitiveGraphFacade graphFacade;

    public void setGraphFacade(CognitiveGraphFacade graphFacade) {
        this.graphFacade = graphFacade;
    }

    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final AtomicInteger totalEntitiesAdded = new AtomicInteger(0);
    private final AtomicInteger totalRelationsAdded = new AtomicInteger(0);
    private final AtomicLong lastRunDurationMs = new AtomicLong(0);
    private volatile String lastError = null;

    /**
     * Statistics snapshot for graph enrichment operations.
     */
    public record EnrichmentStats(
            int totalMemories,
            int enrichedMemories,
            int pendingMemories,
            int totalEntitiesAdded,
            int totalRelationsAdded,
            boolean inProgress,
            long lastRunDurationMs,
            String lastError
    ) {}

    public GraphEnrichmentDaemon(
            MemoryIndex index,
            EntityExtractor entityExtractor,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph) {
        this.index = index;
        this.entityExtractor = entityExtractor;
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
    }

    /**
     * Returns current enrichment telemetry and statistics.
     */
    public EnrichmentStats stats() {
        int total = (index != null) ? index.size() : 0;
        int enriched = 0;
        if (index != null && entityDirectory != null) {
            for (String id : index.allIds()) {
                var loc = index.locate(id);
                if (loc != null) {
                    int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                    if (entityDirectory.hasMemoryRefOptimistic(slot)) {
                        enriched++;
                    }
                }
            }
        }
        int pending = Math.max(0, total - enriched);
        return new EnrichmentStats(
                total, enriched, pending,
                totalEntitiesAdded.get(),
                totalRelationsAdded.get(),
                inProgress.get(),
                lastRunDurationMs.get(),
                lastError
        );
    }

    /**
     * Scheduled callback invoked by {@code DaemonSupervisor}.
     * Enriches up to 25 unenriched memories per cycle.
     */
    public void enrichPending() {
        enrichBatch(25);
    }

    /**
     * Enriches a batch of unenriched memories up to the given limit.
     *
     * @param limit maximum memories to process in this batch
     * @return number of memories successfully enriched
     */
    public int enrichBatch(int limit) {
        if (index == null || entityExtractor == null || entityDirectory == null || !entityExtractor.isAvailable()) {
            return 0;
        }

        if (!inProgress.compareAndSet(false, true)) {
            log.debug("[GraphEnricher] Enrichment already in progress, skipping batch trigger");
            return 0;
        }

        long startNs = System.nanoTime();
        int enrichedCount = 0;
        lastError = null;

        try {
            int effectiveLimit = limit > 0 ? limit : Integer.MAX_VALUE;
            List<String> candidateIds = new ArrayList<>();

            for (String id : index.allIds()) {
                var loc = index.locate(id);
                if (loc == null) continue;
                int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                if (!entityDirectory.hasMemoryRefOptimistic(slot)) {
                    candidateIds.add(id);
                    if (candidateIds.size() >= effectiveLimit) {
                        break;
                    }
                }
            }

            if (candidateIds.isEmpty()) {
                log.debug("[GraphEnricher] All memories are currently enriched");
                return 0;
            }

            log.info("[GraphEnricher] Starting enrichment batch of {} memories (total candidates: {})",
                    candidateIds.size(), candidateIds.size());

            for (String id : candidateIds) {
                var loc = index.locate(id);
                if (loc == null) continue;
                String text = index.text(id);
                if (text == null || text.isBlank()) continue;

                int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);

                try {
                    List<ExtractedEntity> entities = entityExtractor.extract(id, text);
                    if (entities != null && !entities.isEmpty()) {
                        populateEntities(entities, slot, id);
                        syncTemporalFacts(entities, slot, id, System.currentTimeMillis() / 1000L);
                        enrichedCount++;
                    }
                } catch (Exception e) {
                    log.warn("[GraphEnricher] Failed to extract entities for '{}': {}", id, e.getMessage());
                    lastError = e.getMessage();
                }
            }

        } finally {
            lastRunDurationMs.set((System.nanoTime() - startNs) / 1_000_000L);
            inProgress.set(false);
        }

        if (enrichedCount > 0 && graphFacade != null) {
            graphFacade.invalidateCache();
        }

        log.info("[GraphEnricher] Completed enrichment batch: {} memories enriched in {}ms",
                enrichedCount, lastRunDurationMs.get());
        return enrichedCount;
    }

    /**
     * Enriches all unenriched memories across the entire index in batches until complete.
     *
     * @return total memories enriched
     */
    public int enrichAll() {
        int totalEnriched = 0;
        int batch;
        while ((batch = enrichBatch(50)) > 0) {
            totalEnriched += batch;
        }
        return totalEnriched;
    }

    private final AtomicBoolean reextractInProgress = new AtomicBoolean(false);
    private final AtomicInteger totalReextracted = new AtomicInteger(0);

    /**
     * Re-extracts entities and relationships for a batch of memories, overwriting
     * existing graph data.
     *
     * @param limit maximum memories to process in this batch
     * @return number of memories successfully re-extracted
     */
    public int reextractBatch(int limit) {
        if (index == null || entityExtractor == null || entityDirectory == null || !entityExtractor.isAvailable()) {
            return 0;
        }

        if (!reextractInProgress.compareAndSet(false, true)) {
            log.debug("[GraphEnricher] Re-extraction already in progress, skipping batch trigger");
            return 0;
        }

        long startNs = System.nanoTime();
        int reextractedCount = 0;
        lastError = null;

        try {
            int effectiveLimit = limit > 0 ? limit : Integer.MAX_VALUE;
            List<String> candidateIds = new ArrayList<>();

            for (String id : index.allIds()) {
                var loc = index.locate(id);
                if (loc != null) {
                    candidateIds.add(id);
                    if (candidateIds.size() >= effectiveLimit) {
                        break;
                    }
                }
            }

            if (candidateIds.isEmpty()) {
                log.debug("[GraphEnricher] No memories found for re-extraction");
                return 0;
            }

            log.info("[GraphEnricher] Starting re-extraction batch of {} memories", candidateIds.size());

            for (int i = 0; i < candidateIds.size(); i++) {
                String id = candidateIds.get(i);
                if (i > 0 && i % 10 == 0) {
                    log.info("[GraphEnricher] Re-extraction progress: {}/{}", i, candidateIds.size());
                }
                var loc = index.locate(id);
                if (loc == null) continue;
                String text = index.text(id);
                if (text == null || text.isBlank()) continue;

                int slot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);

                try {
                    // 1. Unlink existing entity references for this memory
                    entityDirectory.unlinkMemory(slot);

                    // 2. TKG cleanup if possible
                    if (temporalKnowledgeGraph != null) {
                        try {
                            var retractMethod = temporalKnowledgeGraph.getClass().getMethod("retractFactsForMemory", int.class);
                            retractMethod.invoke(temporalKnowledgeGraph, slot);
                        } catch (NoSuchMethodException e) {
                            // Skip TKG cleanup
                        } catch (Exception e) {
                            log.debug("Failed to retract facts via reflection", e);
                        }
                    }

                    // 3. Re-extract entities
                    List<ExtractedEntity> entities = entityExtractor.extract(id, text);
                    if (entities != null && !entities.isEmpty()) {
                        // 4. Repopulate
                        populateEntities(entities, slot, id);
                        syncTemporalFacts(entities, slot, id, System.currentTimeMillis() / 1000L);
                        reextractedCount++;
                    }
                } catch (Exception e) {
                    log.warn("[GraphEnricher] Failed to re-extract entities for '{}': {}", id, e.getMessage());
                    lastError = e.getMessage();
                }
            }
            if (reextractedCount > 0) {
                totalReextracted.addAndGet(reextractedCount);
            }

        } finally {
            lastRunDurationMs.set((System.nanoTime() - startNs) / 1_000_000L);
            reextractInProgress.set(false);
        }

        if (reextractedCount > 0 && graphFacade != null) {
            graphFacade.invalidateCache();
        }

        log.info("[GraphEnricher] Completed re-extraction batch: {} memories re-extracted in {}ms",
                reextractedCount, lastRunDurationMs.get());
        return reextractedCount;
    }

    /**
     * Re-extracts all memories across the entire index in batches until complete.
     *
     * @return total memories re-extracted
     */
    public int reextractAll() {
        int total = 0;
        int batch;
        while ((batch = reextractBatch(50)) > 0) {
            total += batch;
        }
        return total;
    }

    /**
     * Returns current re-extraction progress stats.
     */
    public EnrichmentStats reextractStats() {
        int total = (index != null) ? index.size() : 0;
        int reextracted = totalReextracted.get();
        int pending = Math.max(0, total - reextracted);
        return new EnrichmentStats(
                total, reextracted, pending,
                totalEntitiesAdded.get(),
                totalRelationsAdded.get(),
                reextractInProgress.get(),
                lastRunDurationMs.get(),
                lastError
        );
    }

    private void populateEntities(List<ExtractedEntity> entities, int memoryIdx, String id) {
        int entitiesAdded = 0;
        var entityIds = new ArrayList<Integer>(entities.size());

        for (ExtractedEntity entity : entities) {
            int eid = entityDirectory.intern(entity.name(), entity.typeName());
            if (eid >= 0) {
                entityDirectory.linkEntityToMemory(eid, memoryIdx);
                entityIds.add(eid);
                entitiesAdded++;
            }
        }

        if (entitiesAdded > 0) {
            totalEntitiesAdded.addAndGet(entitiesAdded);

            // Create hyperedge for multi-entity co-occurrence (if >= 2 entities in this memory)
            if (hyperEntityGraph != null && entityIds.size() >= 2) {
                int[] vertexArr = entityIds.stream().mapToInt(Integer::intValue).toArray();
                if (vertexArr.length > HyperEntityLayout.MAX_VERTICES_PER_EDGE) {
                    int[] truncated = new int[HyperEntityLayout.MAX_VERTICES_PER_EDGE];
                    System.arraycopy(vertexArr, 0, truncated, 0, HyperEntityLayout.MAX_VERTICES_PER_EDGE);
                    vertexArr = truncated;
                }
                int[] roles = new int[vertexArr.length];
                roles[0] = HyperEntityGraphMemory.ROLE_SUBJECT;
                for (int i = 1; i < roles.length; i++) {
                    roles[i] = HyperEntityGraphMemory.ROLE_CONTEXT;
                }
                hyperEntityGraph.addHyperedge(vertexArr, roles, 0, 1.0f, memoryIdx, System.currentTimeMillis());
            }
        }
    }

    private void syncTemporalFacts(List<ExtractedEntity> entities, int memoryIdx, String memoryId, long ingestEpochSec) {
        if (temporalKnowledgeGraph == null || entities == null || entities.isEmpty()) return;

        int relationsAdded = 0;
        for (ExtractedEntity entity : entities) {
            if (entity.relations() != null) {
                for (var relation : entity.relations()) {
                    int subjectId = entityDirectory.intern(entity.name(), entity.typeName());
                    int objectId = entityDirectory.intern(relation.targetEntityName(), "UNKNOWN");

                    try {
                        temporalKnowledgeGraph.assertFact(
                                subjectId,
                                relation.relationTypeName(),
                                objectId,
                                -1L, (short) 0,
                                ingestEpochSec,
                                Long.MAX_VALUE,
                                0.8f,
                                false
                        );
                        relationsAdded++;
                    } catch (RuntimeException e) {
                        log.debug("Failed to assert temporal fact for entity '{}' relation '{}': {}",
                                entity.name(), relation.relationTypeName(), e.getMessage());
                    }
                }
            }
        }
        if (relationsAdded > 0) {
            totalRelationsAdded.addAndGet(relationsAdded);
        }
    }
}
