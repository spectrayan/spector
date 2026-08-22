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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConflictMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * Executes the 10-condition AISME Phase 1–7 Sub-Relay Differential Benchmarking Matrix
 * against the Balanced Baseline dataset to isolate and evaluate each individual biological
 * cognitive subsystem (ADR-0009).
 */
public final class AismeRelayMatrixRunner {

    private static final Logger log = LoggerFactory.getLogger(AismeRelayMatrixRunner.class);

    private final Path datasetDir;
    private final Path outputDir;
    private final int topK;
    private final MetricsComputer metricsComputer;

    public record BenchmarkCondition(
            String id,
            String name,
            String phaseDescription,
            AismeConfig config,
            ConflictMode conflictMode,
            float minTrustScore
    ) {}

    public record ConditionResult(
            BenchmarkCondition condition,
            double meanNdcg,
            double meanMrr,
            double meanRecall,
            double cohensD,
            double pValue,
            double avgLatencyMs,
            Map<String, Double> perQueryNdcg
    ) {}

    public AismeRelayMatrixRunner(Path datasetDir, Path outputDir, int topK) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
        this.topK = topK > 0 ? topK : 10;
        this.metricsComputer = new MetricsComputer();
    }

    public static void main(String[] args) {
        String dataDir = args.length >= 1 ? args[0] : System.getProperty("datasetDir", "d:/git/spector-datasets/balanced-baseline/data");
        String outDir = args.length >= 2 ? args[1] : System.getProperty("outputDir", "d:/git/spector-datasets/balanced-baseline/results");
        int topK = args.length >= 3 ? Integer.parseInt(args[2]) : Integer.parseInt(System.getProperty("topK", "10"));
        new AismeRelayMatrixRunner(Path.of(dataDir), Path.of(outDir), topK).run();
    }

    public List<ConditionResult> run() {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  AISME Phase 1–7 Sub-Relay Differential Benchmark Matrix           ║");
        log.info("║  Dataset: {} (TopK={})                                             ║", datasetDir, topK);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} corpus records, {} queries",
                dataset.corpus().size(), dataset.queries().size());

        List<BenchmarkCondition> conditions = defineConditions();
        List<ConditionResult> results = new ArrayList<>();

        Path cacheFile = datasetDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b");

        try (CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {
            ConditionResult baselineResult = null;

            for (int i = 0; i < conditions.size(); i++) {
                BenchmarkCondition cond = conditions.get(i);
                log.info("\n▶ Running Condition {}/{}: [{}] {}", i + 1, conditions.size(), cond.id(), cond.name());

                try (BenchmarkSetup setup = new BenchmarkSetup()) {
                    SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir, cond.config());

                    List<Double> ndcgList = new ArrayList<>();
                    List<Double> mrrList = new ArrayList<>();
                    List<Double> recallList = new ArrayList<>();
                    List<Double> latencies = new ArrayList<>();
                    Map<String, Double> perQueryNdcg = new LinkedHashMap<>();

                    for (BenchmarkQuery query : dataset.queries()) {
                        long qStart = System.nanoTime();

                        RecallOptions.Builder optBuilder = RecallOptions.builder()
                                .topK(topK)
                                .recallMode(RecallMode.OBSERVE)
                                .profile(CognitiveProfile.BALANCED);

                        if (cond.conflictMode() != null) {
                            optBuilder.conflictMode(cond.conflictMode());
                        }
                        if (cond.minTrustScore() > 0.0f) {
                            optBuilder.minTrustScore(cond.minTrustScore());
                        }

                        List<CognitiveResult> recallResults = memory.recall(query.text(), optBuilder.build());
                        long qElapsed = System.nanoTime() - qStart;
                        latencies.add(qElapsed / 1_000_000.0);

                        List<String> rankedIds = new ArrayList<>();
                        for (CognitiveResult cr : recallResults) {
                            rankedIds.add(cr.id());
                        }

                        Map<String, Integer> qrelsForQuery = dataset.qrels().getOrDefault(query.id(), Map.of());
                        double ndcg = metricsComputer.ndcgAtK(rankedIds, qrelsForQuery, topK);
                        double mrr = metricsComputer.mrrAtK(rankedIds, qrelsForQuery, topK);
                        double rec = metricsComputer.recallAtK(rankedIds, qrelsForQuery, topK);

                        ndcgList.add(ndcg);
                        mrrList.add(mrr);
                        recallList.add(rec);
                        perQueryNdcg.put(query.id(), ndcg);
                    }

                    double meanNdcg = ndcgList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double meanMrr = mrrList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double meanRec = recallList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                    double cohensD = 0.0;
                    double pVal = 1.0;
                    if (baselineResult != null) {
                        double[] baseArray = baselineResult.perQueryNdcg().values().stream().mapToDouble(Double::doubleValue).toArray();
                        double[] condArray = ndcgList.stream().mapToDouble(Double::doubleValue).toArray();
                        cohensD = StatisticalTests.cohensD(baseArray, condArray);
                        pVal = StatisticalTests.pairedTTestPValue(baseArray, condArray);
                    }

                    ConditionResult cr = new ConditionResult(
                            cond, meanNdcg, meanMrr, meanRec, cohensD, pVal, avgLatency, perQueryNdcg
                    );

                    if (baselineResult == null) {
                        baselineResult = cr;
                    }
                    results.add(cr);

                    log.info("  ✓ nDCG@{}: {:.4f} | MRR: {:.4f} | Recall: {:.4f} | Cohen's d: {:+.3f} | Latency: {:.2f}ms",
                            topK, meanNdcg, meanMrr, meanRec, cohensD, avgLatency);
                }
            }
        }

        try {
            generateReport(results);
        } catch (IOException e) {
            log.error("Failed to generate AISME differential report", e);
        }

        return results;
    }

    private List<BenchmarkCondition> defineConditions() {
        List<BenchmarkCondition> list = new ArrayList<>();

        // 0. Baseline (No AISME)
        list.add(new BenchmarkCondition(
                "CONFIG_0", "Baseline (No AISME)",
                "Standard Cognitive Pathway without active inference self-modeling",
                AismeConfig.disabled(), null, 0.0f));

        // 1. Full AISME (All 7 Phases)
        list.add(new BenchmarkCondition(
                "CONFIG_1", "Full AISME (All 7 Phases)",
                "Full generative self-model with homeostatic, free-energy, Hopfield, manifold, predictive coding, continuity, and workspace relays",
                AismeConfig.defaultConfig(), null, 0.0f));

        // 2. Phase 1 Only (Homeostasis)
        list.add(new BenchmarkCondition(
                "CONFIG_2", "Phase 1 Only: Homeostatic Bias",
                "Homeostatic Core & Affective Resonance Bias Relay",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(true)
                        .enableFreeEnergy(false)
                        .enableHopfield(false)
                        .enableManifold(false)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 3. Phase 2 Only (Free Energy Minimization)
        list.add(new BenchmarkCondition(
                "CONFIG_3", "Phase 2 Only: Free Energy Guided",
                "Variational Free Energy Minimization (G = epistemic + pragmatic)",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(true)
                        .enableHopfield(false)
                        .enableManifold(false)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 4. Phase 3 Only (Modern Hopfield Attractors)
        list.add(new BenchmarkCondition(
                "CONFIG_4", "Phase 3 Only: Hopfield Attractors",
                "Dense Associative Hopfield Energy Dynamic & Memory Basins",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(false)
                        .enableHopfield(true)
                        .enableManifold(false)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 5. Phase 4 Only (Riemannian Manifold Geodesic)
        list.add(new BenchmarkCondition(
                "CONFIG_5", "Phase 4 Only: Riemannian Manifold",
                "Cognitive Manifold Geodesic Distance Rerank Relay",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(false)
                        .enableHopfield(false)
                        .enableManifold(true)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 6. Phase 5 Only (Predictive Coding & Simulation)
        list.add(new BenchmarkCondition(
                "CONFIG_6", "Phase 5 Only: Predictive Coding",
                "Hierarchical Predictive Coding & Constructive Simulation Relay",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(false)
                        .enableHopfield(false)
                        .enableManifold(false)
                        .enablePredictiveCoding(true)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 7. Phase 6 Only (Consciousness Continuity / Phi)
        list.add(new BenchmarkCondition(
                "CONFIG_7", "Phase 6 Only: Consciousness Continuity",
                "Temporal Consciousness Continuity Metric & Phi Evaluation",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(false)
                        .enableHopfield(false)
                        .enableManifold(false)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(true)
                        .enableGlobalWorkspace(false)
                        .build(), null, 0.0f));

        // 8. Phase 7 Only (Global Workspace Conscious Access)
        list.add(new BenchmarkCondition(
                "CONFIG_8", "Phase 7 Only: Global Workspace",
                "Limited-Capacity Global Neuronal Workspace Conscious Access Gateway",
                AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(false)
                        .enableFreeEnergy(false)
                        .enableHopfield(false)
                        .enableManifold(false)
                        .enablePredictiveCoding(false)
                        .enableConsciousnessContinuity(false)
                        .enableGlobalWorkspace(true)
                        .build(), null, 0.0f));

        // 9. TANGLE + GPM Fail-Closed Release Gate
        list.add(new BenchmarkCondition(
                "CONFIG_9", "TANGLE + GPM Gate",
                "Multi-Evidence Conflict Resolution + Fail-Closed Governed Release Gate",
                AismeConfig.disabled(), ConflictMode.MULTI_EVIDENCE, 0.50f));

        return list;
    }

    private void generateReport(List<ConditionResult> results) throws IOException {
        Files.createDirectories(outputDir);
        Path reportFile = outputDir.resolve("aisme_relay_benchmark_report.md");
        Path jsonFile = outputDir.resolve("aisme_matrix.json");

        StringBuilder md = new StringBuilder();
        md.append("# 🧬 AISME Phase 1–7 Sub-Relay Differential Benchmark Report\n\n");
        md.append("**Dataset:** `balanced-baseline` (11,367 records, 19 queries, 365-day narrative timeline)\n");
        md.append("**Generated:** ").append(java.time.Instant.now()).append("\n");
        md.append("**Architecture:** Java 25 Panama Direct Off-Heap • AVX-512 Fused SIMD • Cognitive Pathways\n\n");

        md.append("## 1. Differential Summary Matrix\n\n");
        md.append("| # | Condition | nDCG@10 | MRR@10 | Recall@10 | Cohen's d (vs Base) | p-value | Avg Latency |\n");
        md.append("|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|\n");

        for (ConditionResult cr : results) {
            md.append(String.format("| **%s** | %s | **%.4f** | %.4f | %.4f | %+.3f | %.4f | %.2f ms |\n",
                    cr.condition().id(), cr.condition().name(),
                    cr.meanNdcg(), cr.meanMrr(), cr.meanRecall(),
                    cr.cohensD(), cr.pValue(), cr.avgLatencyMs()));
        }

        md.append("\n## 2. Biological Cognitive Subsystem Descriptions\n\n");
        for (ConditionResult cr : results) {
            md.append(String.format("### %s: %s\n", cr.condition().id(), cr.condition().name()));
            md.append("- **Mechanism:** ").append(cr.condition().phaseDescription()).append("\n");
            md.append(String.format("- **Performance:** nDCG@10 = `%.4f`, MRR@10 = `%.4f`, Recall@10 = `%.4f`, Latency = `%.2f ms`\n\n",
                    cr.meanNdcg(), cr.meanMrr(), cr.meanRecall(), cr.avgLatencyMs()));
        }

        Files.writeString(reportFile, md.toString(), StandardCharsets.UTF_8);
        log.info("Saved differential benchmark report to {}", reportFile);

        // Simple JSON export
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < results.size(); i++) {
            ConditionResult cr = results.get(i);
            json.append(String.format("  {\"id\": \"%s\", \"name\": \"%s\", \"ndcg\": %.4f, \"mrr\": %.4f, \"recall\": %.4f, \"latencyMs\": %.2f}%s\n",
                    cr.condition().id(), cr.condition().name(), cr.meanNdcg(), cr.meanMrr(), cr.meanRecall(), cr.avgLatencyMs(),
                    i < results.size() - 1 ? "," : ""));
        }
        json.append("]\n");
        Files.writeString(jsonFile, json.toString(), StandardCharsets.UTF_8);
        log.info("Saved differential matrix JSON to {}", jsonFile);
    }
}
