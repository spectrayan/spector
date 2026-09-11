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
package com.spectrayan.spector.core.parity;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.core.cognitive.ActRActivationKernel;
import com.spectrayan.spector.core.cognitive.CognitiveMassKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel.FusionParams;
import com.spectrayan.spector.core.cognitive.EdgeImportanceKernel;
import com.spectrayan.spector.core.cognitive.MassDilatedDecayKernel;
import com.spectrayan.spector.core.cognitive.PowerLawDecayKernel;
import com.spectrayan.spector.core.graph.GraphCentralityKernel;
import com.spectrayan.spector.core.math.SigmoidKernel;
import com.spectrayan.spector.core.math.SoftmaxKernel;
import com.spectrayan.spector.core.similarity.BM25Kernel;

/**
 * ADR-0033 Principle 3 + Principle 6: batch/scalar equivalence for every struct-of-arrays seam.
 *
 * <p>Each batch kernel MUST produce bit-identical results to a loop over its per-record
 * counterpart. Divergence between the two paths is the specific failure mode the batch seams
 * introduce, so every mandated batch form is covered here — including the null-substitution and
 * short-array degradation branches, which are the parts most likely to drift silently.</p>
 */
@DisplayName("ADR-0033 Principle 3: Batch vs Scalar Kernel Parity")
class BatchScalarParityTest {

    /** Batch forms must be bit-identical to the scalar loop, not merely close. */
    private static final float EXACT = 0.0f;

    private static final int N = 257; // deliberately not a multiple of any SIMD lane width
    private static final long NOW_MS = 1_760_000_000_000L;

    /** Fixed seed so every parity failure is reproducible. */
    private static final long SEED = 33L;

    private static Random rng() {
        return new Random(SEED);
    }

    // ─────────────────────── 1. CognitiveScoreFusionKernel ───────────────────────

    @Nested
    @DisplayName("CognitiveScoreFusionKernel")
    class Fusion {

        private final float[] l2dists = new float[N];
        private final long[] timestamps = new long[N];
        private final float[] masses = new float[N];
        private final byte[] arousals = new byte[N];
        private final float[] storages = new float[N];
        private final boolean[] hasStorage = new boolean[N];
        private final int[] recallCounts = new int[N];
        private final float[] importances = new float[N];
        private final float[] tagOverlaps = new float[N];
        private final byte[] valences = new byte[N];
        private final boolean[] focusMatches = new boolean[N];
        private final boolean[] zeroTimeDecays = new boolean[N];
        private final float[] priors = new float[N];

        Fusion() {
            Random r = rng();
            for (int i = 0; i < N; i++) {
                l2dists[i] = r.nextFloat() * 4.0f;
                timestamps[i] = NOW_MS - (long) (r.nextDouble() * 3L * 365 * 86_400_000L);
                masses[i] = r.nextFloat() * 5.0f;
                arousals[i] = (byte) r.nextInt(256);
                storages[i] = 1.0f + r.nextFloat() * 4.0f;
                hasStorage[i] = r.nextBoolean();
                recallCounts[i] = r.nextInt(15);
                importances[i] = r.nextFloat() * 10.0f;
                tagOverlaps[i] = r.nextFloat();
                valences[i] = (byte) (r.nextInt(255) - 128);
                focusMatches[i] = r.nextBoolean();
                zeroTimeDecays[i] = r.nextInt(10) == 0;
                priors[i] = r.nextFloat();
            }
        }

        private float[] scalarReference(FusionParams p, byte queryValence) {
            float[] out = new float[N];
            for (int i = 0; i < N; i++) {
                out[i] = CognitiveScoreFusionKernel.computeFusedScore(
                        l2dists[i], timestamps[i], NOW_MS, masses[i], arousals[i],
                        storages[i], hasStorage[i], recallCounts[i], importances[i],
                        tagOverlaps[i], valences[i], queryValence,
                        focusMatches[i], zeroTimeDecays[i], priors[i], p);
            }
            return out;
        }

        private void assertParity(FusionParams p, String label) {
            final byte queryValence = 40;
            float[] batch = new float[N];
            CognitiveScoreFusionKernel.computeFusedScores(
                    l2dists, timestamps, masses, arousals, storages, hasStorage,
                    recallCounts, importances, tagOverlaps, valences, focusMatches,
                    zeroTimeDecays, priors, NOW_MS, queryValence, p, batch, N);

            ParityHarness.assertBatchMatchesScalar(batch, scalarReference(p, queryValence), EXACT, label);
        }

        @Test
        @DisplayName("fast path (all arrays present) matches scalar exactly — MULTIPLICATIVE")
        void fastPathMultiplicative() {
            assertParity(FusionParams.DEFAULT, "fusion MULTIPLICATIVE fast path");
        }

        @Test
        @DisplayName("fast path matches scalar exactly — ADDITIVE")
        void fastPathAdditive() {
            FusionParams p = new FusionParams(1.0f, 0.5f, 0.7f, 0.3f, 0.2f, 1.5f, 0.3f, 1.0f,
                    true, true, false, false);
            assertParity(p, "fusion ADDITIVE fast path");
        }

        @Test
        @DisplayName("fast path matches scalar exactly — valenceAlign + twoFactor disabled")
        void fastPathValenceAlignNoTwoFactor() {
            FusionParams p = new FusionParams(1.2f, 0.8f, 0.6f, 0.3f, 0.25f, 2.0f, 0.4f, 0.3f,
                    false, false, false, true);
            assertParity(p, "fusion valenceAlign, twoFactor off");
        }

        @Test
        @DisplayName("pureSimilarity short-circuit matches scalar exactly")
        void pureSimilarityPath() {
            FusionParams p = new FusionParams(1.3f, 0.5f, 0.7f, 0.3f, 0.2f, 1.5f, 0.3f, 1.0f,
                    false, true, true, false);
            assertParity(p, "fusion pureSimilarity");
        }

        @Test
        @DisplayName("slow path (null optional arrays) matches scalar with substituted defaults")
        void slowPathNullArraysUseDocumentedDefaults() {
            final byte queryValence = 0;
            final FusionParams p = FusionParams.DEFAULT;

            float[] batch = new float[N];
            CognitiveScoreFusionKernel.computeFusedScores(
                    l2dists, timestamps, masses, arousals, storages, /* hasStorageStrength */ null,
                    recallCounts, importances, tagOverlaps, valences, /* focusMatches */ null,
                    /* zeroTimeDecays */ null, /* associativePriors */ null,
                    NOW_MS, queryValence, p, batch, N);

            float[] expected = new float[N];
            for (int i = 0; i < N; i++) {
                expected[i] = CognitiveScoreFusionKernel.computeFusedScore(
                        l2dists[i], timestamps[i], NOW_MS, masses[i], arousals[i],
                        storages[i], false, recallCounts[i], importances[i],
                        tagOverlaps[i], valences[i], queryValence,
                        false, false, 0.0f, p);
            }
            ParityHarness.assertBatchMatchesScalar(batch, expected, EXACT, "fusion slow path null defaults");
        }

        @Test
        @DisplayName("count larger than outScores length is clamped, not thrown")
        void oversizedCountIsClamped() {
            float[] batch = new float[8];
            CognitiveScoreFusionKernel.computeFusedScores(
                    l2dists, timestamps, masses, arousals, storages, hasStorage,
                    recallCounts, importances, tagOverlaps, valences, focusMatches,
                    zeroTimeDecays, priors, NOW_MS, (byte) 0, FusionParams.DEFAULT, batch, N);

            float[] scalar = scalarReference(FusionParams.DEFAULT, (byte) 0);
            for (int i = 0; i < batch.length; i++) {
                ParityHarness.assertBitExact(batch[i], scalar[i], "clamped fusion[" + i + "]");
            }
        }

        @Test
        @DisplayName("null params falls back to FusionParams.DEFAULT")
        void nullParamsUsesDefault() {
            float[] withNull = new float[N];
            float[] withDefault = new float[N];
            CognitiveScoreFusionKernel.computeFusedScores(
                    l2dists, timestamps, masses, arousals, storages, hasStorage, recallCounts,
                    importances, tagOverlaps, valences, focusMatches, zeroTimeDecays, priors,
                    NOW_MS, (byte) 0, null, withNull, N);
            CognitiveScoreFusionKernel.computeFusedScores(
                    l2dists, timestamps, masses, arousals, storages, hasStorage, recallCounts,
                    importances, tagOverlaps, valences, focusMatches, zeroTimeDecays, priors,
                    NOW_MS, (byte) 0, FusionParams.DEFAULT, withDefault, N);

            ParityHarness.assertBatchMatchesScalar(withNull, withDefault, EXACT, "null FusionParams -> DEFAULT");
        }
    }

    // ─────────────────────── 2. Linear-blend fusion variant ───────────────────────

    @Test
    @DisplayName("computeLinearBlendScores matches scalar computeLinearBlendScore exactly")
    void linearBlendBatchMatchesScalar() {
        Random r = rng();
        float[] sims = new float[N];
        float[] importances = new float[N];
        float[] decays = new float[N];
        float[] tagOverlaps = new float[N];
        for (int i = 0; i < N; i++) {
            sims[i] = r.nextFloat();
            importances[i] = r.nextFloat() * 10.0f;
            decays[i] = r.nextFloat();
            tagOverlaps[i] = r.nextFloat();
        }
        final float alpha = 0.7f;
        final float beta = 0.4f;
        final float tagBoost = 0.25f;

        float[] batch = new float[N];
        CognitiveScoreFusionKernel.computeLinearBlendScores(
                sims, importances, decays, tagOverlaps, alpha, beta, tagBoost, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = CognitiveScoreFusionKernel.computeLinearBlendScore(
                    sims[i], importances[i], decays[i], tagOverlaps[i], alpha, beta, tagBoost);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "linear-blend fusion batch");
    }

    @Test
    @DisplayName("computeLinearBlendScore reproduces the pre-migration SemanticRecallStrategy expression")
    void linearBlendMatchesPreMigrationSemanticRecall() {
        Random r = rng();
        final float alpha = 0.65f;
        final float beta = 0.35f;
        final float tagBoost = 0.2f;
        for (int i = 0; i < 500; i++) {
            float similarity = r.nextFloat();
            float importance = r.nextFloat() * 10.0f;
            float decay = r.nextFloat();
            float tagOverlap = r.nextFloat();

            // Pre-migration inline expression, verbatim.
            float baseScore = alpha * similarity + beta * (importance / 10.0f) * decay;
            float expected = baseScore * (1.0f + tagOverlap * tagBoost);

            float actual = CognitiveScoreFusionKernel.computeLinearBlendScore(
                    similarity, importance, decay, tagOverlap, alpha, beta, tagBoost);
            ParityHarness.assertBitExact(actual, expected, "linear-blend vs pre-migration inline");
        }
    }

    @Test
    @DisplayName("linear-blend is NOT equal to the 6-phase fusion — they are distinct formulas")
    void linearBlendIsDistinctFromSixPhase() {
        float linear = CognitiveScoreFusionKernel.computeLinearBlendScore(
                0.8f, 7.0f, 0.6f, 0.4f, 0.7f, 0.5f, 0.2f);
        float sixPhase = CognitiveScoreFusionKernel.computeFusedScore(
                0.25f, NOW_MS - 86_400_000L, NOW_MS, 2.0f, (byte) 100, 2.0f, true,
                3, 7.0f, 0.4f, (byte) 10, (byte) 10, false, false, 0.0f, FusionParams.DEFAULT);
        assertThat(linear)
                .as("Guard against a future 'unification' silently collapsing the two formulas")
                .isNotEqualTo(sixPhase);
    }

    // ─────────────────────── 3. CognitiveMassKernel ───────────────────────

    @Test
    @DisplayName("computeMassBatch matches scalar computeMass exactly")
    void cognitiveMassBatchMatchesScalar() {
        Random r = rng();
        float[] importances = new float[N];
        byte[] arousals = new byte[N];
        float[] storages = new float[N];
        for (int i = 0; i < N; i++) {
            importances[i] = r.nextFloat() * 10.0f;
            arousals[i] = (byte) r.nextInt(256);
            storages[i] = 1.0f + r.nextFloat() * 4.0f;
        }

        float[] batch = new float[N];
        CognitiveMassKernel.computeMassBatch(importances, arousals, storages, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = CognitiveMassKernel.computeMass(importances[i], arousals[i], storages[i]);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "cognitive mass batch");
    }

    // ─────────────────────── 4. PowerLawDecayKernel ───────────────────────

    @Test
    @DisplayName("computeDecayBatch matches scalar computeDecayWithArousal exactly")
    void powerLawDecayBatchMatchesScalar() {
        Random r = rng();
        final float[] buckets = PowerLawDecayKernel.computeBuckets(0.15f, 0.10f);
        long[] timestamps = new long[N];
        int[] recallCounts = new int[N];
        byte[] arousals = new byte[N];
        for (int i = 0; i < N; i++) {
            timestamps[i] = NOW_MS - (long) (r.nextDouble() * 6L * 365 * 86_400_000L);
            recallCounts[i] = r.nextInt(12);
            arousals[i] = (byte) r.nextInt(256);
        }

        float[] batch = new float[N];
        PowerLawDecayKernel.computeDecayBatch(timestamps, recallCounts, arousals, NOW_MS, buckets, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = PowerLawDecayKernel.computeDecayWithArousal(
                    timestamps[i], NOW_MS, recallCounts[i], arousals[i], buckets);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "power-law decay batch");
    }

    // ─────────────────────── 5. MassDilatedDecayKernel ───────────────────────

    @Test
    @DisplayName("computeBatch matches scalar compute exactly (all arrays present)")
    void massDilatedDecayBatchMatchesScalar() {
        Random r = rng();
        long[] timestamps = new long[N];
        float[] masses = new float[N];
        byte[] arousals = new byte[N];
        int[] recallCounts = new int[N];
        boolean[] zeroTimeDecays = new boolean[N];
        for (int i = 0; i < N; i++) {
            timestamps[i] = NOW_MS - (long) (r.nextDouble() * 4L * 365 * 86_400_000L);
            masses[i] = r.nextFloat() * 6.0f;
            arousals[i] = (byte) r.nextInt(256);
            recallCounts[i] = r.nextInt(15);
            zeroTimeDecays[i] = r.nextInt(8) == 0;
        }
        final float lambda = 1.0f;

        float[] batch = new float[N];
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, arousals, recallCounts, zeroTimeDecays, NOW_MS, lambda, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = MassDilatedDecayKernel.compute(
                    timestamps[i], NOW_MS, masses[i], arousals[i], recallCounts[i], zeroTimeDecays[i], lambda);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "mass-dilated decay batch");
    }

    @Test
    @DisplayName("computeBatch null arousals/recallCounts/zeroTimeDecays substitute neutral defaults")
    void massDilatedDecayBatchNullsSubstituteNeutralDefaults() {
        Random r = rng();
        long[] timestamps = new long[N];
        float[] masses = new float[N];
        for (int i = 0; i < N; i++) {
            timestamps[i] = NOW_MS - (long) (r.nextDouble() * 4L * 365 * 86_400_000L);
            masses[i] = r.nextFloat() * 6.0f;
        }
        final float lambda = 1.0f;

        float[] withNulls = new float[N];
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, null, null, null, NOW_MS, lambda, withNulls, N);

        // Explicit zero-filled arrays must give an identical result — this is the substitution
        // contract SemanticRecallStrategy relies on to avoid allocating throwaway arrays.
        float[] withZeroArrays = new float[N];
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, new byte[N], new int[N], new boolean[N],
                NOW_MS, lambda, withZeroArrays, N);

        ParityHarness.assertBatchMatchesScalar(
                withNulls, withZeroArrays, EXACT, "mass-dilated decay null-substitution");
    }

    @Test
    @DisplayName("computeBatch tolerates short optional arrays by falling back to defaults")
    void massDilatedDecayBatchShortArraysDoNotThrow() {
        long[] timestamps = new long[N];
        float[] masses = new float[N];
        java.util.Arrays.fill(timestamps, NOW_MS - 86_400_000L);
        java.util.Arrays.fill(masses, 1.0f);

        float[] out = new float[N];
        // arousals deliberately too short — must degrade, not throw.
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, new byte[3], null, null, NOW_MS, 1.0f, out, N);

        float[] expected = new float[N];
        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, null, null, null, NOW_MS, 1.0f, expected, N);
        ParityHarness.assertBatchMatchesScalar(out, expected, EXACT, "short optional array degradation");
    }

    // ─────────────────────── 6. ActRActivationKernel ───────────────────────

    @Test
    @DisplayName("computeBucketActivations matches scalar computeBucketActivation exactly")
    void actRBatchMatchesScalar() {
        Random r = rng();
        final float[] buckets = PowerLawDecayKernel.computeBuckets(0.15f, 0.10f);
        final int slots = 8;
        int[][] relativeSeconds = new int[N][slots];
        long[] creationMs = new long[N];
        for (int i = 0; i < N; i++) {
            creationMs[i] = NOW_MS - (long) (r.nextDouble() * 365L * 86_400_000L);
            for (int s = 0; s < slots; s++) {
                // Mix populated and empty (0) slots to exercise the empty-slot contract.
                relativeSeconds[i][s] = r.nextInt(4) == 0 ? 0 : r.nextInt(3_000_000);
            }
        }

        float[] batch = new float[N];
        ActRActivationKernel.computeBucketActivations(relativeSeconds, creationMs, NOW_MS, buckets, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = ActRActivationKernel.computeBucketActivation(
                    relativeSeconds[i], creationMs[i], NOW_MS, buckets);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "ACT-R bucket activation batch");
    }

    @Test
    @DisplayName("all-empty ring buffer returns the -1.0f no-history sentinel in batch form too")
    void actRBatchPreservesSentinel() {
        final float[] buckets = PowerLawDecayKernel.computeBuckets(0.15f, 0.10f);
        int[][] allEmpty = new int[4][8]; // every slot 0 == empty
        long[] creationMs = {NOW_MS - 1000L, NOW_MS - 2000L, NOW_MS - 3000L, NOW_MS - 4000L};

        float[] out = new float[4];
        ActRActivationKernel.computeBucketActivations(allEmpty, creationMs, NOW_MS, buckets, out, 4);

        assertThat(out)
                .as("The -1.0f sentinel drives the DecayStrategy fallback branch and must survive batching")
                .containsOnly(-1.0f);
    }

    // ─────────────────────── 7. EdgeImportanceKernel ───────────────────────

    @Test
    @DisplayName("scoreBatch (9-signal) matches scalar score exactly")
    void edgeImportanceScoreBatchMatchesScalar() {
        Random r = rng();
        final int currentCycle = 5000;
        float[] weights = new float[N];
        int[] lastCycles = new int[N];
        int[] bridgeScores = new int[N];
        int[] sharedNeighbors = new int[N];
        float[] impA = new float[N];
        float[] impB = new float[N];
        byte[] arA = new byte[N];
        byte[] arB = new byte[N];
        byte[] vaA = new byte[N];
        byte[] vaB = new byte[N];
        float[] stA = new float[N];
        float[] stB = new float[N];
        boolean[] protA = new boolean[N];
        boolean[] protB = new boolean[N];
        for (int i = 0; i < N; i++) {
            weights[i] = r.nextFloat() * 10.0f;
            lastCycles[i] = currentCycle - r.nextInt(500);
            bridgeScores[i] = r.nextInt(256);
            sharedNeighbors[i] = r.nextInt(20);
            impA[i] = r.nextFloat() * 10.0f;
            impB[i] = r.nextFloat() * 10.0f;
            arA[i] = (byte) r.nextInt(256);
            arB[i] = (byte) r.nextInt(256);
            vaA[i] = (byte) (r.nextInt(255) - 128);
            vaB[i] = (byte) (r.nextInt(255) - 128);
            stA[i] = 1.0f + r.nextFloat() * 4.0f;
            stB[i] = 1.0f + r.nextFloat() * 4.0f;
            protA[i] = r.nextBoolean();
            protB[i] = r.nextBoolean();
        }
        final float[] w = null; // exercise the DEFAULT_WEIGHTS fallback

        float[] batch = new float[N];
        EdgeImportanceKernel.scoreBatch(weights, currentCycle, lastCycles, bridgeScores, sharedNeighbors,
                impA, impB, arA, arB, vaA, vaB, stA, stB, protA, protB, w, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = EdgeImportanceKernel.score(
                    weights[i], currentCycle, lastCycles[i], bridgeScores[i], sharedNeighbors[i],
                    impA[i], impB[i], arA[i], arB[i], vaA[i], vaB[i], stA[i], stB[i],
                    protA[i], protB[i], w);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "edge importance 9-signal batch");
    }

    @Test
    @DisplayName("scoreStructuralBatch matches the exact scalar call the hot eviction path makes")
    void edgeImportanceStructuralBatchMatchesHotPathScalarCall() {
        Random r = rng();
        final int currentCycle = 40_000;
        short[] lastCycles = new short[N];
        byte[] bridgeScores = new byte[N];
        float[] weights = new float[N];
        for (int i = 0; i < N; i++) {
            weights[i] = r.nextFloat() * 10.0f;
            lastCycles[i] = (short) r.nextInt(65_536);
            bridgeScores[i] = (byte) r.nextInt(256);
        }

        float[] batch = new float[N];
        EdgeImportanceKernel.scoreStructuralBatch(
                weights, currentCycle, lastCycles, bridgeScores, null, null, batch, N);

        // Mirrors HebbianGraph/HebbianGraphMemory.replaceLowestImportance verbatim, including the
        // unsigned widening of the on-disk short/byte fields and sharedNeighbors == 0.
        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = EdgeImportanceKernel.scoreStructural(
                    weights[i], currentCycle,
                    Short.toUnsignedInt(lastCycles[i]),
                    Byte.toUnsignedInt(bridgeScores[i]),
                    0, null);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "edge importance structural batch");
    }

    // ─────────────────────── 8. BM25Kernel ───────────────────────

    @Test
    @DisplayName("scoreTerms matches scalar scoreTerm exactly")
    void bm25BatchMatchesScalar() {
        Random r = rng();
        int[] tfs = new int[N];
        int[] docLens = new int[N];
        for (int i = 0; i < N; i++) {
            tfs[i] = 1 + r.nextInt(30);
            docLens[i] = 10 + r.nextInt(4000);
        }
        final float avgDocLen = 512.0f;
        final float k1 = 1.2f;
        final float b = 0.75f;
        final float idf = BM25Kernel.idf(37, 10_000);

        float[] batch = new float[N];
        BM25Kernel.scoreTerms(tfs, docLens, avgDocLen, k1, b, idf, batch, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = BM25Kernel.scoreTerm(tfs[i], docLens[i], avgDocLen, k1, b, idf);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "BM25 term scoring batch");
    }

    @Test
    @DisplayName("scoreTerms honours the scalar idf <= 0 guard (regression: batch dropped it)")
    void bm25BatchHonoursNonPositiveIdfGuard() {
        int[] tfs = {5, 12, 3};
        int[] docLens = {100, 500, 250};

        for (float degenerateIdf : new float[]{0.0f, -0.5f}) {
            float[] batch = new float[3];
            BM25Kernel.scoreTerms(tfs, docLens, 512.0f, 1.2f, 0.75f, degenerateIdf, batch, 3);

            float[] scalar = new float[3];
            for (int i = 0; i < 3; i++) {
                scalar[i] = BM25Kernel.scoreTerm(tfs[i], docLens[i], 512.0f, 1.2f, 0.75f, degenerateIdf);
            }
            ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "BM25 idf=" + degenerateIdf);
            assertThat(batch).as("non-positive IDF must contribute nothing").containsOnly(0.0f);
        }
    }

    @Test
    @DisplayName("scoreTerms zero/negative term frequencies score 0 in both paths")
    void bm25BatchHandlesZeroTermFrequency() {
        int[] tfs = {0, 7, -3, 2};
        int[] docLens = {100, 500, 250, 800};
        final float idfValue = BM25Kernel.idf(37, 10_000);

        float[] batch = new float[4];
        BM25Kernel.scoreTerms(tfs, docLens, 512.0f, 1.2f, 0.75f, idfValue, batch, 4);

        float[] scalar = new float[4];
        for (int i = 0; i < 4; i++) {
            scalar[i] = BM25Kernel.scoreTerm(tfs[i], docLens[i], 512.0f, 1.2f, 0.75f, idfValue);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "BM25 zero/negative tf");
        assertThat(batch[0]).isZero();
        assertThat(batch[2]).isZero();
    }

    @Test
    @DisplayName("indexed scoreTerms overload also matches scalar exactly")
    void bm25IndexedOverloadMatchesScalar() {
        Random r = rng();
        final int docs = 64;
        int[] docLens = new int[docs];
        for (int i = 0; i < docs; i++) {
            docLens[i] = 10 + r.nextInt(4000);
        }
        int[] tfs = new int[N];
        int[] docIndices = new int[N];
        for (int i = 0; i < N; i++) {
            tfs[i] = 1 + r.nextInt(30);
            docIndices[i] = r.nextInt(docs);
        }
        final float avgDocLen = 512.0f;
        final float k1 = 1.2f;
        final float b = 0.75f;
        final float idfValue = BM25Kernel.idf(37, 10_000);

        float[] batch = new float[N];
        BM25Kernel.scoreTerms(tfs, 0, docIndices, 0, docLens, avgDocLen, k1, b, idfValue, batch, 0, N);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = BM25Kernel.scoreTerm(tfs[i], docLens[docIndices[i]], avgDocLen, k1, b, idfValue);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "BM25 indexed batch overload");
    }

    // ─────────────────────── 9. SigmoidKernel ───────────────────────

    @Test
    @DisplayName("sigmoidBatch matches scalar sigmoid exactly")
    void sigmoidBatchMatchesScalar() {
        Random r = rng();
        float[] src = new float[N];
        for (int i = 0; i < N; i++) {
            src[i] = (r.nextFloat() - 0.5f) * 40.0f; // spans the saturating tails
        }

        float[] batch = new float[N];
        SigmoidKernel.sigmoidBatch(src, batch);

        float[] scalar = new float[N];
        for (int i = 0; i < N; i++) {
            scalar[i] = SigmoidKernel.sigmoid(src[i]);
        }
        ParityHarness.assertBatchMatchesScalar(batch, scalar, EXACT, "sigmoid batch");
    }

    // ─────────────────────── 10. SoftmaxKernel ───────────────────────

    @Test
    @DisplayName("applySoftmaxTemperature with a caller-supplied scratch buffer matches the allocating form")
    void softmaxScratchOverloadMatchesAllocatingForm() {
        Random r = rng();
        float[] a = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = r.nextFloat() * 10.0f;
        }
        float[] b = a.clone();

        SoftmaxKernel.applySoftmaxTemperature(a, 0.4f);
        SoftmaxKernel.applySoftmaxTemperature(b, 0.4f, new float[N]);

        ParityHarness.assertBatchMatchesScalar(b, a, EXACT, "softmax scratch overload");
    }

    @Test
    @DisplayName("a scratch buffer that is too short is silently replaced, not read out of bounds")
    void softmaxUndersizedScratchIsHandled() {
        Random r = rng();
        float[] a = new float[N];
        for (int i = 0; i < N; i++) {
            a[i] = r.nextFloat() * 10.0f;
        }
        float[] b = a.clone();

        SoftmaxKernel.applySoftmaxTemperature(a, 2.5f);
        SoftmaxKernel.applySoftmaxTemperature(b, 2.5f, new float[3]);

        ParityHarness.assertBatchMatchesScalar(b, a, EXACT, "softmax undersized scratch");
    }

    @Test
    @DisplayName("computeProbabilities and computeProbabilitiesScaled agree when beta == 1/T")
    void softmaxTemperatureAndBetaFormsAgree() {
        Random r = rng();
        float[] scores = new float[N];
        for (int i = 0; i < N; i++) {
            scores[i] = (r.nextFloat() - 0.5f) * 6.0f;
        }
        final float temperature = 0.8f;

        float[] viaTemperature = new float[N];
        float[] viaBeta = new float[N];
        SoftmaxKernel.computeProbabilities(scores, temperature, viaTemperature);
        SoftmaxKernel.computeProbabilitiesScaled(scores, 1.0f / temperature, viaBeta);

        ParityHarness.assertBatchMatchesScalar(viaBeta, viaTemperature, EXACT,
                "softmax 1/T vs beta parameterisation");
    }

    // ─────────────────────── 11. GraphCentralityKernel (T3 determinism) ───────────────────────

    @Nested
    @DisplayName("GraphCentralityKernel T3 determinism")
    class WilsonDeterminism {

        private int[][] ringWithChords(int nodes) {
            Random r = new Random(7L);
            int[][] adjacency = new int[nodes][];
            for (int i = 0; i < nodes; i++) {
                int chord = r.nextInt(nodes);
                adjacency[i] = new int[]{(i + 1) % nodes, (i - 1 + nodes) % nodes, chord};
            }
            return adjacency;
        }

        @Test
        @DisplayName("identical seed produces bit-identical bridge scores")
        void sameSeedIsReproducible() {
            int[][] adjacency = ringWithChords(64);
            int[][] first = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 64, 12, 5_000, 99L);
            int[][] second = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 64, 12, 5_000, 99L);

            assertThat(second)
                    .as("Purity Tier T3 requires determinism given a seed — this is the whole contract")
                    .isDeepEqualTo(first);
        }

        @Test
        @DisplayName("different seeds explore different spanning trees")
        void differentSeedsDiverge() {
            int[][] adjacency = ringWithChords(64);
            int[][] a = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 64, 12, 5_000, 1L);
            int[][] b = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 64, 12, 5_000, 2L);

            assertThat(java.util.Arrays.deepToString(b))
                    .as("A seeded sampler that ignores its seed would silently produce identical output")
                    .isNotEqualTo(java.util.Arrays.deepToString(a));
        }

        @Test
        @DisplayName("all scores stay within the [0, 255] byte-encodable range")
        void scoresAreRangeBounded() {
            int[][] adjacency = ringWithChords(48);
            int[][] scores = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 48, 10, 5_000, 42L);
            for (int[] nodeScores : scores) {
                for (int s : nodeScores) {
                    assertThat(s).isBetween(0, 255);
                }
            }
        }

        @Test
        @DisplayName("no wall-clock dependency: repeated runs across a time gap are identical")
        void noWallClockDependency() throws InterruptedException {
            int[][] adjacency = ringWithChords(32);
            int[][] before = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 32, 8, 2_000, 5L);
            Thread.sleep(15);
            int[][] after = GraphCentralityKernel.computeWilsonBridgeScores(adjacency, 32, 8, 2_000, 5L);

            assertThat(after)
                    .as("A budgetMs-style wall-clock bound would make this flaky — it must not exist")
                    .isDeepEqualTo(before);
        }
    }
}
