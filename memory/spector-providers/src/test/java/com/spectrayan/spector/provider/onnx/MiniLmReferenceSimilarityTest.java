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
package com.spectrayan.spector.provider.onnx;

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference cosines for the bundled int8 all-MiniLM-L6-v2.
 *
 * <p>Unrelated text sits near zero. A floor around 0.4 means the vector is not the
 * model's cosine (a fused recall score, or a corrupted int8 GEMM). When two embeds
 * of the same text disagree, the usual cause on Intel hosts is ONNX Runtime's AMX
 * tile state not surviving preemption; the session is opened with one intra-op thread
 * so that path is not the default.</p>
 */
class MiniLmReferenceSimilarityTest {

    private static final String CAKE = "chocolate lava cake";
    private static final String BUDGET = "the quarterly budget review was postponed";
    private static final String HORSE = "a horse galloped across the field";
    private static final String DATABASE = "the database migration failed at midnight";
    private static final String PARAPHRASE_A = "How do I bake a chocolate lava cake?";
    private static final String PARAPHRASE_B = "What is the recipe for a chocolate lava cake?";

    @Test
    void unrelatedTextStaysNearZeroAndParaphrasesStayHigh() throws Exception {
        EmbeddingProvider provider = bundled();
        float[] cake = provider.embed(CAKE).vector();
        float[] cakeAgain = provider.embed(CAKE).vector();
        assertBitIdentical(cake, cakeAgain, CAKE);

        float[] budget = provider.embed(BUDGET).vector();
        float[] horse = provider.embed(HORSE).vector();
        float[] database = provider.embed(DATABASE).vector();
        float[] recipeA = provider.embed(PARAPHRASE_A).vector();
        float[] recipeB = provider.embed(PARAPHRASE_B).vector();

        assertThat(cosine(cake, budget)).isBetween(-0.15, 0.15);
        assertThat(cosine(horse, database)).isBetween(-0.15, 0.15);
        assertThat(cosine(recipeA, recipeB)).isGreaterThan(0.70);
        assertThat(norm(cake)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1.0e-3));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<float[]>> pending = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                pending.add(pool.submit(() -> provider.embed(CAKE).vector()));
            }
            for (Future<float[]> future : pending) {
                assertBitIdentical(cake, future.get(), CAKE);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static EmbeddingProvider bundled() {
        return new OnnxProviderFactory()
                .createEmbeddingProvider(new ProviderConfig("onnx", "onnx", "", "", "", 0, Map.of()))
                .orElseThrow();
    }

    private static void assertBitIdentical(float[] expected, float[] actual, String text) {
        assertThat(actual)
                .as("repeated embed of \"%s\" differed; on Intel hosts this is ONNX Runtime AMX tile state "
                        + "being dropped when the thread is preempted", text)
                .containsExactly(expected);
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    private static double norm(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum);
    }
}
