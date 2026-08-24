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
package com.spectrayan.spector.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.index.text.BM25Index;
import com.spectrayan.spector.index.text.SIMDScoreAccumulator;
import com.spectrayan.spector.index.text.SpladeIndex;

import org.junit.jupiter.api.*;

import java.util.*;

/**
 * Performance benchmarks for SPLADE/BM25/SIMD retrieval components.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RetrievalBenchmarkTest {

    @Test
    @Order(1)
    @DisplayName("SIMD addArrays  --  100K elements under 1ms")
    void simd_addArrays_100K_under_1ms() {
        int n = 100_000;
        float[] dst = new float[n];
        float[] src = new float[n];
        Random rng = new Random(42);
        for (int i = 0; i < n; i++) {
            dst[i] = rng.nextFloat();
            src[i] = rng.nextFloat();
        }

        // Warm up
        for (int i = 0; i < 5_000; i++) {
            SIMDScoreAccumulator.addArrays(dst, src, n);
        }

        Arrays.fill(dst, 0f);
        long start = System.nanoTime();
        SIMDScoreAccumulator.addArrays(dst, src, n);
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed / 1_000_000).as("100K addArrays < 1ms").isLessThan(1);
    }

    @Test
    @Order(2)
    @DisplayName("SIMD maxValue  --  100K elements under 500us")
    void simd_maxValue_100K_under_500us() {
        int n = 100_000;
        float[] arr = new float[n];
        Random rng = new Random(42);
        for (int i = 0; i < n; i++) arr[i] = rng.nextFloat();

        // Warm up
        for (int i = 0; i < 5_000; i++) {
            SIMDScoreAccumulator.maxValue(arr, n);
        }

        long start = System.nanoTime();
        float max = SIMDScoreAccumulator.maxValue(arr, n);
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed / 1000).as("100K maxValue < 500us").isLessThan(500);
    }

    @Test
    @Order(3)
    @DisplayName("SpladeIndex  --  10K docs, single query under 5ms")
    void splade_10K_search_under_5ms() {
        SpladeIndex index = new SpladeIndex();
        Random rng = new Random(42);

        for (int d = 0; d < 10_000; d++) {
            Map<String, Float> sparse = new HashMap<>();
            for (int t = 0; t < 100; t++) {
                sparse.put("term-" + rng.nextInt(5000), rng.nextFloat() * 3.0f);
            }
            index.indexSparse("doc-" + d, sparse);
        }

        Map<String, Float> query = new HashMap<>();
        for (int t = 0; t < 20; t++) {
            query.put("term-" + rng.nextInt(5000), rng.nextFloat() * 2.0f);
        }

        for (int i = 0; i < 100; i++) {
            index.searchSparse(query, 10);
        }

        long start = System.nanoTime();
        ScoredResult[] results = index.searchSparse(query, 10);
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed / 1_000_000).as("10K SPLADE search < 5ms").isLessThan(5);
        assertThat(results).isNotEmpty();

        index.close();
    }

    @Test
    @Order(4)
    @DisplayName("SpladeIndex  --  bulk index 1K docs x 100 terms under 500ms")
    void splade_bulkIndex_1K_under_500ms() {
        SpladeIndex index = new SpladeIndex();
        Random rng = new Random(42);

        List<Map<String, Float>> sparseVecs = new ArrayList<>(1000);
        for (int d = 0; d < 1000; d++) {
            Map<String, Float> sparse = new HashMap<>();
            for (int t = 0; t < 100; t++) {
                sparse.put("term-" + rng.nextInt(5000), rng.nextFloat() * 3.0f);
            }
            sparseVecs.add(sparse);
        }

        long start = System.nanoTime();
        for (int d = 0; d < 1000; d++) {
            index.indexSparse("doc-" + d, sparseVecs.get(d));
        }
        long elapsed = System.nanoTime() - start;

        assertThat(elapsed / 1_000_000).as("1K bulk index < 500ms").isLessThan(500);
        assertThat(index.size()).isEqualTo(1000);

        index.close();
    }

    @Test
    @Order(5)
    @DisplayName("BM25Index  --  10K docs, multi-term query under 500us")
    void bm25_10K_search_under_500us() {
        BM25Index index = new BM25Index();
        Random rng = new Random(42);
        String[] words = {
                "java", "panama", "vector", "memory", "segment", "cognitive",
                "recall", "graph", "hebbian", "synapse", "cortex", "embedding",
                "splade", "dense", "sparse", "index", "quantum", "neural"
        };

        for (int d = 0; d < 10_000; d++) {
            StringBuilder sb = new StringBuilder();
            int docLen = 15 + rng.nextInt(35);
            for (int w = 0; w < docLen; w++) {
                sb.append(words[rng.nextInt(words.length)]).append(" ");
            }
            index.index("doc-" + d, sb.toString());
        }

        for (int i = 0; i < 500; i++) {
            index.search("cognitive vector memory recall", 10);
        }

        long start = System.nanoTime();
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            ScoredResult[] results = index.search("cognitive vector memory recall", 10);
            assertThat(results).isNotEmpty();
        }
        long elapsedNanos = System.nanoTime() - start;
        long avgMicros = (elapsedNanos / iterations) / 1000;

        assertThat(avgMicros).as("10K BM25 search < 500us").isLessThan(500);

        index.close();
    }
}
