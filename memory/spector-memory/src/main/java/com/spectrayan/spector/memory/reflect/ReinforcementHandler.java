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
package com.spectrayan.spector.memory.reflect;

import com.spectrayan.spector.memory.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.amygdala.Valence;
import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.IndexRecordMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.RecallPathway;
import com.spectrayan.spector.memory.synapse.ActRActivation;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;
import com.spectrayan.spector.memory.sync.MemoryWal;

import com.spectrayan.spector.memory.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.RecallPathway;
import com.spectrayan.spector.memory.synapse.ActRActivation;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;
import com.spectrayan.spector.memory.sync.MemoryWal;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorPropertyConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

/**
 * Handles memory reinforcement — valence tracking, Long-Term Potentiation (LTP),
 * ACT-R activation updates, Two-Factor storage strength, and optional ICNU
 * importance re-fusion.
 *
 * <h3>Reinforcement Pipeline</h3>
 * <ol>
 *   <li><b>Valence</b> — updates the valence tracker for emotional weighting</li>
 *   <li><b>LTP</b> — increments agent recall count (strengthens retrieval)</li>
 *   <li><b>ACT-R</b> — records recall timestamp in ring buffer (V3 headers)</li>
 *   <li><b>Two-Factor</b> — updates storage strength S(t) via Bjork &amp; Bjork model</li>
 *   <li><b>Lateral feedback</b> — informs the lateral evaluator for neurodivergent tuning</li>
 *   <li><b>WAL</b> — appends reinforce event for durability</li>
 * </ol>
 *
 * <h3>ICNU Re-fusion (optional)</h3>
 * <p>When {@code updatedHints} are provided, importance is re-fused using the ICNU formula
 * and blended 50/50 with current importance. When null, a Hebbian degree-centrality boost
 * is applied instead.</p>
 */
public final class ReinforcementHandler {

    private static final Logger log = LoggerFactory.getLogger(ReinforcementHandler.class);

    private final ValenceTracker valenceTracker;
    private final HebbianGraphBase hebbianGraph;
    private final LateralEvaluator lateralEvaluator;
    private final RecallPathway recallPathway;
    private final MemoryWal wal;
    private final TwoFactorConfig twoFactorConfig;
    private final ProfileAdaptor profileAdaptor;

    public ReinforcementHandler(ValenceTracker valenceTracker,
                         HebbianGraphBase hebbianGraph,
                         LateralEvaluator lateralEvaluator,
                         RecallPathway recallPathway,
                         MemoryWal wal,
                         TwoFactorConfig twoFactorConfig,
                         ProfileAdaptor profileAdaptor) {
        this.valenceTracker = valenceTracker;
        this.hebbianGraph = hebbianGraph;
        this.lateralEvaluator = lateralEvaluator;
        this.recallPathway = recallPathway;
        this.wal = wal;
        this.twoFactorConfig = twoFactorConfig;
        this.profileAdaptor = profileAdaptor;
    }

    /**
     * Reinforces a memory with the given valence signal.
     *
     * @param memoryId   the memory ID to reinforce
     * @param valence    positive/negative outcome signal (-128 to +127)
     * @param partitionRegistry the live partition registry (#443)
     * @param index           the memory index
     */
    public void reinforce(String memoryId, byte valence,
                   PartitionRegistry partitionRegistry, MemoryIndex index) {
        if (memoryId == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "memoryId");
        }
        MemoryLocation loc = index.locate(memoryId);
        if (loc == null) {
            log.warn("Reinforce: memory '{}' not found", memoryId);
            return;
        }

        // #443: resolve the store by the memory's colocated partition.
        CognitiveMemoryRouter cognitiveRouter = partitionRegistry.routerFor(loc.colocatedPartition());
        MemorySegment segment = cognitiveRouter.segmentFor(loc.type());
        if (segment != null) {
            CognitiveRecordLayout layout = cognitiveRouter.layoutFor(loc.type());

            if (cognitiveRouter.audit() != null) {
                int slotIndex = (int) (loc.offset() / layout.stride());
                long creationTs = layout.readTimestamp(segment, loc.offset());
                long nowMs = System.currentTimeMillis();

                // Step 1: Valence tracking
                valenceTracker.reinforce(segment, loc.offset(), layout, valence);

                // Step 2: LTP — increment agent recall count in audit region
                cognitiveRouter.audit().incrementAgentRecallCount(loc.type(), slotIndex);

                // Step 3: ACT-R — record recall timestamp in 8-slot ring buffer
                cognitiveRouter.audit().recordRecall(loc.type(), slotIndex, creationTs, nowMs, (byte) 0, 0);

                // Step 4: Two-Factor Memory — update storage strength S(t) in audit region
                int rawBucket = DecayStrategy.ageToBucket(creationTs, nowMs);
                float currentR = DecayStrategy.decay(rawBucket);
                float deltaS = twoFactorConfig.sGain() * (1.0f - currentR);
                cognitiveRouter.audit().casStorageStrength(loc.type(), slotIndex,
                        currentS -> Math.min(twoFactorConfig.sMax(),
                                Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MIN, currentS + deltaS)));
            } else {
                // Step 1: Valence tracking
                valenceTracker.reinforce(segment, loc.offset(), layout, valence);

                // Step 2: LTP — increment agent recall count
                layout.incrementAgentRecallCount(segment, loc.offset());

                // Step 3: ACT-R — record recall timestamp in ring buffer (V3 only)
                if (layout.headerLayout().version() >= 3) {
                    long creationTs = layout.readTimestamp(segment, loc.offset());
                    ActRActivation.recordRecall(segment, loc.offset(), creationTs,
                            System.currentTimeMillis());
                }

                // Step 4: Two-Factor Memory — update storage strength S(t)
                var headerLayout = layout.headerLayout();
                if (headerLayout.headerBytes() > 32) { // V2+ has storage_strength
                    long timestamp = layout.readTimestamp(segment, loc.offset());
                    int rawBucket = DecayStrategy.ageToBucket(timestamp, System.currentTimeMillis());
                    float currentR = DecayStrategy.decay(rawBucket);
                    float deltaS = twoFactorConfig.sGain() * (1.0f - currentR);
                    headerLayout.casStorageStrength(segment, loc.offset(), currentS -> Math.min(twoFactorConfig.sMax(), Math.max(0.01f, currentS + deltaS)));
                }
            }
        }

        // Step 5: Lateral evaluator feedback
        if (recallPathway.wasLateral(memoryId)) {
            if (valence > 0) {
                lateralEvaluator.recordLateralReinforcement();
                log.debug("Lateral reinforcement: '{}' (positive valence={})", memoryId, valence);
            } else if (valence < 0) {
                lateralEvaluator.recordLateralSuppression();
                log.debug("Lateral suppression via reinforce: '{}' (negative valence={})",
                        memoryId, valence);
            }
        }

        // Step 6: WAL append
        wal.appendReinforce(memoryId, valence);

        // Step 7: ProfileAdaptor — record reinforcement outcome for profile learning
        if (profileAdaptor != null && segment != null) {
            try {
                byte profileOrdinal;
                if (cognitiveRouter.audit() != null) {
                    int slotIndex = (int) (loc.offset() / cognitiveRouter.layoutFor(loc.type()).stride());
                    profileOrdinal = cognitiveRouter.audit().readAuditRecord(loc.type(), slotIndex).lastRecallProfile();
                } else {
                    profileOrdinal = segment.get(
                            java.lang.foreign.ValueLayout.JAVA_BYTE,
                            loc.offset() + com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE);
                }
                if (profileOrdinal >= 0 && profileOrdinal < com.spectrayan.spector.memory.model.CognitiveProfile.values().length) {
                    com.spectrayan.spector.memory.model.CognitiveProfile usedProfile =
                            com.spectrayan.spector.memory.model.CognitiveProfile.values()[profileOrdinal];
                    // Read tag names from the MemoryIndex (bloom filter can't be reversed)
                    String[] tags = index.tags(memoryId);
                    if (tags != null && tags.length > 0) {
                        profileAdaptor.recordOutcome(usedProfile, tags, valence > 0);
                    }
                }
            } catch (RuntimeException e) {
                log.debug("ProfileAdaptor recording failed for '{}': {}", memoryId, e.getMessage());
            }
        }

        log.debug("Reinforce: '{}' with valence={}", memoryId, valence);
    }

    /**
     * Reinforces a memory with optional ICNU importance re-fusion.
     *
     * <p>When {@code updatedHints} are provided, importance is re-fused using
     * the ICNU formula and blended 50/50 with the current importance. When null,
     * a Hebbian degree-centrality boost is applied instead.</p>
     *
     * @param memoryId     the memory ID to reinforce
     * @param valence      positive/negative outcome (-128 to +127)
     * @param updatedHints   optional ICNU hints for re-fusion (null = auto-compute)
     * @param partitionRegistry the live partition registry (#443)
     * @param index          the memory index
     */
    public void reinforceWithHints(String memoryId, byte valence,
                            IngestionHints updatedHints,
                            PartitionRegistry partitionRegistry, MemoryIndex index) {
        // Delegate core reinforcement
        reinforce(memoryId, valence, partitionRegistry, index);

        // Importance re-fusion
        MemoryLocation loc = index.locate(memoryId);
        if (loc == null) return;

        CognitiveMemoryRouter cognitiveRouter = partitionRegistry.routerFor(loc.colocatedPartition());
        MemorySegment segment = cognitiveRouter.segmentFor(loc.type());
        if (segment == null) return;

        CognitiveRecordLayout layout = cognitiveRouter.layoutFor(loc.type());
        var headerLayout = layout.headerLayout();

        float oldImportance = layout.readImportance(segment, loc.offset());
        float finalImportance = headerLayout.casImportance(segment, loc.offset(), currentImportance -> {
            float newImportance;
            if (updatedHints != null && !updatedHints.isEmpty()) {
                // Re-fuse importance with updated ICNU hints
                float noveltyApprox = Math.min(1.0f, currentImportance / 5.0f);
                float refusedImportance = IcnuWeights.DEFAULT.fuse(updatedHints, noveltyApprox);
                // Blend 50/50 with current importance to avoid wild swings
                newImportance = 0.5f * currentImportance + 0.5f * refusedImportance;
            } else {
                // Degree centrality boost from Hebbian graph
                int graphIdx = loc.graphSlot();
                if (graphIdx >= 0 && hebbianGraph != null) {
                    var edges = hebbianGraph.neighbors(graphIdx);
                    int degree = edges.size();
                    // Logarithmic boost: +5% per edge, capped at +30%
                    float boost = Math.min(0.30f, degree * 0.05f);
                    newImportance = Math.min(10.0f, currentImportance * (1.0f + boost));
                } else {
                    newImportance = currentImportance; // no graph data, no change
                }
            }
            return newImportance;
        });

        if (Math.abs(finalImportance - oldImportance) > 0.001f) {
            log.debug("Reinforce re-fusion: '{}' importance {} → {}",
                    memoryId, oldImportance, finalImportance);
        }
    }
}
