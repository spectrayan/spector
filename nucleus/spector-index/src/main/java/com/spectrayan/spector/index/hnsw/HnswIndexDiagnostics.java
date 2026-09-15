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

import java.util.Map;
import java.util.TreeMap;

/**
 * Point-in-time snapshot of an HNSW graph's structure and build parameters.
 *
 * <p>Works with any {@link AbstractHnswIndex}, including {@link HnswIndex} and
 * {@link QuantizedHnswIndex}. Emitted as a structured INFO log after a
 * persistence load and after graph builds.</p>
 *
 * <p>An empty index yields {@code nodeCount == 0}, {@code maxLevel == -1},
 * {@code entryPoint == -1} and an empty level distribution.</p>
 *
 * @param nodeCount         number of nodes in the graph
 * @param maxLevel          highest layer in the graph, or {@code -1} if empty
 * @param entryPoint        entry point node index, or {@code -1} if empty
 * @param levelDistribution number of nodes present on each layer {@code 0..maxLevel},
 *                          ordered by layer; layer 0 always equals {@code nodeCount}
 * @param m                 max connections per node on upper layers
 * @param efConstruction    candidate list size used during construction
 * @param estimatedRecall   heuristic recall@10 estimate derived from
 *                          {@code efSearch} and {@code m}, capped at 0.99
 * @see AbstractHnswIndex
 */
public record HnswIndexDiagnostics (int nodeCount, int maxLevel,
int entryPoint, Map<Integer, Integer> levelDistribution, int m,
int efConstruction,double estimatedRecall) {

    /**
     * Captures diagnostics from the given index.
     *
     * <p>Iterates over every node to build the level distribution, so the cost
     * is proportional to the node count.</p>
     *
     * @param index the HNSW index to inspect
     * @return a diagnostics snapshot of the index
     */
    public static HnswIndexDiagnostics of(AbstractHnswIndex index) {
        int nodeCount = index.size();
        int maxLevel = index.maxLevel();
        int entryPoint = index.entryPoint();
        Map<Integer, Integer> levelDistribution = new TreeMap<>();
        for (int node = 0; node < nodeCount; node++) {
            int nodeLevel = index.getLevel(node);
            for (int level = 0; level <= nodeLevel; level++) {
                levelDistribution.merge(level, 1, Integer::sum);
            }
        }
        int m = index.params().m();
        int efConstruction = index.params().efConstruction();
        int efSearch = index.params().efSearch();
        double estimatedRecall = Math.min(0.99, 0.90 + (double) efSearch / (efSearch + m));
        return new HnswIndexDiagnostics(nodeCount, maxLevel, entryPoint, levelDistribution, m, efConstruction, estimatedRecall);
    }

    /**
     * Formats the diagnostics as a multi-line, human-readable block for logging.
     *
     * @return the formatted diagnostics
     */
    public String toLogString() {
        return String.format(
            """
            HNSW Index Diagnostics:
                Entries: %,d
                Levels: %s
                Entry Point: node=%d, level=%d
                M=%d, efConstruction=%d
                Estimated Recall@10: %.1f%%
            """,
            nodeCount,
            levelDistribution,
            entryPoint,
            maxLevel,
            m,
            efConstruction,
            estimatedRecall * 100
        );
    }
}
