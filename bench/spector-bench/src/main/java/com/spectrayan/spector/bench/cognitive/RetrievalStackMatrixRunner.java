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
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.TextSearchMode;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Retrieval stack matrix runner — tests all {@link TextSearchMode} values
 * across all queries to measure nDCG and latency for each retrieval configuration.
 *
 * <h3>Modes Tested</h3>
 * <ul>
 *   <li>{@code HYBRID} — BM25 + Vector (default)</li>
 *   <li>{@code VECTOR_ONLY} — Pure semantic similarity</li>
 *   <li>{@code KEYWORD_ONLY} — Pure BM25 keyword search</li>
 *   <li>{@code SPLADE} — SPLADE learned sparse retrieval only (if available)</li>
 *   <li>{@code SPLADE_HYBRID} — SPLADE + Vector (if available)</li>
 *   <li>{@code COLBERT_RERANK} — ColBERT reranking (if available)</li>
 *   <li>{@code FULL_STACK} — All layers active (if available)</li>
 * </ul>
 *
 * <h3>Output</h3>
 * <p>TSV with columns: mode, mean_nDCG, p50_latency_ms, p99_latency_ms,
 * queries_executed, queries_failed.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... RetrievalStackMatrixRunner <datasetDir> <outputDir>
 * }</pre>
 */
public final class RetrievalStackMatrixRunner {

    private static final Logger log = LoggerFactory.getLogger(RetrievalStackMatrixRunner.class);

    private final Path datasetDir;
    private final Path outputDir;

    public RetrievalStackMatrixRunner(Path datasetDir, Path outputDir) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: RetrievalStackMatrixRunner <datasetDir> <outputDir>");
            System.exit(1);
            return;
        }
        new RetrievalStackMatrixRunner(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Executes the retrieval stack matrix test.
     */
    public void run() {
        log.info("═══ Retrieval Stack Matrix Test ═══");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} queries", dataset.queries().size());

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            log.info("Memory created with {} memories", memory.totalMemories());

            MetricsComputer metrics = new MetricsComputer();

            // Modes to test — only include modes that don't require missing providers
            TextSearchMode[] modes = {
                    TextSearchMode.HYBRID,
                    TextSearchMode.VECTOR_ONLY,
                    TextSearchMode.KEYWORD_ONLY,
                    // SPLADE, SPLADE_HYBRID, COLBERT_RERANK, FULL_STACK
                    // included but will degrade gracefully if providers unavailable
                    TextSearchMode.SPLADE,
                    TextSearchMode.SPLADE_HYBRID,
                    TextSearchMode.COLBERT_RERANK,
                    TextSearchMode.FULL_STACK
            };

            Map<TextSearchMode, ModeResult> results = new LinkedHashMap<>();

            for (TextSearchMode mode : modes) {
                log.info("Testing mode: {}", mode);
                List<Double> ndcgs = new ArrayList<>();
                List<Long> latenciesNs = new ArrayList<>();
                int failed = 0;

                for (BenchmarkQuery query : dataset.queries()) {
                    Map<String, Integer> qrels = dataset.qrels().getOrDefault(query.id(), Map.of());
                    if (qrels.isEmpty()) continue;

                    try {
                        RecallOptions options = RecallOptions.builder()
                                .topK(10)
                                .recallMode(RecallMode.OBSERVE)
                                .profile(query.cognitiveProfile())
                                .textSearchMode(mode)
                                .build();

                        long start = System.nanoTime();
                        var cogResults = memory.recall(query.text(), options);
                        long elapsed = System.nanoTime() - start;

                        List<String> rankedIds = cogResults.stream()
                                .map(r -> r.id())
                                .toList();

                        double ndcg = metrics.ndcgAtK(rankedIds, qrels, 10);
                        ndcgs.add(ndcg);
                        latenciesNs.add(elapsed);
                    } catch (Exception e) {
                        failed++;
                        log.debug("Query {} with mode {} failed: {}", query.id(), mode, e.getMessage());
                    }
                }

                results.put(mode, new ModeResult(ndcgs, latenciesNs, failed));
                log.info("  {} → nDCG={:.4f}, queries={}, failed={}",
                        mode, mean(ndcgs), ndcgs.size(), failed);
            }

            writeReport(results);
            log.info("═══ Retrieval Stack Matrix Complete ═══");

        } catch (Exception e) {
            log.error("Retrieval stack matrix failed: {}", e.getMessage(), e);
        }
    }

    private record ModeResult(List<Double> ndcgs, List<Long> latenciesNs, int failed) {}

    private void writeReport(Map<TextSearchMode, ModeResult> results) {
        try {
            Files.createDirectories(outputDir);
            Path outFile = outputDir.resolve("retrieval-stack-matrix.tsv");

            StringBuilder sb = new StringBuilder();
            sb.append("mode\tmean_nDCG\tp50_latency_ms\tp99_latency_ms\tqueries_ok\tqueries_failed\n");

            for (var entry : results.entrySet()) {
                TextSearchMode mode = entry.getKey();
                ModeResult result = entry.getValue();

                double meanNdcg = mean(result.ndcgs());
                double p50 = percentileMs(result.latenciesNs(), 0.50);
                double p99 = percentileMs(result.latenciesNs(), 0.99);

                sb.append(String.format("%s\t%.4f\t%.3f\t%.3f\t%d\t%d%n",
                        mode.name(), meanNdcg, p50, p99,
                        result.ndcgs().size(), result.failed()));
            }

            Files.writeString(outFile, sb.toString());
            log.info("Retrieval stack matrix written to {}", outFile);

            // Console summary
            System.out.println("\n══════════════════════════════════════════════════════════════════");
            System.out.println("  RETRIEVAL STACK MATRIX");
            System.out.println("══════════════════════════════════════════════════════════════════");
            System.out.printf("  %-20s  %8s  %10s  %10s  %5s%n",
                    "Mode", "nDCG", "p50 (ms)", "p99 (ms)", "OK");
            System.out.println("  " + "─".repeat(60));

            for (var entry : results.entrySet()) {
                ModeResult r = entry.getValue();
                System.out.printf("  %-20s  %8.4f  %10.3f  %10.3f  %5d%n",
                        entry.getKey().name(),
                        mean(r.ndcgs()),
                        percentileMs(r.latenciesNs(), 0.50),
                        percentileMs(r.latenciesNs(), 0.99),
                        r.ndcgs().size());
            }
            System.out.println("══════════════════════════════════════════════════════════════════\n");

        } catch (IOException e) {
            log.error("Failed to write retrieval matrix report: {}", e.getMessage(), e);
        }
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private static double percentileMs(List<Long> nanosValues, double pct) {
        if (nanosValues.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(nanosValues);
        sorted.sort(Long::compare);
        int idx = (int) Math.ceil(pct * sorted.size()) - 1;
        idx = Math.clamp(idx, 0, sorted.size() - 1);
        return sorted.get(idx) / 1_000_000.0;
    }
}
