/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.score;

import com.spectrayan.spector.core.cognitive.EdgeImportanceKernel;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isPinned;
import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isResolved;

/**
 * Neuroscience-informed edge importance scoring for graph eviction decisions.
 *
 * <h3>Biological Grounding</h3>
 * <p>Each signal in the scoring formula maps to a well-established biological
 * mechanism of synaptic plasticity, maintenance, or pruning:</p>
 * <ol>
 *   <li><b>Weight</b> (Hebbian LTP): Frequently co-recalled memories have strong
 *       synaptic connections that should be preserved.</li>
 *   <li><b>Recency</b> (Synaptic Tagging &amp; Capture): Recently activated edges
 *       are in an early-LTP state and decay exponentially unless consolidated.</li>
 *   <li><b>Bridge Score</b> (Betweenness Centrality): Edges that connect otherwise-
 *       disconnected graph clusters act like critical neural pathways between brain regions.</li>
 *   <li><b>Redundancy</b> (Synaptic Competition): Edges with many alternative paths
 *       (high shared neighbor count) are pruned first, freeing capacity.</li>
 *   <li><b>Memory Importance</b> (ACT-R Base-Level Transfer): High-importance memories
 *       transfer protection to their incident edges.</li>
 *   <li><b>Arousal</b> (Amygdala Modulation): Emotionally intense memories form
 *       more durable associations.</li>
 *   <li><b>Valence Congruence</b> (Mood-Congruent Bonding): Memories of similar emotional
 *       valence are more likely to be co-retrieved.</li>
 *   <li><b>Storage Strength</b> (Two-Factor Memory): Memories with high storage strength
 *       have entrenched associations that resist decay.</li>
 *   <li><b>Zeigarnik Protection</b> (Task Completion): Edges involving unresolved
 *       intentions or pinned memories receive eviction immunity.</li>
 * </ol>
 *
 * <p>All signals are normalized to [0, 1] before weighted linear combination.</p>
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
    private final float[] weightsArray;

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
        this.weightsArray = new float[] {
                wWeight, wRecency, wBridge, wRedundancy, wImportance, wArousal, wValence, wStorage, wZeigarnik
        };
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

        boolean isProtectedA = !isResolved(flagsA) || isPinned(flagsA);
        boolean isProtectedB = !isResolved(flagsB) || isPinned(flagsB);

        return EdgeImportanceKernel.score(
                weight, currentCycle, lastCycle, bridgeScore, sharedNeighbors,
                importanceA, importanceB, arousalA, arousalB, valenceA, valenceB,
                storageStrengthA, storageStrengthB, isProtectedA, isProtectedB, weightsArray);
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
        return EdgeImportanceKernel.scoreStructural(
                weight, currentCycle, lastCycle, bridgeScore, sharedNeighbors, weightsArray);
    }
}
