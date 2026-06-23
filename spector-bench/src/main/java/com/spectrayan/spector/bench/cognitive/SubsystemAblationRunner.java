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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.ScoredResult;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.TextSearchMode;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Ablation study runner — measures the contribution of each subsystem by
 * disabling it and measuring the nDCG drop.
 *
 * <h3>Ablation Conditions</h3>
 * <ol>
 *   <li><b>FULL</b> — complete pipeline (control)</li>
 *   <li><b>NO_GRAPH</b> — disable Hebbian, Temporal, Entity graphs</li>
 *   <li><b>NO_IMPORTANCE</b> — SIMILARITY scoring mode (importance × decay disabled)</li>
 *   <li><b>NO_TAGS</b> — empty tag filter (Bloom filter bypassed)</li>
 *   <li><b>NO_VALENCE</b> — full valence range (valence filter bypassed)</li>
 *   <li><b>VECTOR_ONLY</b> — pure vector search, no BM25</li>
 *   <li><b>KEYWORD_ONLY</b> — pure BM25, no vector search</li>
 *   <li><b>NO_SALIENCE</b> — profile = NONE (salience profile disabled)</li>
 * </ol>
 *
 * <h3>Output</h3>
 * <p>TSV file with columns: condition, mean_nDCG, delta_from_full, pct_drop.
 * Shows which subsystems contribute most to retrieval quality.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... SubsystemAblationRunner <datasetDir> <outputDir>
 * }</pre>
 */
public final class SubsystemAblationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubsystemAblationRunner.class);

    /** The ablation conditions we test. */
    private enum AblationCondition {
        FULL("Complete pipeline (control)"),
        NO_GRAPH("Graphs disabled (Hebbian/Temporal/Entity)"),
        NO_IMPORTANCE("Similarity-only scoring (no importance/decay)"),
        NO_TAGS("Tag filter bypassed"),
        NO_VALENCE("Valence filter bypassed"),
        VECTOR_ONLY("Vector search only (no BM25)"),
        KEYWORD_ONLY("Keyword search only (no vectors)"),
        NO_SALIENCE("Salience profile disabled");

        private final String description;
        AblationCondition(String description) { this.description = description; }
        public String description() { return description; }
    }

    private final Path datasetDir;
    private final Path outputDir;

    public SubsystemAblationRunner(Path datasetDir, Path outputDir) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SubsystemAblationRunner <datasetDir> <outputDir>");
            System.exit(1);
            return;
        }
        new SubsystemAblationRunner(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Executes the full ablation study.
     */
    public void run() {
        log.info("═══ Subsystem Ablation Study ═══");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} queries, {} corpus records",
                dataset.queries().size(), dataset.corpus().size());

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            log.info("Memory created with {} memories", memory.totalMemories());

            MetricsComputer metrics = new MetricsComputer();

            // Results: condition → list of per-query nDCGs
            Map<AblationCondition, List<Double>> results = new LinkedHashMap<>();
            for (AblationCondition c : AblationCondition.values()) {
                results.put(c, new ArrayList<>());
            }

            // Run each query under each condition
            int queryCount = 0;
            for (BenchmarkQuery query : dataset.queries()) {
                Map<String, Integer> qrels = dataset.qrels().getOrDefault(query.id(), Map.of());
                if (qrels.isEmpty()) continue;

                queryCount++;
                for (AblationCondition condition : AblationCondition.values()) {
                    try {
                        RecallOptions options = buildAblationOptions(query, condition);
                        var cogResults = memory.recall(query.text(), options);
                        List<String> rankedIds = cogResults.stream()
                                .map(r -> r.id())
                                .toList();
                        double ndcg = metrics.ndcgAtK(rankedIds, qrels, 10);
                        results.get(condition).add(ndcg);
                    } catch (Exception e) {
                        log.warn("Query {} / {} failed: {}", query.id(), condition, e.getMessage());
                        results.get(condition).add(0.0);
                    }
                }

                if (queryCount % 10 == 0) {
                    log.info("Progress: {}/{} queries", queryCount, dataset.queries().size());
                }
            }

            writeAblationReport(results, queryCount);
            log.info("═══ Ablation Study Complete ═══");

        } catch (Exception e) {
            log.error("Ablation study failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds recall options for a specific ablation condition.
     */
    private RecallOptions buildAblationOptions(BenchmarkQuery query, AblationCondition condition) {
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(10)
                .recallMode(RecallMode.OBSERVE);

        switch (condition) {
            case FULL -> {
                // Control: full pipeline with all features
                builder.profile(query.cognitiveProfile());
                applyFilters(builder, query);
            }
            case NO_GRAPH -> {
                // Disable graph augmentation by setting threshold to 0.0
                builder.profile(query.cognitiveProfile());
                builder.graphExpansionThreshold(0.0f);
                applyFilters(builder, query);
            }
            case NO_IMPORTANCE -> {
                // Similarity-only scoring — importance and decay have no effect
                builder.profile(query.cognitiveProfile());
                builder.scoringMode(ScoringMode.SIMILARITY);
                applyFilters(builder, query);
            }
            case NO_TAGS -> {
                // Skip tag filtering — don't apply synaptic filter
                builder.profile(query.cognitiveProfile());
                // Deliberately omit synapticFilter
                if (query.minValence() != null) builder.minValence(query.minValence());
                if (query.maxValence() != null) builder.maxValence(query.maxValence());
            }
            case NO_VALENCE -> {
                // Full valence range — effectively disables valence filter
                builder.profile(query.cognitiveProfile());
                builder.minValence(Byte.MIN_VALUE);
                builder.maxValence(Byte.MAX_VALUE);
                if (!query.synapticFilterTags().isEmpty()) {
                    builder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
                }
            }
            case VECTOR_ONLY -> {
                // Pure vector search — no BM25
                builder.profile(query.cognitiveProfile());
                builder.textSearchMode(TextSearchMode.VECTOR_ONLY);
                applyFilters(builder, query);
            }
            case KEYWORD_ONLY -> {
                // Pure BM25 keyword search — no vector similarity
                builder.profile(query.cognitiveProfile());
                builder.textSearchMode(TextSearchMode.KEYWORD_ONLY);
                applyFilters(builder, query);
            }
            case NO_SALIENCE -> {
                // No salience profile — uses raw defaults (alpha=0.6, beta=0.4)
                // Deliberately omit profile
                applyFilters(builder, query);
            }
        }

        return builder.build();
    }

    /** Applies tag and valence filters from the query. */
    private void applyFilters(RecallOptions.Builder builder, BenchmarkQuery query) {
        if (!query.synapticFilterTags().isEmpty()) {
            builder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
        }
        if (query.minValence() != null) builder.minValence(query.minValence());
        if (query.maxValence() != null) builder.maxValence(query.maxValence());
    }

    private void writeAblationReport(Map<AblationCondition, List<Double>> results, int queryCount) {
        try {
            Files.createDirectories(outputDir);
            Path outFile = outputDir.resolve("ablation-results.tsv");

            // Compute FULL mean as baseline
            double fullMean = results.get(AblationCondition.FULL).stream()
                    .mapToDouble(d -> d).average().orElse(0.0);

            StringBuilder sb = new StringBuilder();
            sb.append("condition\tdescription\tmean_nDCG\tdelta\tpct_change\n");

            for (AblationCondition condition : AblationCondition.values()) {
                List<Double> ndcgs = results.get(condition);
                double mean = ndcgs.stream().mapToDouble(d -> d).average().orElse(0.0);
                double delta = mean - fullMean;
                double pctChange = fullMean > 0 ? (delta / fullMean) * 100.0 : 0.0;

                sb.append(String.format("%s\t%s\t%.4f\t%+.4f\t%+.1f%%%n",
                        condition.name(), condition.description(), mean, delta, pctChange));
            }

            Files.writeString(outFile, sb.toString());
            log.info("Ablation report written to {}", outFile);

            // Console summary
            System.out.println("\n══════════════════════════════════════════════════════════════");
            System.out.println("  ABLATION STUDY RESULTS");
            System.out.printf("  Queries: %d   |   Baseline (FULL) nDCG: %.4f%n", queryCount, fullMean);
            System.out.println("══════════════════════════════════════════════════════════════");
            System.out.printf("  %-20s  %8s  %8s  %8s%n", "Condition", "nDCG", "Δ", "%Δ");
            System.out.println("  " + "─".repeat(56));

            for (AblationCondition condition : AblationCondition.values()) {
                double mean = results.get(condition).stream().mapToDouble(d -> d).average().orElse(0.0);
                double delta = mean - fullMean;
                double pctChange = fullMean > 0 ? (delta / fullMean) * 100.0 : 0.0;
                String marker = condition == AblationCondition.FULL ? " ◆" :
                        (pctChange < -10 ? " ⚠" : "");
                System.out.printf("  %-20s  %8.4f  %+8.4f  %+7.1f%%%s%n",
                        condition.name(), mean, delta, pctChange, marker);
            }
            System.out.println("══════════════════════════════════════════════════════════════\n");

        } catch (IOException e) {
            log.error("Failed to write ablation report: {}", e.getMessage(), e);
        }
    }
}
