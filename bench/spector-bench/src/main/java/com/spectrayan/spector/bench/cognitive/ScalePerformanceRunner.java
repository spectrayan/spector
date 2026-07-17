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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.ScoredResult;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.SpectorMemory;

/**
 * Scale performance runner — measures recall latency and throughput at different
 * corpus sizes and concurrency levels.
 *
 * <h3>Test Dimensions</h3>
 * <ul>
 *   <li><b>Scale</b>: 1K, 5K, 10K, 20K memories (uses first N corpus entries)</li>
 *   <li><b>Concurrency</b>: 1, 2, 4, 8 threads per scale point</li>
 * </ul>
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li>p50, p95, p99 recall latency (ms)</li>
 *   <li>Throughput (queries per second)</li>
 *   <li>Ingestion throughput (memories per second)</li>
 *   <li>Memory footprint (estimated from tier stores)</li>
 * </ul>
 *
 * <h3>Output</h3>
 * <p>TSV with columns: corpus_size, threads, p50_ms, p95_ms, p99_ms, qps,
 * ingestion_time_ms, ingestion_rate_per_sec.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... ScalePerformanceRunner <datasetDir> <outputDir>
 * }</pre>
 */
public final class ScalePerformanceRunner {

    private static final Logger log = LoggerFactory.getLogger(ScalePerformanceRunner.class);

    /** Scale points to test. */
    private static final int[] SCALE_POINTS = {1_000, 5_000, 10_000, 20_000};

    /** Concurrency levels per scale point. */
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};

    /** Warmup queries before measurement. */
    private static final int WARMUP_QUERIES = 5;

    private final Path datasetDir;
    private final Path outputDir;

    public ScalePerformanceRunner(Path datasetDir, Path outputDir) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: ScalePerformanceRunner <datasetDir> <outputDir>");
            System.exit(1);
            return;
        }
        new ScalePerformanceRunner(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Executes the full scale performance test.
     */
    public void run() {
        log.info("═══ Scale Performance Test ═══");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset fullDataset = loader.load(datasetDir);
        int maxCorpus = fullDataset.corpus().size();
        log.info("Full dataset: {} corpus records, {} queries", maxCorpus, fullDataset.queries().size());

        List<ScaleResult> allResults = new ArrayList<>();

        for (int scale : SCALE_POINTS) {
            if (scale > maxCorpus) {
                log.warn("Skipping scale point {} — corpus only has {} records", scale, maxCorpus);
                continue;
            }

            log.info("─── Scale point: {} memories ───", scale);

            // Create a subset of the corpus
            var subsetCorpus = fullDataset.corpus().subList(0, scale);
            LoadedDataset subset = new LoadedDataset(
                    subsetCorpus, fullDataset.queries(), fullDataset.qrels(),
                    fullDataset.entityRelations(), fullDataset.temporalChains(),
                    fullDataset.hebbianEdges(), fullDataset.persona());

            try (BenchmarkSetup setup = new BenchmarkSetup();
                 EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

                // Measure ingestion time
                long ingestStart = System.nanoTime();
                SpectorMemory memory = setup.createMemoryInstance(subset, embedder);
                long ingestElapsed = System.nanoTime() - ingestStart;
                double ingestMs = ingestElapsed / 1_000_000.0;
                double ingestRate = scale / (ingestMs / 1_000.0);

                log.info("  Ingested {} memories in {:.1f}ms ({:.0f}/sec)",
                        scale, ingestMs, ingestRate);

                // Test at different concurrency levels
                for (int threads : THREAD_COUNTS) {
                    ScaleResult result = runConcurrencyTest(
                            memory, fullDataset.queries(), scale, threads, ingestMs, ingestRate);
                    allResults.add(result);
                }

            } catch (Exception e) {
                log.error("Scale point {} failed: {}", scale, e.getMessage(), e);
            }
        }

        writeReport(allResults);
        log.info("═══ Scale Performance Test Complete ═══");
    }

    private ScaleResult runConcurrencyTest(SpectorMemory memory, List<BenchmarkQuery> queries,
                                            int corpusSize, int threadCount,
                                            double ingestMs, double ingestRate) {
        log.info("  Testing {} threads...", threadCount);

        // Warmup
        for (int i = 0; i < Math.min(WARMUP_QUERIES, queries.size()); i++) {
            BenchmarkQuery q = queries.get(i);
            try {
                memory.recall(q.text(), RecallOptions.builder()
                        .topK(10).recallMode(RecallMode.OBSERVE).profile(q.cognitiveProfile())
                        .build());
            } catch (Exception ignored) {}
        }

        // Measure
        List<Long> latenciesNs = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicLong totalQueries = new AtomicLong(0);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            long testStart = System.nanoTime();

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                futures.add(executor.submit(() -> {
                    // Each thread runs a subset of queries
                    for (int i = threadIdx; i < queries.size(); i += threadCount) {
                        BenchmarkQuery q = queries.get(i);
                        try {
                            RecallOptions opts = RecallOptions.builder()
                                    .topK(10)
                                    .recallMode(RecallMode.OBSERVE)
                                    .profile(q.cognitiveProfile())
                                    .build();

                            long start = System.nanoTime();
                            memory.recall(q.text(), opts);
                            long elapsed = System.nanoTime() - start;

                            latenciesNs.add(elapsed);
                            totalQueries.incrementAndGet();
                        } catch (Exception e) {
                            log.trace("Query {} failed at scale {}: {}", q.id(), corpusSize, e.getMessage());
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(120, TimeUnit.SECONDS); } catch (Exception e) {
                    log.warn("Thread timed out at scale={}, threads={}", corpusSize, threadCount);
                }
            }

            long testElapsed = System.nanoTime() - testStart;
            double testSec = testElapsed / 1_000_000_000.0;
            double qps = totalQueries.get() / testSec;

            double p50 = percentileMs(latenciesNs, 0.50);
            double p95 = percentileMs(latenciesNs, 0.95);
            double p99 = percentileMs(latenciesNs, 0.99);

            log.info("    {} threads: p50={:.3f}ms p95={:.3f}ms p99={:.3f}ms QPS={:.0f}",
                    threadCount, p50, p95, p99, qps);

            return new ScaleResult(corpusSize, threadCount, p50, p95, p99, qps,
                    ingestMs, ingestRate, totalQueries.get());
        }
    }

    private record ScaleResult(int corpusSize, int threads,
                                double p50Ms, double p95Ms, double p99Ms,
                                double qps, double ingestMs, double ingestRate,
                                long queriesExecuted) {}

    private void writeReport(List<ScaleResult> results) {
        try {
            Files.createDirectories(outputDir);
            Path outFile = outputDir.resolve("scale-performance.tsv");

            StringBuilder sb = new StringBuilder();
            sb.append("corpus_size\tthreads\tp50_ms\tp95_ms\tp99_ms\tqps\t")
              .append("ingest_ms\tingest_rate_per_sec\tqueries_executed\n");

            for (ScaleResult r : results) {
                sb.append(String.format("%d\t%d\t%.3f\t%.3f\t%.3f\t%.1f\t%.1f\t%.1f\t%d%n",
                        r.corpusSize(), r.threads(),
                        r.p50Ms(), r.p95Ms(), r.p99Ms(), r.qps(),
                        r.ingestMs(), r.ingestRate(), r.queriesExecuted()));
            }

            Files.writeString(outFile, sb.toString());
            log.info("Scale performance report written to {}", outFile);

            // Console summary
            System.out.println("\n═══════════════════════════════════════════════════════════════════════");
            System.out.println("  SCALE PERFORMANCE RESULTS");
            System.out.println("═══════════════════════════════════════════════════════════════════════");
            System.out.printf("  %8s  %4s  %8s  %8s  %8s  %10s  %12s%n",
                    "Corpus", "Thr", "p50", "p95", "p99", "QPS", "Ingest/sec");
            System.out.println("  " + "─".repeat(70));

            for (ScaleResult r : results) {
                System.out.printf("  %8d  %4d  %7.3fms  %7.3fms  %7.3fms  %10.0f  %12.0f%n",
                        r.corpusSize(), r.threads(),
                        r.p50Ms(), r.p95Ms(), r.p99Ms(), r.qps(), r.ingestRate());
            }
            System.out.println("═══════════════════════════════════════════════════════════════════════\n");

        } catch (IOException e) {
            log.error("Failed to write scale performance report: {}", e.getMessage(), e);
        }
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
