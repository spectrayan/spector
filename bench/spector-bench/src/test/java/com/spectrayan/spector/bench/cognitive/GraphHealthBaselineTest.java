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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.GraphHealthReportGenerator.CycleDataPoint;
import com.spectrayan.spector.bench.cognitive.GraphHealthReportGenerator.ScalePointData;
import com.spectrayan.spector.bench.cognitive.GraphSnapshotCollector.GraphSnapshot;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;

/**
 * P0 integration test: measures GraphHealthMetrics baseline across multiple
 * scale points and reflection cycles using the balanced-baseline dataset.
 *
 * <p>Produces a detailed markdown report at {@code RnD/graph-health-baseline-report.md}
 * that answers: "Do we need Riemannian geometry, or is flat-space with
 * importance-ranked eviction already sufficient?"</p>
 *
 * <h3>Scale Points</h3>
 * <ul>
 *   <li>1K memories — 10 reflection cycles</li>
 *   <li>5K memories — 10 reflection cycles</li>
 *   <li>10K memories — 10 reflection cycles</li>
 *   <li>20K memories — 20 reflection cycles (convergence analysis)</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Ollama running locally with {@code nomic-embed-text:latest}</li>
 *   <li>Dataset at {@code D:\git\spector-datasets\balanced-baseline\data\}</li>
 * </ul>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE", matches = "true")
@DisplayName("P0: GraphHealthMetrics Baseline — 20K Balanced Dataset")
class GraphHealthBaselineTest {

    private static final Logger log = LoggerFactory.getLogger(GraphHealthBaselineTest.class);

    /** Path to the balanced-baseline dataset (override with {@code -Dspector.bench.dataset.dir}). */
    private static final Path DATASET_DIR = Path.of(System.getProperty(
            "spector.bench.dataset.dir", "../spector-datasets/balanced-baseline/data"));

    /** Path to output the baseline report (override with {@code -Dspector.bench.report.output}). */
    private static final Path REPORT_OUTPUT = Path.of(System.getProperty(
            "spector.bench.report.output", "target/graph-health-baseline-report.md"));

    /** Scale points: subset sizes to test. */
    private static final int[] SCALE_POINTS = {1_000, 5_000, 10_000, 20_000};

    /** Reflection cycles per scale point (except the largest). */
    private static final int CYCLES_PER_SCALE = 10;

    /** Extra cycles for the largest scale point (convergence analysis). */
    private static final int CYCLES_FULL_SCALE = 20;

    /** Ollama embedding model to use. */
    private static final String EMBED_MODEL = System.getProperty(
            "spector.embed.model", "nomic-embed-text:latest");

    @Test
    @DisplayName("Baseline: graph health over multiple scale points and reflection cycles")
    void baseline_graphHealthOverMultipleReflectionCycles() throws Exception {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("P0: GraphHealthMetrics Baseline Test Starting");
        log.info("Dataset: {}", DATASET_DIR);
        log.info("Embed model: {}", EMBED_MODEL);
        log.info("Scale points: {} scale(s), cycles: {}/{}", SCALE_POINTS.length, CYCLES_PER_SCALE, CYCLES_FULL_SCALE);
        log.info("═══════════════════════════════════════════════════════════════");

        // Load the full dataset once
        var loader = new DatasetLoader();
        LoadedDataset fullDataset = loader.load(DATASET_DIR);
        log.info("Full dataset loaded: {} corpus, {} entities, {} hebbian, {} temporal",
                fullDataset.corpus().size(), fullDataset.entityRelations().size(),
                fullDataset.hebbianEdges().size(), fullDataset.temporalChains().size());

        // Create embedding provider
        EmbeddingProvider embedder = OllamaEmbeddingProvider.create(EMBED_MODEL);
        log.info("Embedding provider ready: {} dimensions", embedder.dimensions());

        List<ScalePointData> allScaleData = new ArrayList<>();

        for (int i = 0; i < SCALE_POINTS.length; i++) {
            int targetSize = SCALE_POINTS[i];
            int cycles = (i == SCALE_POINTS.length - 1) ? CYCLES_FULL_SCALE : CYCLES_PER_SCALE;
            String label = formatLabel(targetSize);

            log.info("───────────────────────────────────────────────────────────────");
            log.info("Scale Point: {} ({} records, {} reflection cycles)", label, targetSize, cycles);
            log.info("───────────────────────────────────────────────────────────────");

            // Create subset dataset
            LoadedDataset subset = createSubset(fullDataset, targetSize);

            // Ingest and measure
            ScalePointData data = runScalePoint(label, subset, embedder, cycles);
            allScaleData.add(data);

            log.info("Scale {} complete: {} ingested in {}ms, {} cycles run",
                    label, data.corpusSize(), data.ingestionMs(), data.cycles().size());
        }

        // Generate the report
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("Generating baseline report at: {}", REPORT_OUTPUT);
        GraphHealthReportGenerator.generate(REPORT_OUTPUT, allScaleData);
        log.info("Report generated successfully.");

        // Verify the report exists and has content
        assertTrue(REPORT_OUTPUT.toFile().exists(), "Report file should exist");
        assertTrue(REPORT_OUTPUT.toFile().length() > 1000, "Report should have substantial content");

        // Verify basic quality gates on the largest scale point
        ScalePointData largest = allScaleData.getLast();
        assertNotNull(largest.snapshotInitial(), "Initial snapshot should exist");
        assertTrue(largest.snapshotInitial().totalMemories() > 0, "Should have ingested memories");
        assertTrue(largest.snapshotInitial().entityCount() > 0, "Should have entities");
        assertTrue(largest.cycles().size() == CYCLES_FULL_SCALE,
                "Should have run all " + CYCLES_FULL_SCALE + " cycles at max scale");

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("P0: GraphHealthMetrics Baseline Test COMPLETE ✓");
        log.info("═══════════════════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs one scale point: ingest → snapshot → N reflection cycles → snapshot.
     */
    private ScalePointData runScalePoint(String label, LoadedDataset dataset,
                                          EmbeddingProvider embedder, int cycles) {
        long startIngest = System.currentTimeMillis();

        try (var setup = new BenchmarkSetup()) {
            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            long ingestionMs = System.currentTimeMillis() - startIngest;

            log.info("[{}] Ingestion complete: {}ms, totalMemories={}", label, ingestionMs, memory.totalMemories());

            // Snapshot #0: post-ingestion, pre-reflection
            GraphSnapshot initial = GraphSnapshotCollector.capture(memory);
            log.info("[{}] Initial snapshot: {}", label, initial);

            // Run reflection cycles
            List<CycleDataPoint> cycleData = new ArrayList<>();
            for (int c = 1; c <= cycles; c++) {
                long cycleStart = System.currentTimeMillis();
                ReflectReport report = memory.reflect();
                long cycleMs = System.currentTimeMillis() - cycleStart;

                GraphSnapshot postCycle = GraphSnapshotCollector.capture(memory);

                cycleData.add(new CycleDataPoint(c, report, postCycle));

                // Log every cycle
                if (report.graphHealth() != null) {
                    log.info("[{}] Cycle {}/{}: {}ms | decayed={} surviving={} bridgeProt={} arousalMod={} frag={}",
                            label, c, cycles, cycleMs,
                            report.graphHealth().totalEdgesDecayed(),
                            report.graphHealth().totalEdgesSurviving(),
                            report.graphHealth().totalBridgeProtected(),
                            report.graphHealth().hebbianArousalModulated(),
                            String.format("%.4f", report.graphHealth().fragmentationRatio()));
                } else {
                    log.info("[{}] Cycle {}/{}: {}ms | consolidated={} tombstoned={} (no graph metrics)",
                            label, c, cycles, cycleMs,
                            report.consolidatedCount(), report.tombstonedCount());
                }
            }

            return new ScalePointData(label, dataset.corpus().size(), ingestionMs, initial, cycleData);
        }
    }

    /**
     * Creates a subset of the full dataset with the first N corpus records.
     * Filters hebbian edges, temporal chains, and entity relations to only include
     * references to the subset's memory IDs.
     */
    private LoadedDataset createSubset(LoadedDataset full, int targetSize) {
        int actualSize = Math.min(targetSize, full.corpus().size());
        List<BenchmarkCorpusRecord> subCorpus = full.corpus().subList(0, actualSize);

        // Collect valid memory IDs for this subset
        var validIds = new java.util.HashSet<String>();
        for (BenchmarkCorpusRecord r : subCorpus) {
            validIds.add(r.id());
        }

        // Filter Hebbian edges: both endpoints must be in subset
        List<HebbianEdgeDef> subHebbian = full.hebbianEdges().stream()
                .filter(e -> validIds.contains(e.memoryIdA()) && validIds.contains(e.memoryIdB()))
                .toList();

        // Filter temporal chains: all members must be in subset
        List<TemporalChainDef> subTemporal = full.temporalChains().stream()
                .filter(tc -> validIds.containsAll(tc.orderedMemoryIds()))
                .toList();

        // Filter entity relations: at least one sourceMemoryId must be in subset
        List<EntityRelation> subEntities = full.entityRelations().stream()
                .filter(er -> er.sourceMemoryIds().stream().anyMatch(validIds::contains))
                .toList();

        log.info("Subset created: corpus={}/{}, hebbian={}/{}, temporal={}/{}, entities={}/{}",
                subCorpus.size(), full.corpus().size(),
                subHebbian.size(), full.hebbianEdges().size(),
                subTemporal.size(), full.temporalChains().size(),
                subEntities.size(), full.entityRelations().size());

        return new LoadedDataset(
                subCorpus,
                full.queries(),
                full.qrels(),
                subEntities,
                subTemporal,
                subHebbian,
                full.persona()
        );
    }

    private static String formatLabel(int size) {
        if (size >= 1000) return (size / 1000) + "K";
        return String.valueOf(size);
    }
}
