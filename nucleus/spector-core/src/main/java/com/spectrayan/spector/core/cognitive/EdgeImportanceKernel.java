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
package com.spectrayan.spector.core.cognitive;

import com.spectrayan.spector.core.similarity.VectorOps;

/**
 * Pure mathematical kernel for 9-signal multi-factor synaptic pruning and edge importance (ADR-0033 Domain 5, #13).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class EdgeImportanceKernel {

    public static final int IDX_WEIGHT = 0;
    public static final int IDX_RECENCY = 1;
    public static final int IDX_BRIDGE = 2;
    public static final int IDX_REDUNDANCY = 3;
    public static final int IDX_IMPORTANCE = 4;
    public static final int IDX_AROUSAL = 5;
    public static final int IDX_VALENCE = 6;
    public static final int IDX_STORAGE = 7;
    public static final int IDX_ZEIGARNIK = 8;

    public static final float[] DEFAULT_WEIGHTS = {
            0.18f, // wWeight
            0.15f, // wRecency
            0.15f, // wBridge
            0.08f, // wRedundancy
            0.12f, // wImportance
            0.10f, // wArousal
            0.07f, // wValence
            0.08f, // wStorage
            0.07f  // wZeigarnik
    };

    private EdgeImportanceKernel() {}

    /**
     * Computes the 9-signal neuroscience-informed edge importance score.
     *
     * @param weight co-recall weight
     * @param currentCycle current reflection cycle counter
     * @param lastCycle cycle when edge was last strengthened
     * @param bridgeScore structural bridge score (0-255)
     * @param sharedNeighbors common neighbors count
     * @param importanceA raw importance of node A (0.0-10.0)
     * @param importanceB raw importance of node B (0.0-10.0)
     * @param arousalA emotional arousal of node A (byte)
     * @param arousalB emotional arousal of node B (byte)
     * @param valenceA valence of node A (byte)
     * @param valenceB valence of node B (byte)
     * @param storageStrengthA storage strength of node A
     * @param storageStrengthB storage strength of node B
     * @param isProtectedA whether node A has protection (e.g. pinned or unresolved)
     * @param isProtectedB whether node B has protection (e.g. pinned or unresolved)
     * @param weights 9-element array of signal weights, or null for default
     * @return importance score
     */
    public static float score(
            final float weight,
            final int currentCycle,
            final int lastCycle,
            final int bridgeScore,
            final int sharedNeighbors,
            final float importanceA,
            final float importanceB,
            final byte arousalA,
            final byte arousalB,
            final byte valenceA,
            final byte valenceB,
            final float storageStrengthA,
            final float storageStrengthB,
            final boolean isProtectedA,
            final boolean isProtectedB,
            final float[] weights) {

        final float[] w = (weights != null && weights.length >= 9) ? weights : DEFAULT_WEIGHTS;

        // Signal 1: Weight — Hebbian LTP
        final float weightSignal = VectorOps.sigmoid(weight - 3.0f);

        // Signal 2: Recency — STC theory (decay with ~50 cycle half-life)
        final float recencySignal = (float) Math.exp(-(currentCycle - lastCycle) / 72.0);

        // Signal 3: Bridge score
        final float bridgeSignal = bridgeScore / 255.0f;

        // Signal 4: Redundancy
        final float redundancy = 1.0f / (1.0f + sharedNeighbors * 0.3f);

        // Signal 5: Importance
        final float avgImportance = (importanceA + importanceB) / 2.0f;
        final float importanceSignal = Math.min(1.0f, avgImportance / 10.0f);

        // Signal 6: Arousal
        final int arousalMax = Math.max(Byte.toUnsignedInt(arousalA), Byte.toUnsignedInt(arousalB));
        final float arousalSignal = arousalMax / 255.0f;

        // Signal 7: Valence congruence
        final float valenceDiff = Math.abs(valenceA - valenceB) / 255.0f;
        final float valenceCongruence = 1.0f - valenceDiff;

        // Signal 8: Storage strength
        final float avgStorage = (storageStrengthA + storageStrengthB) / 2.0f;
        final float storageSignal = Math.min(1.0f, Math.max(0.0f, (avgStorage - 1.0f) / 4.0f));

        // Signal 9: Zeigarnik protection
        final float protectionBoost = (isProtectedA || isProtectedB) ? 0.2f : 0.0f;

        return w[IDX_WEIGHT] * weightSignal
                + w[IDX_RECENCY] * recencySignal
                + w[IDX_BRIDGE] * bridgeSignal
                + w[IDX_REDUNDANCY] * redundancy
                + w[IDX_IMPORTANCE] * importanceSignal
                + w[IDX_AROUSAL] * arousalSignal
                + w[IDX_VALENCE] * valenceCongruence
                + w[IDX_STORAGE] * storageSignal
                + w[IDX_ZEIGARNIK] * protectionBoost;
    }

    /**
     * Computes the simplified 4-signal structural edge importance score.
     */
    public static float scoreStructural(
            final float weight,
            final int currentCycle,
            final int lastCycle,
            final int bridgeScore,
            final int sharedNeighbors,
            final float[] weights) {

        final float[] w = (weights != null && weights.length >= 4) ? weights : DEFAULT_WEIGHTS;

        final float weightSignal = VectorOps.sigmoid(weight - 3.0f);
        final float recencySignal = (float) Math.exp(-(currentCycle - lastCycle) / 72.0);
        final float bridgeSignal = bridgeScore / 255.0f;
        final float redundancy = 1.0f / (1.0f + sharedNeighbors * 0.3f);

        float totalStructural = w[IDX_WEIGHT] + w[IDX_RECENCY] + w[IDX_BRIDGE] + w[IDX_REDUNDANCY];
        if (totalStructural <= 0.0f) {
            totalStructural = 1.0f;
        }

        return (w[IDX_WEIGHT] / totalStructural) * weightSignal
                + (w[IDX_RECENCY] / totalStructural) * recencySignal
                + (w[IDX_BRIDGE] / totalStructural) * bridgeSignal
                + (w[IDX_REDUNDANCY] / totalStructural) * redundancy;
    }
}
