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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.HeaderCursor;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final com.spectrayan.spector.config.properties.TwoFactorProperties twoFactorConfig;
    private final ProfileAdaptor profileAdaptor;

    public ReinforcementHandler(ValenceTracker valenceTracker,
                         HebbianGraphBase hebbianGraph,
                         LateralEvaluator lateralEvaluator,
                         RecallPathway recallPathway,
                         MemoryWal wal,
                         com.spectrayan.spector.config.properties.TwoFactorProperties twoFactorConfig,
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
        if (cognitiveRouter != null) {
            cognitiveRouter.reinforce(loc, valence, valenceTracker.learningRate(),
                    twoFactorConfig != null ? twoFactorConfig.sGain() : 1.0f,
                    twoFactorConfig != null ? twoFactorConfig.sMax() : 100.0f);
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
        if (profileAdaptor != null && cognitiveRouter != null && loc.type() != MemoryType.EPISODIC) {
            try {
                byte profileOrdinal = cognitiveRouter.readLastRecallProfile(loc);
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
                            RememberHints updatedHints,
                            PartitionRegistry partitionRegistry, MemoryIndex index) {
        // Delegate core reinforcement
        reinforce(memoryId, valence, partitionRegistry, index);

        // Importance re-fusion
        MemoryLocation loc = index.locate(memoryId);
        if (loc == null) return;

        CognitiveMemoryRouter cognitiveRouter = partitionRegistry.routerFor(loc.colocatedPartition());
        if (cognitiveRouter == null) return;

        try (HeaderCursor cursor = cognitiveRouter.cursor(loc.type())) {
            if (cursor == null) return;
            cursor.seekOffset(loc.offset());
            float oldImportance = cursor.importance();
            float finalImportance = cursor.updateImportance(currentImportance -> {
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
}
