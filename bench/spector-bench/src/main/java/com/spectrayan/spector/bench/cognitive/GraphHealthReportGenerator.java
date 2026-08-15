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

import com.spectrayan.spector.bench.cognitive.GraphSnapshotCollector.GraphSnapshot;
import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.model.ReflectReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final TemplateEngine TEMPLATE_ENGINE = TemplateEngine.createDefault();

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
        generate(outputPath, scalePoints, TEMPLATE_ENGINE);
    }

    public static void generate(Path outputPath, List<ScalePointData> scalePoints, TemplateEngine templateEngine) throws IOException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Object> model = new HashMap<>();
        model.put("generatedAt", now);

        // Section 1: Dataset Summary
        List<Map<String, Object>> datasetSummary = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            datasetSummary.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "corpusSize", String.format("%,d", sp.corpusSize()),
                    "ingestionMs", String.format("%,d", sp.ingestionMs()),
                    "entities", s.entityCount(),
                    "entityEdges", s.entityEdgeCount(),
                    "hebbianActive", s.hebbianActiveNodes(),
                    "hebbianEdges", s.hebbianTotalEdges(),
                    "temporalLinked", s.temporalLinkedCount()
            ));
        }
        model.put("datasetSummary", datasetSummary);

        // Section 2: Scale Curves
        List<Map<String, Object>> scaleCurves = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            scaleCurves.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "entityToMemoryRatio", s.entityToMemoryRatio(),
                    "hebbianEdgeDensity", s.hebbianEdgeDensity(),
                    "entityMaxDegree", s.entityMaxDegree(),
                    "hebbianMaxDegree", s.hebbianMaxDegree(),
                    "temporalCoverage", s.temporalCoverage() * 100f
            ));
        }
        model.put("scaleCurves", scaleCurves);

        // Section 3: Convergence Analysis
        List<Map<String, Object>> convergencePerScale = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            List<Map<String, Object>> cycles = new ArrayList<>();
            for (CycleDataPoint dp : sp.cycles()) {
                GraphHealthMetrics m = dp.report().graphHealth();
                Map<String, Object> cycleMap = new HashMap<>();
                cycleMap.put("cycle", dp.cycle());
                if (m == null) {
                    cycleMap.put("hebDecayed", "—");
                    cycleMap.put("hebSurviving", "—");
                    cycleMap.put("hebBridgeProt", "—");
                    cycleMap.put("hebArousalMod", "—");
                    cycleMap.put("entDecayed", "—");
                    cycleMap.put("entSurviving", "—");
                    cycleMap.put("entBridgeProt", "—");
                    cycleMap.put("avgImport", "—");
                    cycleMap.put("avgAge", "—");
                    cycleMap.put("maxAge", "—");
                    cycleMap.put("fragRatio", "—");
                } else {
                    cycleMap.put("hebDecayed", m.hebbianEdgesDecayed());
                    cycleMap.put("hebSurviving", m.hebbianEdgesSurviving());
                    cycleMap.put("hebBridgeProt", m.hebbianBridgeProtected());
                    cycleMap.put("hebArousalMod", m.hebbianArousalModulated());
                    cycleMap.put("entDecayed", m.entityEdgesDecayed());
                    cycleMap.put("entSurviving", m.entityEdgesSurviving());
                    cycleMap.put("entBridgeProt", m.entityBridgeProtected());
                    cycleMap.put("avgImport", String.format("%.3f", m.averageImportanceScore()));
                    cycleMap.put("avgAge", String.format("%.1f", m.averageEdgeAge()));
                    cycleMap.put("maxAge", m.maxEdgeAge());
                    cycleMap.put("fragRatio", String.format("%.4f", m.fragmentationRatio()));
                }
                cycles.add(cycleMap);
            }
            convergencePerScale.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "corpusSize", sp.corpusSize(),
                    "cycles", cycles
            ));
        }
        model.put("convergencePerScale", convergencePerScale);

        // Section 4: Bridge Score Distribution
        List<Map<String, Object>> bridgeDistributions = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            if (sp.cycles().isEmpty()) continue;
            CycleDataPoint last = sp.cycles().getLast();
            GraphHealthMetrics m = last.report().graphHealth();
            if (m == null) continue;
            int total = m.bridgeQ1() + m.bridgeQ2() + m.bridgeQ3() + m.bridgeQ4();
            float q4pct = total > 0 ? (float) m.bridgeQ4() / total * 100f : 0f;
            bridgeDistributions.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "q1", m.bridgeQ1(),
                    "q2", m.bridgeQ2(),
                    "q3", m.bridgeQ3(),
                    "q4", m.bridgeQ4(),
                    "total", total,
                    "q4Pct", q4pct
            ));
        }
        model.put("bridgeDistributions", bridgeDistributions);

        // Section 4.5: Entity Hierarchy Depth
        boolean hasDepthData = scalePoints.stream()
                .flatMap(sp -> sp.cycles().stream())
                .map(c -> c.report().graphHealth())
                .filter(Objects::nonNull)
                .anyMatch(m -> m.depthBucket1() + m.depthBucket2()
                        + m.depthBucket3() + m.depthBucket4Plus() > 0);

        model.put("hasDepthData", hasDepthData);
        if (hasDepthData) {
            List<Map<String, Object>> hierarchyDepths = new ArrayList<>();
            for (ScalePointData sp : scalePoints) {
                if (sp.cycles().isEmpty()) continue;
                CycleDataPoint last = sp.cycles().getLast();
                GraphHealthMetrics m = last.report().graphHealth();
                if (m == null) continue;
                int total = m.depthBucket1() + m.depthBucket2() + m.depthBucket3() + m.depthBucket4Plus();
                float deepPct = total > 0
                        ? (float) (m.depthBucket3() + m.depthBucket4Plus()) / total * 100f : 0f;
                hierarchyDepths.add(Map.of(
                        "scaleLabel", sp.scaleLabel(),
                        "maxDepth", m.entityMaxDepth(),
                        "avgDepth", m.averageEntityDepth(),
                        "b1", m.depthBucket1(),
                        "b2", m.depthBucket2(),
                        "b3", m.depthBucket3(),
                        "b4", m.depthBucket4Plus(),
                        "deepPct", deepPct
                ));
            }
            model.put("hierarchyDepths", hierarchyDepths);
        }

        // Section 5: Entity Explosion Assessment
        List<Map<String, Object>> entityExplosions = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            GraphSnapshot s = sp.snapshotInitial();
            entityExplosions.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "entities", s.entityCount(),
                    "entityEdges", s.entityEdgeCount(),
                    "entityMemoryRatio", s.entityToMemoryRatio(),
                    "maxDegree", s.entityMaxDegree(),
                    "avgDegree", s.entityAvgDegree(),
                    "adjHighWater", s.entityAdjHighWater()
            ));
        }
        model.put("entityExplosions", entityExplosions);

        // Section 6: Reflection Impact
        List<Map<String, Object>> reflectionImpacts = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            if (sp.cycles().isEmpty()) continue;
            GraphSnapshot pre = sp.snapshotInitial();
            GraphSnapshot post = sp.cycles().getLast().snapshotAfter();
            int deltaHeb = post.hebbianTotalEdges() - pre.hebbianTotalEdges();
            int deltaEnt = post.entityEdgeCount() - pre.entityEdgeCount();
            reflectionImpacts.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "preHeb", pre.hebbianTotalEdges(),
                    "postHeb", post.hebbianTotalEdges(),
                    "deltaHeb", String.format("%+d", deltaHeb),
                    "preEnt", pre.entityEdgeCount(),
                    "postEnt", post.entityEdgeCount(),
                    "deltaEnt", String.format("%+d", deltaEnt)
            ));
        }
        model.put("reflectionImpacts", reflectionImpacts);

        // Section 7: Consolidation & Pruning Summary
        List<Map<String, Object>> consolidationSummaries = new ArrayList<>();
        for (ScalePointData sp : scalePoints) {
            int totalCons = 0, totalTomb = 0, totalTemp = 0;
            long totalMs = 0;
            for (CycleDataPoint dp : sp.cycles()) {
                totalCons += dp.report().consolidatedCount();
                totalTomb += dp.report().tombstonedCount();
                totalTemp += dp.report().temporalPrunedCount();
                totalMs += dp.report().duration().toMillis();
            }
            consolidationSummaries.add(Map.of(
                    "scaleLabel", sp.scaleLabel(),
                    "totalCons", totalCons,
                    "totalTomb", totalTomb,
                    "totalTemp", totalTemp,
                    "totalMs", String.format("%,d", totalMs)
            ));
        }
        model.put("consolidationSummaries", consolidationSummaries);

        // Section 8: Verdict
        if (!scalePoints.isEmpty()) {
            ScalePointData largest = scalePoints.getLast();
            GraphSnapshot s = largest.snapshotInitial();
            boolean entityExplosionWarning = s.entityToMemoryRatio() > 0.05;

            boolean bridgeProtectionActive = false;
            int bridgeProtectedCount = 0;
            if (!largest.cycles().isEmpty()) {
                GraphHealthMetrics lastM = largest.cycles().getLast().report().graphHealth();
                if (lastM != null && lastM.totalBridgeProtected() > 0) {
                    bridgeProtectionActive = true;
                    bridgeProtectedCount = lastM.totalBridgeProtected();
                }
            }

            Map<String, Object> verdict = Map.of(
                    "entityExplosionWarning", entityExplosionWarning,
                    "entityMemoryRatio", s.entityToMemoryRatio(),
                    "largestScaleLabel", largest.scaleLabel(),
                    "bridgeProtectionActive", bridgeProtectionActive,
                    "bridgeProtectedCount", bridgeProtectedCount
            );
            model.put("verdict", verdict);
        }

        String renderedReport = templateEngine.render("reports/graph-health", model);

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, renderedReport);
        log.info("Graph health baseline report written to: {}", outputPath);
    }
}
