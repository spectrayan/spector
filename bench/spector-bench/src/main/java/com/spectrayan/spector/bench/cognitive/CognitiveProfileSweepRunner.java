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
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Sweeps all {@link CognitiveProfile} values across all queries to produce
 * an nDCG matrix showing which profile works best for each query type.
 *
 * <h3>Output</h3>
 * <p>Produces a TSV file with columns: queryId, expectedSubsystem, then one
 * nDCG column per profile (BALANCED, DEBUGGING, ..., DEFAULT_MODE_NETWORK).
 * Also produces a summary row with mean nDCG per profile and identifies
 * the optimal profile for each query category.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... CognitiveProfileSweepRunner <datasetDir> <outputDir>
 * }</pre>
 */
public final class CognitiveProfileSweepRunner {

    private static final Logger log = LoggerFactory.getLogger(CognitiveProfileSweepRunner.class);

    private final Path datasetDir;
    private final Path outputDir;

    public CognitiveProfileSweepRunner(Path datasetDir, Path outputDir) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: CognitiveProfileSweepRunner <datasetDir> <outputDir>");
            System.exit(1);
            return;
        }
        new CognitiveProfileSweepRunner(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Executes the full profile sweep.
     */
    public void run() {
        log.info("═══ Cognitive Profile Sweep ═══");

        // Load dataset
        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} queries", dataset.queries().size());

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            log.info("Memory created with {} memories", memory.totalMemories());

            CognitiveProfile[] profiles = CognitiveProfile.values();

            // Matrix: queryId → (profile → nDCG)
            Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();
            // Per-profile aggregate nDCG lists
            Map<String, List<Double>> profileNdcgs = new LinkedHashMap<>();
            for (CognitiveProfile p : profiles) {
                profileNdcgs.put(p.name(), new ArrayList<>());
            }

            MetricsComputer metrics = new MetricsComputer();

            for (BenchmarkQuery query : dataset.queries()) {
                Map<String, Double> row = new LinkedHashMap<>();
                Map<String, Integer> qrels = dataset.qrels().getOrDefault(query.id(), Map.of());

                if (qrels.isEmpty()) {
                    log.warn("No qrels for query {}, skipping", query.id());
                    continue;
                }

                for (CognitiveProfile profile : profiles) {
                    RecallOptions options = RecallOptions.builder()
                            .topK(10)
                            .recallMode(RecallMode.OBSERVE)
                            .profile(profile)
                            .build();

                    try {
                        var results = memory.recall(query.text(), options);
                        List<String> rankedIds = results.stream()
                                .map(r -> r.id())
                                .toList();
                        double ndcg = metrics.ndcgAtK(rankedIds, qrels, 10);
                        row.put(profile.name(), ndcg);
                        profileNdcgs.get(profile.name()).add(ndcg);
                    } catch (Exception e) {
                        log.warn("Query {} with profile {} failed: {}", query.id(), profile, e.getMessage());
                        row.put(profile.name(), 0.0);
                        profileNdcgs.get(profile.name()).add(0.0);
                    }
                }

                matrix.put(query.id(), row);
                log.debug("Query {} complete", query.id());
            }

            // Write results
            writeMatrix(matrix, profileNdcgs, profiles, dataset);
            log.info("═══ Profile Sweep Complete ═══");

        } catch (Exception e) {
            log.error("Profile sweep failed: {}", e.getMessage(), e);
        }
    }

    private void writeMatrix(Map<String, Map<String, Double>> matrix,
                             Map<String, List<Double>> profileNdcgs,
                             CognitiveProfile[] profiles,
                             LoadedDataset dataset) {
        try {
            Files.createDirectories(outputDir);
            Path outFile = outputDir.resolve("profile-sweep-matrix.tsv");

            StringBuilder sb = new StringBuilder();

            // Header
            sb.append("query_id\texpected_subsystem");
            for (CognitiveProfile p : profiles) {
                sb.append('\t').append(p.name());
            }
            sb.append("\tbest_profile\n");

            // Data rows
            for (var entry : matrix.entrySet()) {
                String queryId = entry.getKey();
                Map<String, Double> row = entry.getValue();

                // Find expected subsystem from the query
                String subsystem = dataset.queries().stream()
                        .filter(q -> q.id().equals(queryId))
                        .map(BenchmarkQuery::expectedSubsystem)
                        .findFirst().orElse("UNKNOWN");

                sb.append(queryId).append('\t').append(subsystem);

                String bestProfile = "BALANCED";
                double bestNdcg = -1;
                for (CognitiveProfile p : profiles) {
                    double ndcg = row.getOrDefault(p.name(), 0.0);
                    sb.append('\t').append(String.format("%.4f", ndcg));
                    if (ndcg > bestNdcg) {
                        bestNdcg = ndcg;
                        bestProfile = p.name();
                    }
                }
                sb.append('\t').append(bestProfile).append('\n');
            }

            // Summary row (mean nDCG per profile)
            sb.append("MEAN\t");
            String bestOverall = "BALANCED";
            double bestOverallNdcg = -1;
            for (CognitiveProfile p : profiles) {
                List<Double> ndcgs = profileNdcgs.get(p.name());
                double mean = ndcgs.stream().mapToDouble(d -> d).average().orElse(0.0);
                sb.append('\t').append(String.format("%.4f", mean));
                if (mean > bestOverallNdcg) {
                    bestOverallNdcg = mean;
                    bestOverall = p.name();
                }
            }
            sb.append('\t').append(bestOverall).append('\n');

            Files.writeString(outFile, sb.toString());
            log.info("Profile sweep matrix written to {}", outFile);

            // Print summary to console
            System.out.println("\n══════════════════════════════════════════════════");
            System.out.println("  PROFILE SWEEP SUMMARY");
            System.out.println("══════════════════════════════════════════════════");
            for (CognitiveProfile p : profiles) {
                List<Double> ndcgs = profileNdcgs.get(p.name());
                double mean = ndcgs.stream().mapToDouble(d -> d).average().orElse(0.0);
                String marker = p.name().equals(bestOverall) ? " ★ BEST" : "";
                System.out.printf("  %-25s  nDCG: %.4f%s%n", p.name(), mean, marker);
            }
            System.out.println("══════════════════════════════════════════════════\n");

        } catch (IOException e) {
            log.error("Failed to write profile sweep matrix: {}", e.getMessage(), e);
        }
    }
}
