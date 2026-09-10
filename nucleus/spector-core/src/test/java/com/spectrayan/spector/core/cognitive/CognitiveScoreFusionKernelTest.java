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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CognitiveScoreFusionKernel Test Suite")
class CognitiveScoreFusionKernelTest {

    private static final float EPSILON = 1e-5f;

    @Test
    @DisplayName("Phase 1: similarity calculation")
    void testSimilarity() {
        assertEquals(1.0f, CognitiveScoreFusionKernel.similarity(0.0f, 1.0f), EPSILON);
        assertEquals(0.5f, CognitiveScoreFusionKernel.similarity(1.0f, 1.0f), EPSILON);
        assertEquals(0.2f, CognitiveScoreFusionKernel.similarity(2.0f, 2.0f), EPSILON); // 1 / (1 + 4) = 0.2
    }

    @Test
    @DisplayName("Phase 2: importance decay factor calculation")
    void testImportanceDecayFactor() {
        // importance=10.0f (norm = 1.0f), beta=0.5f, decay=1.0f, storageBoost=1.0f
        float factor = CognitiveScoreFusionKernel.importanceDecayFactor(10.0f, 0.5f, 1.0f, 1.0f);
        assertEquals(1.5f, factor, EPSILON);

        // importance=5.0f (norm = 0.5f), beta=0.5f, decay=0.8f, storageBoost=1.2f
        // 1.0 + 0.5 * 0.5 * 0.8 * 1.2 = 1.0 + 0.24 = 1.24
        float factor2 = CognitiveScoreFusionKernel.importanceDecayFactor(5.0f, 0.5f, 0.8f, 1.2f);
        assertEquals(1.24f, factor2, EPSILON);
    }

    @Test
    @DisplayName("Phase 3: valence alignment")
    void testValenceAlignment() {
        float score = 0.8f;
        // Disabled: untouched
        assertEquals(score, CognitiveScoreFusionKernel.applyValenceAlignment(score, false, (byte) 50, (byte) 100), EPSILON);

        // Identical valence: multiplier = 1.0
        assertEquals(score, CognitiveScoreFusionKernel.applyValenceAlignment(score, true, (byte) 100, (byte) 100), EPSILON);

        // Maximum discrepancy (127 vs -128 = 255): multiplier = 0.0
        assertEquals(0.0f, CognitiveScoreFusionKernel.applyValenceAlignment(score, true, (byte) 127, (byte) -128), EPSILON);
    }

    @Test
    @DisplayName("Phase 4: tag relevance")
    void testTagRelevance() {
        float score = 0.5f;
        // In additive mode: unchanged
        assertEquals(score, CognitiveScoreFusionKernel.applyTagRelevance(score, 0.5f, 0.2f, true), EPSILON);

        // In standard mode: score * (1.0 + 0.5 * 0.2) = score * 1.1 = 0.55
        assertEquals(0.55f, CognitiveScoreFusionKernel.applyTagRelevance(score, 0.5f, 0.2f, false), EPSILON);
    }

    @Test
    @DisplayName("Phase 5: hyperfocus modulation")
    void testHyperfocus() {
        float score = 0.6f;
        // Focus false: unchanged
        assertEquals(score, CognitiveScoreFusionKernel.applyHyperfocus(score, false, 1.5f), EPSILON);

        // Focus true: score * boost
        assertEquals(0.9f, CognitiveScoreFusionKernel.applyHyperfocus(score, true, 1.5f), EPSILON);
    }

    @Test
    @DisplayName("Phase 6: associative prior injection")
    void testAssociativePrior() {
        float score = 0.7f;
        float prior = 0.4f;
        float delta = 0.3f;

        // Additive: score + delta * prior = 0.7 + 0.12 = 0.82
        assertEquals(0.82f, CognitiveScoreFusionKernel.applyAssociativePrior(score, prior, delta, true), EPSILON);

        // Multiplicative: score * (1.0 + delta * prior) = 0.7 * (1.0 + 0.12) = 0.784
        assertEquals(0.784f, CognitiveScoreFusionKernel.applyAssociativePrior(score, prior, delta, false), EPSILON);
    }

    @Test
    @DisplayName("End-to-end fused score calculation: pure similarity bypass")
    void testComputeFusedScorePureSimilarity() {
        CognitiveScoreFusionKernel.FusionParams params = new CognitiveScoreFusionKernel.FusionParams(
                1.0f, 0.5f, 0.7f, 0.3f, 0.2f, 1.5f, 0.3f, 1.0f, false, true, true, false
        );

        float score = CognitiveScoreFusionKernel.computeFusedScore(
                0.5f, 1000L, 2000L, 1.0f, (byte) 0, 1.0f, false, 0, 5.0f,
                0.0f, (byte) 0, (byte) 0, false, false, 0.0f, params
        );

        assertEquals(1.0f / 1.5f, score, EPSILON);
    }

    @Test
    @DisplayName("End-to-end fused score: batch calculation parity with scalar")
    void testBatchParity() {
        int count = 4;
        float[] l2dists = {0.1f, 0.5f, 1.2f, 2.0f};
        long[] timestampsMs = {1000L, 2000L, 3000L, 4000L};
        float[] masses = {1.0f, 2.5f, 0.5f, 3.0f};
        byte[] arousals = {10, 50, 0, 100};
        float[] storageStrengths = {1.0f, 2.0f, 1.5f, 3.0f};
        boolean[] hasStorage = {false, true, true, true};
        int[] recallCounts = {0, 3, 1, 5};
        float[] importances = {5.0f, 8.0f, 3.0f, 9.5f};
        float[] tagOverlaps = {0.2f, 0.8f, 0.0f, 0.5f};
        byte[] valences = {10, 20, 30, 40};
        boolean[] focusMatches = {false, true, false, true};
        boolean[] zeroDecays = {false, false, false, false};
        float[] priors = {0.0f, 0.2f, 0.1f, 0.5f};

        long nowMs = 5000L;
        byte queryValence = 25;
        CognitiveScoreFusionKernel.FusionParams params = CognitiveScoreFusionKernel.FusionParams.DEFAULT;

        float[] batchScores = new float[count];
        CognitiveScoreFusionKernel.computeFusedScores(
                l2dists, timestampsMs, masses, arousals, storageStrengths, hasStorage,
                recallCounts, importances, tagOverlaps, valences, focusMatches, zeroDecays,
                priors, nowMs, queryValence, params, batchScores, count
        );

        for (int i = 0; i < count; i++) {
            float scalarScore = CognitiveScoreFusionKernel.computeFusedScore(
                    l2dists[i], timestampsMs[i], nowMs, masses[i], arousals[i],
                    storageStrengths[i], hasStorage[i], recallCounts[i], importances[i],
                    tagOverlaps[i], valences[i], queryValence, focusMatches[i],
                    zeroDecays[i], priors[i], params
            );
            assertEquals(scalarScore, batchScores[i], EPSILON, "Mismatch at index " + i);
        }
    }
}
