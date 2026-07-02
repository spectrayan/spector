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
package com.spectrayan.spector.bench.cognitive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.GraphSnapshotCollector.GraphSnapshot;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.model.ReflectReport;

/**
 * Generates a markdown baseline report from graph health data collected during
 * the P0 GraphHealthMetrics experiment.
 *
 * <p>The report includes scale curves, convergence analysis, bridge protection
 * effectiveness, and a data-driven verdict on whether Riemannian geometry
 * optimization is warranted.</p>
 */
public final class GraphHealthReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(GraphHealthReportGenerator.class);

    /**
     * A single data point from one reflection cycle.
     *
     * @param cycle         the cycle number (1-based)
     * @param report        the ReflectReport from this cycle
     * @param snapshotAfter the graph snapshot captured after the cycle
     */
    public record CycleDataPoint(int cycle, ReflectReport report, GraphSnapshot snapshotAfter) {}

    /**
     * All data for one scale point (e.g., 1K, 5K, 10K, 20K memories).
     *
     * @param scaleLabel      human-readable label (e.g., "1K", "5K")
     * @param corpusSize      actual corpus records ingested
     * @param ingestionMs     time to ingest all records (ms)
     * @param snapshotInitial graph snapshot right after ingestion, before reflection
     * @param cycles          per-cycle data points
     */
    public record ScalePointData(
            String scaleLabel,
            int corpusSize,
            long ingestionMs,
            GraphSnapshot snapshotInitial,
            List<CycleDataPoint> cycles
    ) {}

    /**
     * Generates the full baseline report and writes it to the given path.
     *
     * @param outputPath  the file to write the report to
     * @param scalePoints data collected at each scale point
     * @throws IOException if writing fails
     */
    public static void generate(Path outputPath, List<ScalePointData> scalePoints) throws IOException {
        var sb = new StringBuilder(16_000);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        sb.append("# P0: Graph Health Baseline Report\n\n");
        sb.append("**Generated**: ").append(now).append("\n");
        sb.append("**Dataset**: `spector-datasets/balanced-baseline` (Mike Thompson, 365-day corpus)\n\n");
        sb.append("---\n\n");

        // ── Section 1: Dataset Summary ──
        sb.append("## 1. Dataset Summary\n\n");
        sb.append("| Scale Point | Corpus Size | Ingestion (ms) | Entities | Entity Edges | ");
        sb.append("Hebbian Active | Hebbian Edges | Temporal Linked |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            sb.append(String.format("| %s | %,d | %,d | %d | %d | %d | %d | %d |\n",
                    sp.scaleLabel(), sp.corpusSize(), sp.ingestionMs(),
                    s.entityCount(), s.entityEdgeCount(),
                    s.hebbianActiveNodes(), s.hebbianTotalEdges(),
                    s.temporalLinkedCount()));
        }
        sb.append("\n");

        // ── Section 2: Scale Curves (Post-Ingestion) ──
        sb.append("## 2. Scale Curves — Post-Ingestion State\n\n");
        sb.append("| Scale | Entity:Memory Ratio | Hebbian Edge Density | ");
        sb.append("Entity Max Degree | Hebbian Max Degree | Temporal Coverage |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            sb.append(String.format("| %s | %.4f | %.2f | %d | %d | %.1f%% |\n",
                    sp.scaleLabel(),
                    s.entityToMemoryRatio(),
                    s.hebbianEdgeDensity(),
                    s.entityMaxDegree(),
                    s.hebbianMaxDegree(),
                    s.temporalCoverage() * 100f));
        }
        sb.append("\n");

        // ── Section 3: Convergence Analysis ──
        sb.append("## 3. Convergence Analysis — Per-Cycle Metrics\n\n");
        for (ScalePointData sp : scalePoints) {
            sb.append("### ").append(sp.scaleLabel()).append(" (").append(sp.corpusSize()).append(" memories)\n\n");
            sb.append("| Cycle | Heb Decayed | Heb Surviving | Heb Bridge Prot | Heb Arousal Mod | ");
            sb.append("Ent Decayed | Ent Surviving | Ent Bridge Prot | ");
            sb.append("Avg Import | Avg Age | Max Age | Frag Ratio |\n");
            sb.append("|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|\n");

            for (CycleDataPoint dp : sp.cycles()) {
                GraphHealthMetrics m = dp.report().graphHealth();
                if (m == null) {
                    sb.append(String.format("| %d | — | — | — | — | — | — | — | — | — | — | — |\n", dp.cycle()));
                    continue;
                }
                sb.append(String.format("| %d | %d | %d | %d | %d | %d | %d | %d | %.3f | %.1f | %d | %.4f |\n",
                        dp.cycle(),
                        m.hebbianEdgesDecayed(), m.hebbianEdgesSurviving(),
                        m.hebbianBridgeProtected(), m.hebbianArousalModulated(),
                        m.entityEdgesDecayed(), m.entityEdgesSurviving(),
                        m.entityBridgeProtected(),
                        m.averageImportanceScore(), m.averageEdgeAge(),
                        m.maxEdgeAge(), m.fragmentationRatio()));
            }
            sb.append("\n");
        }

        // ── Section 4: Bridge Score Distribution ──
        sb.append("## 4. Bridge Score Distribution (Final Cycle)\n\n");
        sb.append("| Scale | Q1 (0-63) | Q2 (64-127) | Q3 (128-191) | Q4 (192-255) | ");
        sb.append("Total | Q4% (Critical) |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            if (sp.cycles().isEmpty()) continue;
            CycleDataPoint last = sp.cycles().getLast();
            GraphHealthMetrics m = last.report().graphHealth();
            if (m == null) continue;
            int total = m.bridgeQ1() + m.bridgeQ2() + m.bridgeQ3() + m.bridgeQ4();
            float q4pct = total > 0 ? (float) m.bridgeQ4() / total * 100f : 0f;
            sb.append(String.format("| %s | %d | %d | %d | %d | %d | %.1f%% |\n",
                    sp.scaleLabel(), m.bridgeQ1(), m.bridgeQ2(), m.bridgeQ3(), m.bridgeQ4(),
                    total, q4pct));
        }
        sb.append("\n");

        // ── Section 4.5: Entity Hierarchy Depth ──
        sb.append("## 4.5. Entity Hierarchy Depth (Final Cycle)\n\n");
        sb.append("| Scale | Max Depth | Avg Depth | 1-Hop | 2-Hop | 3-Hop | 4+-Hop | ");
        sb.append("Deep (3+)% |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            if (sp.cycles().isEmpty()) continue;
            CycleDataPoint last = sp.cycles().getLast();
            GraphHealthMetrics m = last.report().graphHealth();
            if (m == null) continue;
            int total = m.depthBucket1() + m.depthBucket2() + m.depthBucket3() + m.depthBucket4Plus();
            float deepPct = total > 0
                    ? (float) (m.depthBucket3() + m.depthBucket4Plus()) / total * 100f : 0f;
            sb.append(String.format("| %s | %d | %.1f | %d | %d | %d | %d | %.1f%% |\n",
                    sp.scaleLabel(), m.entityMaxDepth(), m.averageEntityDepth(),
                    m.depthBucket1(), m.depthBucket2(), m.depthBucket3(), m.depthBucket4Plus(),
                    deepPct));
        }
        sb.append("\n");

        // ── Section 5: Entity Explosion Assessment ──
        sb.append("## 5. Entity Explosion Assessment\n\n");
        sb.append("| Scale | Entities | Entity Edges | Entity:Memory | Max Degree | ");
        sb.append("Avg Degree | Adj High Water |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            sb.append(String.format("| %s | %d | %d | %.4f | %d | %.2f | %d |\n",
                    sp.scaleLabel(),
                    s.entityCount(), s.entityEdgeCount(),
                    s.entityToMemoryRatio(),
                    s.entityMaxDegree(), s.entityAvgDegree(),
                    s.entityAdjHighWater()));
        }
        sb.append("\n");

        // ── Section 6: Reflection Impact ──
        sb.append("## 6. Reflection Impact — Before vs. After\n\n");
        sb.append("| Scale | Pre-Reflect Heb Edges | Post-Reflect Heb Edges | Δ Heb | ");
        sb.append("Pre-Reflect Ent Edges | Post-Reflect Ent Edges | Δ Ent |\n");
        sb.append("|:---|:---|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            if (sp.cycles().isEmpty()) continue;
            GraphSnapshot pre = sp.snapshotInitial();
            GraphSnapshot post = sp.cycles().getLast().snapshotAfter();
            int deltaHeb = post.hebbianTotalEdges() - pre.hebbianTotalEdges();
            int deltaEnt = post.entityEdgeCount() - pre.entityEdgeCount();
            sb.append(String.format("| %s | %d | %d | %+d | %d | %d | %+d |\n",
                    sp.scaleLabel(),
                    pre.hebbianTotalEdges(), post.hebbianTotalEdges(), deltaHeb,
                    pre.entityEdgeCount(), post.entityEdgeCount(), deltaEnt));
        }
        sb.append("\n");

        // ── Section 7: Consolidation & Pruning Summary ──
        sb.append("## 7. Consolidation & Pruning Summary\n\n");
        sb.append("| Scale | Total Consolidated | Total Tombstoned | Total Temporal Pruned | ");
        sb.append("Total Reflect Time (ms) |\n");
        sb.append("|:---|:---|:---|:---|:---|\n");
        for (ScalePointData sp : scalePoints) {
            int totalCons = 0, totalTomb = 0, totalTemp = 0;
            long totalMs = 0;
            for (CycleDataPoint dp : sp.cycles()) {
                totalCons += dp.report().consolidatedCount();
                totalTomb += dp.report().tombstonedCount();
                totalTemp += dp.report().temporalPrunedCount();
                totalMs += dp.report().duration().toMillis();
            }
            sb.append(String.format("| %s | %d | %d | %d | %,d |\n",
                    sp.scaleLabel(), totalCons, totalTomb, totalTemp, totalMs));
        }
        sb.append("\n");

        // ── Section 8: Verdict ──
        sb.append("## 8. Verdict — Is Riemannian Geometry Warranted?\n\n");
        sb.append("Based on the data above:\n\n");

        // Auto-generate verdict based on data
        if (!scalePoints.isEmpty()) {
            ScalePointData largest = scalePoints.getLast();
            GraphSnapshot s = largest.snapshotInitial();

            sb.append("### Entity Explosion\n\n");
            if (s.entityToMemoryRatio() > 0.05) {
                sb.append("> [!WARNING]\n");
                sb.append(String.format("> Entity-to-memory ratio is **%.4f** at %s. ",
                        s.entityToMemoryRatio(), largest.scaleLabel()));
                sb.append("This suggests entity growth is controlled by the dataset's entity extraction, ");
                sb.append("not exploding combinatorially. ");
                sb.append("HyperEntityGraph (#70) would still reduce edge count but entity explosion ");
                sb.append("is not an immediate crisis.\n\n");
            } else {
                sb.append("> [!TIP]\n");
                sb.append(String.format("> Entity-to-memory ratio is only **%.4f** at %s — very low. ",
                        s.entityToMemoryRatio(), largest.scaleLabel()));
                sb.append("Entity explosion is not a concern at this scale.\n\n");
            }

            sb.append("### Bridge Protection\n\n");
            if (!largest.cycles().isEmpty()) {
                GraphHealthMetrics lastM = largest.cycles().getLast().report().graphHealth();
                if (lastM != null && lastM.totalBridgeProtected() > 0) {
                    sb.append("> [!TIP]\n");
                    sb.append(String.format("> Bridge protection is **active**: %d edges saved from eviction ",
                            lastM.totalBridgeProtected()));
                    sb.append("in the final cycle. The importance-ranked eviction (#68) is working.\n\n");
                } else {
                    sb.append("> [!NOTE]\n");
                    sb.append("> Bridge protection did not fire — either all edges are strong enough ");
                    sb.append("to survive without protection, or the bridge detection threshold needs tuning.\n\n");
                }
            }

            sb.append("### Riemannian Geometry Recommendation\n\n");
            sb.append("> [!IMPORTANT]\n");
            sb.append("> **Review the data tables above** and assess:\n");
            sb.append("> 1. Is entity degree capped frequently (max degree near 48)? → HyperEntityGraph first\n");
            sb.append("> 2. Is fragmentation ratio increasing across cycles? → Spectral sparsification needed\n");
            sb.append("> 3. Is hierarchy depth > 3 hops common? → Hyperbolic embeddings add value\n");
            sb.append("> 4. Is bridge protection saving < 5% of edges? → Bridge detection needs tuning first\n\n");
        }

        sb.append("---\n\n");
        sb.append("*Report generated by `GraphHealthBaselineTest` — P0 baseline measurement.*\n");

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, sb.toString());
        log.info("Graph health baseline report written to: {}", outputPath);
    }
}
