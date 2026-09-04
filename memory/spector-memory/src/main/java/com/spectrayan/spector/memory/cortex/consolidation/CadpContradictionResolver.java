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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Contradiction-Aware Detection with Directional Resolution (CADP) Engine.
 *
 * <p>Encapsulates directional winner/loser resolution, off-heap flag marking,
 * hyperedge creation in {@link HyperEntityGraphMemory}, and fact retraction
 * in {@link TemporalKnowledgeGraph}.</p>
 */
public final class CadpContradictionResolver {

    private static final Logger log = LoggerFactory.getLogger(CadpContradictionResolver.class);

    /**
     * Result of CADP contradiction resolution.
     *
     * @param winner the winning (newer/stronger) memory
     * @param loser  the losing (corrected) memory
     */
    public record ResolutionResult(CognitiveRecord winner, CognitiveRecord loser) {}

    private CadpContradictionResolver() {}

    /**
     * Determines the winning and losing memory under CADP rules (#507).
     *
     * <ol>
     *   <li>Primary: Newer memory wins ({@code timestampMs}).</li>
     *   <li>Secondary: Higher storage strength wins ({@code storageStrength}).</li>
     *   <li>Tertiary: Lexicographically lower ID wins (deterministic tiebreak).</li>
     * </ol>
     *
     * @param recordA first candidate record
     * @param recordB second candidate record
     * @return {@link ResolutionResult} with winner and loser
     */
    public static ResolutionResult determineWinnerLoser(CognitiveRecord recordA, CognitiveRecord recordB) {
        int cmp = Long.compare(recordA.timestampMs(), recordB.timestampMs());
        if (cmp == 0) {
            cmp = Float.compare(recordA.storageStrength(), recordB.storageStrength());
        }
        if (cmp == 0) {
            cmp = recordB.id().compareTo(recordA.id()); // lower ID wins -> A wins when B > A
        }

        if (cmp >= 0) {
            return new ResolutionResult(recordA, recordB);
        } else {
            return new ResolutionResult(recordB, recordA);
        }
    }

    /**
     * Executes full CADP contradiction resolution between two contradictory memories across partitions (#446).
     *
     * @param recordA                first record
     * @param recordB                second record
     * @param partitionManager       partition manager resolving partition routers (optional)
     * @param store                  cognitive tier store fallback (optional)
     * @param hyperEntityGraph       hypergraph memory (optional)
     * @param entityDirectory        entity directory (optional)
     * @param temporalKnowledgeGraph temporal knowledge graph (optional)
     * @return the resolution outcome
     */
    public static ResolutionResult resolve(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            com.spectrayan.spector.memory.persist.PartitionManager partitionManager,
            CognitiveRecordMemory store,
            HyperEntityGraphMemory hyperEntityGraph,
            EntityDirectory entityDirectory,
            TemporalKnowledgeGraph temporalKnowledgeGraph) {

        ResolutionResult result = determineWinnerLoser(recordA, recordB);
        CognitiveRecord winner = result.winner();
        CognitiveRecord loser = result.loser();

        // 1. Mark loser contradicted off-heap on the partition where loser physically resides
        if (partitionManager != null) {
            var router = partitionManager.routerFor(loser.partitionIndex());
            if (router != null) {
                var layout = router.layoutFor(loser.memoryType());
                var segment = router.segmentFor(loser.memoryType());
                if (layout != null && segment != null) {
                    layout.markContradicted(segment, loser.byteOffset());
                }
            }
        } else if (store != null) {
            MemorySegment segment = store.segment();
            CognitiveRecordLayout layout = store.cognitiveLayout();
            layout.markContradicted(segment, loser.byteOffset());
        }
        log.info("CADP resolved: winner='{}' corrects loser='{}'", winner.id(), loser.id());

        // 2. Resolve entity references for winner and loser
        int slotWinner = -1;
        int slotLoser = -1;
        if (partitionManager != null) {
            var routerWinner = partitionManager.routerFor(winner.partitionIndex());
            if (routerWinner != null) {
                var storeWinner = routerWinner.get(winner.memoryType());
                var layoutWinner = routerWinner.layoutFor(winner.memoryType());
                if (storeWinner != null && layoutWinner != null) {
                    slotWinner = memorySlot(winner, storeWinner, layoutWinner);
                }
            }
            var routerLoser = partitionManager.routerFor(loser.partitionIndex());
            if (routerLoser != null) {
                var storeLoser = routerLoser.get(loser.memoryType());
                var layoutLoser = routerLoser.layoutFor(loser.memoryType());
                if (storeLoser != null && layoutLoser != null) {
                    slotLoser = memorySlot(loser, storeLoser, layoutLoser);
                }
            }
        } else if (store != null) {
            slotWinner = memorySlot(winner, store, store.cognitiveLayout());
            slotLoser = memorySlot(loser, store, store.cognitiveLayout());
        }

        List<Integer> entitiesWinner = findEntitiesForRecord(entityDirectory, winner, slotWinner);
        List<Integer> entitiesLoser = findEntitiesForRecord(entityDirectory, loser, slotLoser);

        // 3. Add TYPE_CONTRADICTS hyperedge with ROLE_CORRECTOR and ROLE_CORRECTED (#507, #528)
        if (hyperEntityGraph != null && entitiesWinner != null && entitiesLoser != null) {
            for (int eW : entitiesWinner) {
                for (int eL : entitiesLoser) {
                    if (eW != eL) {
                        hyperEntityGraph.addHyperedge(
                                new int[]{eW, eL},
                                new int[]{HyperEntityGraphMemory.ROLE_CORRECTOR, HyperEntityGraphMemory.ROLE_CORRECTED},
                                HyperEntityGraphMemory.TYPE_CONTRADICTS,
                                1.0f, -1, System.currentTimeMillis());
                    }
                }
            }
        }

        // 4. Retract loser's facts from TemporalKnowledgeGraph (#527)
        if (temporalKnowledgeGraph != null && entitiesLoser != null) {
            for (int eL : entitiesLoser) {
                try {
                    var facts = temporalKnowledgeGraph.factsAbout(eL).resolveAll();
                    if (facts != null) {
                        for (var fact : facts) {
                            if (entitiesWinner == null || !entitiesWinner.contains(fact.objectEntityId())) {
                                temporalKnowledgeGraph.retractFact(fact.factId());
                                log.info("CADP: Retracted temporal fact {} for corrected entity {}", fact.factId(), eL);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    log.debug("CADP: Failed to retract temporal fact for entity {}: {}", eL, e.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Executes full CADP contradiction resolution between two contradictory memories in a single store.
     */
    public static ResolutionResult resolve(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            CognitiveRecordMemory store,
            HyperEntityGraphMemory hyperEntityGraph,
            EntityDirectory entityDirectory,
            TemporalKnowledgeGraph temporalKnowledgeGraph) {
        return resolve(recordA, recordB, null, store, hyperEntityGraph, entityDirectory, temporalKnowledgeGraph);
    }

    /**
     * Computes the 0-based memory slot index for a cognitive record in the given store.
     */
    public static int memorySlot(CognitiveRecord record, CognitiveRecordMemory store, CognitiveRecordLayout layout) {
        long headerOffset = store.isPersistent() ? CognitiveRecordMemory.METADATA_PREAMBLE_BYTES : 0L;
        return (int) ((record.byteOffset() - headerOffset) / layout.stride());
    }

    /**
     * Looks up entity IDs associated with a specific memory record in the {@link EntityDirectory}.
     * Combines memory slot index references with entity name recognition against record text.
     */
    public static List<Integer> findEntitiesForRecord(EntityDirectory entityDirectory, CognitiveRecord record, int slot) {
        if (entityDirectory == null) return null;
        Set<Integer> entities = new HashSet<>();
        if (slot >= 0) {
            int ecnt = entityDirectory.entityCount();
            for (int e = 0; e < ecnt; e++) {
                int refCount = entityDirectory.memoryRefCount(e);
                for (int r = 0; r < refCount; r++) {
                    if (entityDirectory.memoryRefAt(e, r) == slot) {
                        entities.add(e);
                        break;
                    }
                }
            }
        }
        if (record != null && record.text() != null && !record.text().isBlank()) {
            String lowerText = record.text().toLowerCase(Locale.ROOT);
            int ecnt = entityDirectory.entityCount();
            for (int e = 0; e < ecnt; e++) {
                String name = entityDirectory.entityName(e);
                if (name != null && !name.isBlank() && lowerText.contains(name.toLowerCase(Locale.ROOT))) {
                    entities.add(e);
                }
            }
        }
        return entities.isEmpty() ? null : new ArrayList<>(entities);
    }

    /**
     * Looks up entity IDs associated with a specific memory slot in the {@link EntityDirectory}.
     */
    public static List<Integer> findEntitiesForSlot(EntityDirectory entityDirectory, int slot) {
        return findEntitiesForRecord(entityDirectory, null, slot);
    }
}
