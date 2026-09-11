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

import com.spectrayan.spector.core.cognitive.CognitiveMassKernel;
import com.spectrayan.spector.core.cognitive.CognitiveScoreFusionKernel;
import com.spectrayan.spector.core.cognitive.EdgeImportanceKernel;
import com.spectrayan.spector.core.cognitive.MassDilatedDecayKernel;
import com.spectrayan.spector.core.cognitive.PowerLawDecayKernel;
import com.spectrayan.spector.core.math.EmaTracker;
import com.spectrayan.spector.core.math.SigmoidKernel;
import com.spectrayan.spector.core.math.SoftmaxKernel;
import com.spectrayan.spector.core.math.WelfordAccumulator;
import com.spectrayan.spector.core.similarity.BM25Kernel;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.core.similarity.DotProduct;
import com.spectrayan.spector.core.similarity.VectorOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ADR-0033 Principle 6: Behavioural Parity Verification.
 *
 * <p>Validates that every migrated kernel in {@code spector-core} maintains bit-exact
 * or documented epsilon equivalence against its pre-migration baseline implementation.</p>
 */
@DisplayName("Kernel Parity Harness Suite (Principle 6)")
class KernelParityTest {

    private static final float EPSILON = 1e-6f;
    private static final float SIMD_EPSILON = 1e-4f; // Accounts for FMA vs separate mul/add ordering

    // ── 1. CosineSimilarity Parity ──

    @Test
    @DisplayName("CosineSimilarity: SIMD compute matches scalar float baseline within SIMD epsilon")
    void cosineSimilarityMatchesScalarFloatBaseline() {
        Random rnd = new Random(42);
        int dim = 128;
        float[] a = new float[dim];
        float[] b = new float[dim];
        for (int i = 0; i < dim; i++) {
            a[i] = rnd.nextFloat() * 2.0f - 1.0f;
            b[i] = rnd.nextFloat() * 2.0f - 1.0f;
        }

        // Pre-migration scalar float baseline
        float dot = 0.0f, normA = 0.0f, normB = 0.0f;
        for (int i = 0; i < dim; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float expected = (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));

        float actual = CosineSimilarity.compute(a, b);
        ParityHarness.assertWithinEpsilon(actual, expected, SIMD_EPSILON, "CosineSimilarity SIMD vs scalar float");
    }

    @Test
    @DisplayName("CosineSimilarity: computeDouble matches pre-migration DenseDerivedSparseProvider double baseline exactly")
    void cosineSimilarityDoubleMatchesPreMigrationSparseProvider() {
        Random rnd = new Random(1337);
        int dim = 384;
        float[] a = new float[dim];
        float[] b = new float[dim];
        for (int i = 0; i < dim; i++) {
            a[i] = rnd.nextFloat() * 2.0f - 1.0f;
            b[i] = rnd.nextFloat() * 2.0f - 1.0f;
        }

        // Pre-migration DenseDerivedSparseProvider logic
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            norm1 += a[i] * a[i];
            norm2 += b[i] * b[i];
        }
        float expected = norm1 > 0 && norm2 > 0 ? (float) (dot / (Math.sqrt(norm1) * Math.sqrt(norm2))) : 0.0f;

        float actual = CosineSimilarity.computeDouble(a, b);
        ParityHarness.assertBitExact(actual, expected, "CosineSimilarity.computeDouble vs pre-migration SparseProvider");
    }

    @Test
    @DisplayName("CosineSimilarity: computeSafe handles length mismatch without throwing")
    void cosineSimilaritySafeLengthMismatch() {
        float[] a = new float[]{1.0f, 2.0f, 3.0f};
        float[] b = new float[]{1.0f, 2.0f};
        assertEquals(0.0f, CosineSimilarity.computeSafe(a, b), EPSILON);
        assertEquals(0.0f, CosineSimilarity.computeSafeDouble(a, b), EPSILON);
    }

    // ── 2. DotProduct Parity ──

    @Test
    @DisplayName("DotProduct: compute matches scalar baseline")
    void dotProductMatchesScalarBaseline() {
        Random rnd = new Random(101);
        int dim = 256;
        float[] a = new float[dim];
        float[] b = new float[dim];
        float expected = 0.0f;
        for (int i = 0; i < dim; i++) {
            a[i] = rnd.nextFloat();
            b[i] = rnd.nextFloat();
            expected += a[i] * b[i];
        }

        float actual = DotProduct.compute(a, b);
        ParityHarness.assertWithinEpsilon(actual, expected, SIMD_EPSILON, "DotProduct SIMD vs scalar");
    }

    @Test
    @DisplayName("DotProduct: computeTruncated matches pre-migration VectorSpaceProjectionService truncation")
    void dotProductTruncatedMatchesPreMigrationProjectionService() {
        float[] a = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] b = new float[]{2.0f, 3.0f}; // shorter length

        // Pre-migration VectorSpaceProjectionService truncation
        float expected = 0.0f;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            expected += a[i] * b[i];
        }

        float actual = DotProduct.computeTruncated(a, b);
        ParityHarness.assertWithinEpsilon(actual, expected, EPSILON, "DotProduct.computeTruncated vs pre-migration projection");
    }

    // ── 3. SoftmaxKernel Parity ──

    @Test
    @DisplayName("SoftmaxKernel: applySoftmaxTemperature matches pre-migration TemperatureSoftmax formula")
    void softmaxTemperatureMatchesPreMigrationFormula() {
        float[] scores = new float[]{0.85f, 0.72f, 0.45f, 0.12f};
        float[] originalScores = scores.clone();
        float temp = 0.7f;

        // Pre-migration TemperatureSoftmax logic
        int n = originalScores.length;
        float maxScaled = -Float.MAX_VALUE;
        float totalOriginalScore = 0.0f;
        for (int i = 0; i < n; i++) {
            float s = originalScores[i];
            totalOriginalScore += s;
            float scaled = s / temp;
            if (scaled > maxScaled) {
                maxScaled = scaled;
            }
        }
        double sumExp = 0.0;
        double[] expWeights = new double[n];
        for (int i = 0; i < n; i++) {
            float scaled = originalScores[i] / temp;
            double w = Math.exp(scaled - maxScaled);
            expWeights[i] = w;
            sumExp += w;
        }
        float[] expected = new float[n];
        double scaleMultiplier = totalOriginalScore > 0 ? totalOriginalScore : 1.0;
        for (int i = 0; i < n; i++) {
            expected[i] = (float) ((expWeights[i] / sumExp) * scaleMultiplier);
        }

        SoftmaxKernel.applySoftmaxTemperature(scores, temp);

        for (int i = 0; i < n; i++) {
            ParityHarness.assertWithinEpsilon(scores[i], expected[i], EPSILON, "TemperatureSoftmax parity at [" + i + "]");
        }
    }

    @Test
    @DisplayName("SoftmaxKernel: degenerate input leaves scores untouched (not flattened to 1/n)")
    void softmaxTemperaturePreservesUntouchedOnDegenerateInput() {
        float[] scores = new float[]{Float.NaN, 0.5f, 0.9f};
        float[] copy = scores.clone();

        SoftmaxKernel.applySoftmaxTemperature(scores, 0.5f);

        // Assert scores are preserved untouched rather than flattened
        assertThat(scores).containsExactly(copy);
    }

    // ── 4. BM25Kernel Parity ──

    @Test
    @DisplayName("BM25Kernel: scoreTerm matches pre-migration BM25Index formula")
    void bm25TermScoreMatchesPreMigrationBM25Index() {
        int tf = 3;
        int docLen = 120;
        float avgDocLen = 100.0f;
        float k1 = 1.2f;
        float b = 0.75f;
        float idf = 2.45f;

        // Pre-migration BM25Index formula
        float num = tf * (k1 + 1.0f);
        float denom = tf + k1 * (1.0f - b + b * (docLen / avgDocLen));
        float expected = idf * (num / denom);

        float actual = BM25Kernel.scoreTerm(tf, docLen, avgDocLen, k1, b, idf);
        ParityHarness.assertWithinEpsilon(actual, expected, EPSILON, "BM25 scoreTerm vs pre-migration BM25Index");
    }

    @Test
    @DisplayName("BM25Kernel: batch scoreTerms matches scalar scoreTerm")
    void bm25BatchMatchesScalar() {
        int[] tfs = new int[]{1, 3, 0, 5};
        int[] docLens = new int[]{80, 120, 50, 200};
        float avgDocLen = 100.0f;
        float k1 = 1.2f;
        float b = 0.75f;
        float idf = 1.8f;
        float[] batchOut = new float[4];

        BM25Kernel.scoreTerms(tfs, docLens, avgDocLen, k1, b, idf, batchOut, 4);

        for (int i = 0; i < 4; i++) {
            float scalar = BM25Kernel.scoreTerm(tfs[i], docLens[i], avgDocLen, k1, b, idf);
            ParityHarness.assertWithinEpsilon(batchOut[i], scalar, EPSILON, "BM25 scoreTerms batch vs scalar [" + i + "]");
        }
    }

    // ── 5. CognitiveMassKernel Parity ──

    @Test
    @DisplayName("CognitiveMassKernel: computeCognitiveMass matches pre-migration CognitiveMass formula")
    void cognitiveMassMatchesPreMigrationFormula() {
        float importance = 8.5f;
        byte arousal = (byte) 200;
        float storage = 2.5f;

        // Pre-migration CognitiveMass formula:
        final float importanceNorm = importance / 10.0f;
        final float arousalNorm = 1.0f + ((arousal & 0xFF) / 128.0f);
        final float expectedStorageBoost = (float) Math.pow(storage, 0.3f);
        final float expected = importanceNorm * arousalNorm * expectedStorageBoost;

        float actual = CognitiveMassKernel.computeMass(importance, arousal, storage);
        ParityHarness.assertWithinEpsilon(actual, expected, 0.05f, "CognitiveMass vs pre-migration formula");
    }

    // ── 6. MassDilatedDecayKernel Parity ──

    @Test
    @DisplayName("MassDilatedDecayKernel: compute matches pre-migration mass dilated decay formula")
    void massDilatedDecayMatchesPreMigrationFormula() {
        long timestampMs = 1_000_000L;
        long nowMs = 1_000_000L + (long) (5.0 * 86_400_000L); // 5 days later
        float mass = 12.5f;
        byte arousal = (byte) 100;
        int recallCount = 4;
        float lambda = 1.0f;

        // Pre-migration formula
        float arousalMod = 1.0f + 0.3f * (Byte.toUnsignedInt(arousal) / 255.0f);
        float reconBoost = 1.0f + 0.05f * Math.min(recallCount, 10);
        double elapsedDays = (nowMs - timestampMs) / 86_400_000.0;
        float dilatedDecay = 1.0f / (1.0f + (float) ((lambda * Math.log1p(elapsedDays)) / (1.0 + mass)));
        float expected = Math.min(1.0f, dilatedDecay * arousalMod * reconBoost);

        float actual = MassDilatedDecayKernel.compute(timestampMs, nowMs, mass, arousal, recallCount, false, lambda);
        ParityHarness.assertWithinEpsilon(actual, expected, EPSILON, "MassDilatedDecay vs pre-migration formula");
    }

    // ── 7. EdgeImportanceKernel Batch Parity ──

    @Test
    @DisplayName("EdgeImportanceKernel: scoreStructuralBatch matches scalar scoreStructural")
    void edgeImportanceStructuralBatchMatchesScalar() {
        float[] weights = new float[]{1.0f, 3.5f, 7.0f};
        short[] lastCycles = new short[]{5, 8, 12};
        byte[] bridgeScores = new byte[]{10, 50, (byte) 200};
        int[] shared = new int[]{0, 2, 5};
        int currentCycle = 15;
        float[] outScores = new float[3];

        EdgeImportanceKernel.scoreStructuralBatch(weights, currentCycle, lastCycles, bridgeScores, shared, null, outScores, 3);

        for (int i = 0; i < 3; i++) {
            float scalar = EdgeImportanceKernel.scoreStructural(
                    weights[i], currentCycle, Short.toUnsignedInt(lastCycles[i]),
                    Byte.toUnsignedInt(bridgeScores[i]), shared[i], null);
            ParityHarness.assertWithinEpsilon(outScores[i], scalar, EPSILON, "EdgeImportance structural batch vs scalar [" + i + "]");
        }
    }

    // ── 8. SigmoidKernel Parity ──

    @Test
    @DisplayName("SigmoidKernel: sigmoid matches textbook logistic formula")
    void sigmoidMatchesTextbookFormula() {
        float[] inputs = new float[]{-5.0f, -1.0f, 0.0f, 1.0f, 5.0f};
        for (float x : inputs) {
            float expected = (float) (1.0 / (1.0 + Math.exp(-x)));
            float actual = SigmoidKernel.sigmoid(x);
            ParityHarness.assertBitExact(actual, expected, "Sigmoid for x=" + x);
            assertEquals(actual, VectorOps.sigmoid(x), EPSILON);
        }
    }
}
