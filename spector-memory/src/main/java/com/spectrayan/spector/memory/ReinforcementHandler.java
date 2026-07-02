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

import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.memory.synapse.ActRActivation;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;
import com.spectrayan.spector.memory.sync.MemoryWal;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

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
final class ReinforcementHandler {

    private static final Logger log = LoggerFactory.getLogger(ReinforcementHandler.class);

    private final ValenceTracker valenceTracker;
    private final HebbianGraphBase hebbianGraph;
    private final LateralEvaluator lateralEvaluator;
    private final RecallPipeline recallPipeline;
    private final MemoryWal wal;
    private final TwoFactorConfig twoFactorConfig;

    ReinforcementHandler(ValenceTracker valenceTracker,
                         HebbianGraphBase hebbianGraph,
                         LateralEvaluator lateralEvaluator,
                         RecallPipeline recallPipeline,
                         MemoryWal wal,
                         TwoFactorConfig twoFactorConfig) {
        this.valenceTracker = valenceTracker;
        this.hebbianGraph = hebbianGraph;
        this.lateralEvaluator = lateralEvaluator;
        this.recallPipeline = recallPipeline;
        this.wal = wal;
        this.twoFactorConfig = twoFactorConfig;
    }

    /**
     * Reinforces a memory with the given valence signal.
     *
     * @param memoryId   the memory ID to reinforce
     * @param valence    positive/negative outcome signal (-128 to +127)
     * @param tierRouter the current tier router
     * @param index      the memory index
     */
    void reinforce(String memoryId, byte valence,
                   TierRouter tierRouter, MemoryIndex index) {
        if (memoryId == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "memoryId");
        }
        MemoryLocation loc = index.locate(memoryId);
        if (loc == null) {
            log.warn("Reinforce: memory '{}' not found", memoryId);
            return;
        }

        MemorySegment segment = tierRouter.segmentFor(loc.type());
        if (segment != null) {
            CognitiveRecordLayout layout = tierRouter.layoutFor(loc.type());

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
            // ΔS = sGain × (1 - R(t)) → max boost when retrieval was hard
            var headerLayout = layout.headerLayout();
            if (headerLayout.headerBytes() > 32) { // V2+ has storage_strength
                float currentS = headerLayout.readStorageStrength(segment, loc.offset());
                long timestamp = layout.readTimestamp(segment, loc.offset());
                int rawBucket = DecayStrategy.ageToBucket(timestamp, System.currentTimeMillis());
                float currentR = DecayStrategy.decay(rawBucket);
                float deltaS = twoFactorConfig.sGain() * (1.0f - currentR);
                float newS = Math.min(currentS + deltaS, twoFactorConfig.sMax());
                headerLayout.writeStorageStrength(segment, loc.offset(), newS);
            }
        }

        // Step 5: Lateral evaluator feedback
        if (recallPipeline.wasLateral(memoryId)) {
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
     * @param updatedHints optional ICNU hints for re-fusion (null = auto-compute)
     * @param tierRouter   the current tier router
     * @param index        the memory index
     */
    void reinforceWithHints(String memoryId, byte valence,
                            IngestionHints updatedHints,
                            TierRouter tierRouter, MemoryIndex index) {
        // Delegate core reinforcement
        reinforce(memoryId, valence, tierRouter, index);

        // Importance re-fusion
        MemoryLocation loc = index.locate(memoryId);
        if (loc == null) return;

        MemorySegment segment = tierRouter.segmentFor(loc.type());
        if (segment == null) return;

        CognitiveRecordLayout layout = tierRouter.layoutFor(loc.type());
        float currentImportance = layout.readImportance(segment, loc.offset());

        float newImportance;
        if (updatedHints != null && !updatedHints.isEmpty()) {
            // Re-fuse importance with updated ICNU hints
            float noveltyApprox = Math.min(1.0f, currentImportance / 5.0f);
            float refusedImportance = IcnuWeights.DEFAULT.fuse(updatedHints, noveltyApprox);
            // Blend 50/50 with current importance to avoid wild swings
            newImportance = 0.5f * currentImportance + 0.5f * refusedImportance;
        } else {
            // Degree centrality boost from Hebbian graph
            int graphIdx = loc.partitionIndex();
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

        if (Math.abs(newImportance - currentImportance) > 0.001f) {
            layout.writeImportance(segment, loc.offset(), newImportance);
            log.debug("Reinforce re-fusion: '{}' importance {} → {}",
                    memoryId, currentImportance, newImportance);
        }
    }
}
