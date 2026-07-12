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

import static com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isPinned;
import static com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isResolved;

/**
 * Neuroscience-informed edge importance scoring for graph eviction decisions.
 *
 * <h3>Biological Grounding</h3>
 * <p>Each signal in the scoring formula maps to a well-established biological
 * mechanism for synaptic pruning and potentiation:</p>
 * <ul>
 *   <li><b>Weight (Hebbian LTP)</b> — "cells that fire together wire together"</li>
 *   <li><b>Recency (STC theory)</b> — early-LTP without consolidation decays</li>
 *   <li><b>Bridge score</b> — hub neurons with high betweenness centrality</li>
 *   <li><b>Redundancy</b> — synaptic competition prunes redundant connections</li>
 *   <li><b>Memory importance (ACT-R)</b> — base-level activation transfers to edges</li>
 *   <li><b>Arousal (amygdala)</b> — emotionally intense edges resist pruning</li>
 *   <li><b>Valence congruence</b> — mood-congruent memories form stronger bonds</li>
 *   <li><b>Storage strength (Two-Factor)</b> — deeply encoded memories protect edges</li>
 *   <li><b>Zeigarnik protection</b> — unfinished tasks keep their network active</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <p>All computations are branch-free arithmetic (~20 cycles total). Memory-derived
 * signals require off-heap header reads at the call site but are NOT performed inside
 * this scorer. This class is a pure function — no I/O, no locks, no state.</p>
 *
 * @see BridgeDetector
 */
public final class EdgeImportance {

    // ── Configurable signal weights (sum to 1.0) ──

    private final float wWeight;
    private final float wRecency;
    private final float wBridge;
    private final float wRedundancy;
    private final float wImportance;
    private final float wArousal;
    private final float wValence;
    private final float wStorage;
    private final float wZeigarnik;

    /** Default signal weights — neuroscience-informed initial tuning. */
    public static final EdgeImportance DEFAULT = new EdgeImportance(
            0.15f,  // weight (Hebbian LTP frequency)
            0.10f,  // recency (STC early-LTP decay)
            0.20f,  // bridge (hub neuron centrality)
            0.10f,  // redundancy (synaptic competition)
            0.15f,  // importance (ACT-R base-level transfer)
            0.10f,  // arousal (amygdala modulation)
            0.05f,  // valence (mood-congruent bonding)
            0.05f,  // storage (Two-Factor resilience)
            0.10f   // Zeigarnik (unfinished task protection)
    );

    /**
     * Creates an EdgeImportance scorer with explicit signal weights.
     *
     * <p>Weights should sum to 1.0 for normalized scores. The scorer does not
     * enforce this — non-normalized weights work but produce scores outside [0, 1].</p>
     */
    public EdgeImportance(float wWeight, float wRecency, float wBridge,
                          float wRedundancy, float wImportance, float wArousal,
                          float wValence, float wStorage, float wZeigarnik) {
        this.wWeight = wWeight;
        this.wRecency = wRecency;
        this.wBridge = wBridge;
        this.wRedundancy = wRedundancy;
        this.wImportance = wImportance;
        this.wArousal = wArousal;
        this.wValence = wValence;
        this.wStorage = wStorage;
        this.wZeigarnik = wZeigarnik;
    }

    /**
     * Computes a multi-signal importance score for an edge A→B.
     *
     * <p>Higher score = more important to keep. An edge with score 0.8 should
     * be preferred over an edge with score 0.3 during eviction.</p>
     *
     * @param weight          co-recall weight (Hebbian LTP count)
     * @param currentCycle    current reflection cycle counter
     * @param lastCycle       cycle when this edge was last strengthened
     * @param bridgeScore     structural bridge importance (0-255)
     * @param sharedNeighbors number of common neighbors between A and B
     * @param importanceA     importance of memory A (0.0-10.0 from synaptic header)
     * @param importanceB     importance of memory B (0.0-10.0 from synaptic header)
     * @param arousalA        arousal of memory A (unsigned 0-255)
     * @param arousalB        arousal of memory B (unsigned 0-255)
     * @param valenceA        valence of memory A (signed -128 to +127)
     * @param valenceB        valence of memory B (signed -128 to +127)
     * @param storageStrengthA storage strength of memory A (1.0+)
     * @param storageStrengthB storage strength of memory B (1.0+)
     * @param flagsA          flags byte of memory A (pinned, resolved, etc.)
     * @param flagsB          flags byte of memory B (pinned, resolved, etc.)
     * @return importance score (higher = more important to keep)
     */
    public float score(float weight, int currentCycle, int lastCycle,
                       int bridgeScore, int sharedNeighbors,
                       float importanceA, float importanceB,
                       byte arousalA, byte arousalB,
                       byte valenceA, byte valenceB,
                       float storageStrengthA, float storageStrengthB,
                       byte flagsA, byte flagsB) {

        // Signal 1: Weight — Hebbian LTP ("cells that fire together wire together")
        // Sigmoid normalization: maps co-recall count to [0, 1]
        float weightSignal = (float) (1.0 / (1.0 + Math.exp(-weight + 3.0)));

        // Signal 2: Recency — STC theory (early-LTP without consolidation decays)
        // Exponential decay with ~50 cycle half-life (ln(2)/72 ≈ 0.0096)
        float recencySignal = (float) Math.exp(-(currentCycle - lastCycle) / 72.0);

        // Signal 3: Bridge score — hub neurons with high betweenness centrality
        float bridgeSignal = bridgeScore / 255.0f;

        // Signal 4: Redundancy — synaptic competition (prune redundant connections)
        // More shared neighbors = lower importance (this edge is replaceable)
        float redundancy = 1.0f / (1.0f + sharedNeighbors * 0.3f);

        // Signal 5: Memory importance — ACT-R base-level activation transfer
        // High-importance memories transfer protection to their edges
        float avgImportance = (importanceA + importanceB) / 2.0f;
        float importanceSignal = Math.min(1.0f, avgImportance / 10.0f);

        // Signal 6: Arousal — amygdala-mediated connection strengthening
        // If EITHER memory is emotionally intense, the edge is protected
        int arousalMax = Math.max(
                Byte.toUnsignedInt(arousalA), Byte.toUnsignedInt(arousalB));
        float arousalSignal = arousalMax / 255.0f;

        // Signal 7: Valence congruence — mood-congruent memory bonding (Bower, 1981)
        // Same-valence memories form stronger associations
        float valenceDiff = Math.abs(valenceA - valenceB) / 255.0f;
        float valenceCongruence = 1.0f - valenceDiff;

        // Signal 8: Storage strength — Two-Factor Memory resilience (Bjork & Bjork)
        // Deeply encoded memories protect their connections
        float avgStorage = (storageStrengthA + storageStrengthB) / 2.0f;
        float storageSignal = Math.min(1.0f, Math.max(0.0f, (avgStorage - 1.0f) / 4.0f));

        // Signal 9: Zeigarnik protection — unfinished tasks keep their network active
        boolean eitherUnresolved = !isResolved(flagsA) || !isResolved(flagsB);
        boolean eitherPinned = isPinned(flagsA) || isPinned(flagsB);
        float protectionBoost = (eitherUnresolved || eitherPinned) ? 0.2f : 0.0f;

        return wWeight     * weightSignal
             + wRecency    * recencySignal
             + wBridge     * bridgeSignal
             + wRedundancy * redundancy
             + wImportance * importanceSignal
             + wArousal    * arousalSignal
             + wValence    * valenceCongruence
             + wStorage    * storageSignal
             + wZeigarnik  * protectionBoost;
    }

    /**
     * Simplified 5-signal score for use when synaptic header signals are unavailable.
     *
     * <p>Falls back to edge-intrinsic signals only. Used during migration from v1
     * format where metadata defaults to zero, or when header reads are too expensive
     * (batch decay processing).</p>
     *
     * @param weight          co-recall weight
     * @param currentCycle    current reflection cycle counter
     * @param lastCycle       cycle when edge was last strengthened
     * @param bridgeScore     structural bridge importance (0-255)
     * @param sharedNeighbors number of common neighbors between A and B
     * @return importance score
     */
    public float scoreStructural(float weight, int currentCycle, int lastCycle,
                                 int bridgeScore, int sharedNeighbors) {
        float weightSignal = (float) (1.0 / (1.0 + Math.exp(-weight + 3.0)));
        float recencySignal = (float) Math.exp(-(currentCycle - lastCycle) / 72.0);
        float bridgeSignal = bridgeScore / 255.0f;
        float redundancy = 1.0f / (1.0f + sharedNeighbors * 0.3f);

        // Redistribute neuroscience weights to structural signals
        float totalStructural = wWeight + wRecency + wBridge + wRedundancy;
        if (totalStructural <= 0.0f) totalStructural = 1.0f;

        return (wWeight / totalStructural)     * weightSignal
             + (wRecency / totalStructural)    * recencySignal
             + (wBridge / totalStructural)     * bridgeSignal
             + (wRedundancy / totalStructural) * redundancy;
    }
}
