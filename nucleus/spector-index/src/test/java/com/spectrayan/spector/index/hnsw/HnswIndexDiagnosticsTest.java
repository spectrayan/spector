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
package com.spectrayan.spector.index.hnsw;

import com.spectrayan.spector.config.properties.HnswProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.core.similarity.SimilarityFunction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tests for {@link HnswIndexDiagnostics}.
 */
class HnswIndexDiagnosticsTest {

    private static final int DIM = 8;

    @Test
    void emptyIndexHasNoNodesOrLevels() {
        try (var idx = new HnswIndex(DIM, 10, SimilarityFunction.COSINE, new HnswProperties(16, 200, 50))) {
            HnswIndexDiagnostics diag = HnswIndexDiagnostics.of(idx);

            assertThat(diag.nodeCount()).isZero();
            assertThat(diag.maxLevel()).isEqualTo(-1);
            assertThat(diag.entryPoint()).isEqualTo(-1);
            assertThat(diag.levelDistribution()).isEmpty();
        }
    }

    @Test
    void capturesGraphStateAndParams() {
        try (var idx = prebuiltIndex(new HnswProperties(16, 200, 50))) {
            HnswIndexDiagnostics diag = HnswIndexDiagnostics.of(idx);

            assertThat(diag.nodeCount()).isEqualTo(4);
            assertThat(diag.maxLevel()).isEqualTo(2);
            assertThat(diag.entryPoint()).isEqualTo(1);
            assertThat(diag.m()).isEqualTo(16);
            assertThat(diag.efConstruction()).isEqualTo(200);
        }
    }

    @Test
    void levelDistributionCountsNodesPresentAtEachLevel() {
        try (var idx = prebuiltIndex(new HnswProperties(16, 200, 50))) {
            HnswIndexDiagnostics diag = HnswIndexDiagnostics.of(idx);

            // levels: node0=0, node1=2, node2=1, node3=0
            assertThat(diag.levelDistribution())
                    .containsExactly(Map.entry(0, 4), Map.entry(1, 2), Map.entry(2, 1));
        }
    }

    @Test
    void levelDistributionIsConsistentForBuiltIndex() {
        try (var idx = new HnswIndex(DIM, 500, SimilarityFunction.COSINE, new HnswProperties(8, 64, 32))) {
            Random rng = new Random(7);
            for (int i = 0; i < 500; i++) {
                idx.add("doc-" + i, i, randomVector(DIM, rng));
            }

            HnswIndexDiagnostics diag = HnswIndexDiagnostics.of(idx);
            Map<Integer, Integer> dist = diag.levelDistribution();

            assertThat(dist.get(0)).isEqualTo(500);
            assertThat(dist.keySet()).containsExactlyElementsOf(range(diag.maxLevel()));
            for (int level = 1; level <= diag.maxLevel(); level++) {
                assertThat(dist.get(level)).isLessThanOrEqualTo(dist.get(level - 1));
            }
            assertThat(idx.getLevel(diag.entryPoint())).isEqualTo(diag.maxLevel());
        }
    }

    @Test
    void estimatedRecallIsCappedAt99Percent() {
        try (var idx = prebuiltIndex(new HnswProperties(16, 200, 50))) {
            assertThat(HnswIndexDiagnostics.of(idx).estimatedRecall()).isEqualTo(0.99);
        }
    }

    @Test
    void estimatedRecallBelowCapFollowsFormula() {
        // efSearch=1, m=100 -> 0.90 + 1/101
        try (var idx = prebuiltIndex(new HnswProperties(100, 200, 1))) {
            assertThat(HnswIndexDiagnostics.of(idx).estimatedRecall())
                    .isCloseTo(0.90 + 1.0 / 101, within(1e-9));
        }
    }

    @Test
    void toLogStringIncludesAllFields() {
        try (var idx = prebuiltIndex(new HnswProperties(16, 200, 50))) {
            String log = HnswIndexDiagnostics.of(idx).toLogString();

            assertThat(log)
                    .contains("HNSW Index Diagnostics:")
                    .contains("Entries: 4")
                    .contains("Levels: {0=4, 1=2, 2=1}")
                    .contains("Entry Point: node=1, level=2")
                    .contains("M=16, efConstruction=200")
                    // decimal separator depends on the default locale
                    .containsPattern("Estimated Recall@10: 99[.,]0%");
        }
    }

    @Test
    void inspectsQuantizedIndex() {
        try (var idx = new QuantizedHnswIndex(DIM, 300, SimilarityFunction.COSINE, new HnswProperties(8, 64, 32))) {
            Random rng = new Random(11);
            for (int i = 0; i < 300; i++) idx.add("doc-" + i, i, randomVector(DIM, rng));

            HnswIndexDiagnostics diag = HnswIndexDiagnostics.of(idx);
            assertThat(diag.nodeCount()).isEqualTo(300);
            assertThat(diag.levelDistribution().get(0)).isEqualTo(300);
            assertThat(diag.levelDistribution().keySet()).containsExactlyElementsOf(range(diag.maxLevel()));
            assertThat(idx.getLevel(diag.entryPoint())).isEqualTo(diag.maxLevel());
            assertThat(diag.m()).isEqualTo(8);
        }
    }

    /** Builds a 4-node index with fixed levels, entry point 1 and max level 2. */
    private static HnswIndex prebuiltIndex(HnswProperties params) {
        var idx = new HnswIndex(DIM, 10, SimilarityFunction.COSINE, params);
        Random rng = new Random(42);
        int[] levels = {0, 2, 1, 0};
        for (int i = 0; i < levels.length; i++) {
            idx.addPrebuilt("doc-" + i, i, randomVector(DIM, rng), levels[i], new int[0], null);
        }
        idx.restoreGraphState(1, 2);
        return idx;
    }

    private static List<Integer> range(int maxInclusive) {
        List<Integer> levels = new ArrayList<>();
        for (int i = 0; i <= maxInclusive; i++) levels.add(i);
        return levels;
    }

    private static float[] randomVector(int dim, Random rng) {
        float[] v = new float[dim];
        for (int i = 0; i < dim; i++) v[i] = rng.nextFloat() * 2f - 1f;
        return v;
    }
}
