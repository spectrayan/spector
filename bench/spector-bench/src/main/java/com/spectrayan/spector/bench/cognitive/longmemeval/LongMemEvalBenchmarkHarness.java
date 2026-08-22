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
package com.spectrayan.spector.bench.cognitive.longmemeval;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.BenchmarkSetup;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkExitCode;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.UserContext;
import com.spectrayan.spector.memory.pipeline.gatherer.UserContextAssembler;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * CLI Harness for executing the LongMemEval Benchmark.
 */
public final class LongMemEvalBenchmarkHarness {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalBenchmarkHarness.class);

    private final Path datasetDir;
    private final Path outputDir;
    private final int topK;

    public LongMemEvalBenchmarkHarness(Path datasetDir, Path outputDir, int topK) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
        this.topK = topK;
    }

    public static void main(String[] args) {
        String defaultDataDir = System.getProperty(
                "datasetDir",
                System.getProperty("spector.bench.dataset.dir",
                        System.getenv().getOrDefault("DATASET_DIR", "../spector-datasets/longmemeval/data")));
        Path datasetDir = Paths.get(defaultDataDir);
        Path outputDir = Paths.get(System.getProperty("outputDir", "target/benchmark-results/longmemeval"));
        int topK = 10;

        if (args.length >= 1) datasetDir = Paths.get(args[0]);
        if (args.length >= 2) outputDir = Paths.get(args[1]);
        if (args.length >= 3) topK = Integer.parseInt(args[2]);

        LongMemEvalBenchmarkHarness harness = new LongMemEvalBenchmarkHarness(datasetDir, outputDir, topK);
        BenchmarkExitCode exitCode = harness.run();
        System.exit(exitCode.code());
    }

    public BenchmarkExitCode run() {
        log.info("Starting LongMemEval Benchmark Run on dataset {}", datasetDir);
        try {
            DatasetLoader loader = new DatasetLoader();
            LoadedDataset dataset = loader.load(datasetDir);

            EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();
            Path cacheFile = datasetDir.resolve("embeddings.bin");

            try (BenchmarkSetup setup = new BenchmarkSetup();
                 EmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {

                SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir);
                log.info("LongMemEval SpectorMemory instance created with {} records.", memory.totalMemories());

                UserContextAssembler contextAssembler = new UserContextAssembler(
                        memory.admin().temporalKnowledgeGraph(),
                        memory.admin().entityDirectory());

                List<BenchmarkQuery> queries = dataset.queries();
                int evaluatedCount = 0;

                for (BenchmarkQuery q : queries) {
                    RecallOptions options = RecallOptions.builder()
                            .recallMode(RecallMode.OBSERVE)
                            .scoringMode(ScoringMode.COGNITIVE)
                            .enableMmr(true)
                            .topK(topK)
                            .build();

                    List<CognitiveResult> results = memory.recall(q.text(), options);
                    UserContext userContext = contextAssembler.assemble(results, null);

                    log.info("LongMemEval Query [{}] '{}'  --  Retrieved {} results, UserContext assembled {} chunks and {} beliefs",
                            q.id(), q.text(), results.size(), userContext.relevantChunks().size(), userContext.beliefs().size());
                    evaluatedCount++;
                }

                log.info("LongMemEval Evaluation Finished. Total Queries Evaluated: {}", evaluatedCount);
                return BenchmarkExitCode.SUCCESS;
            }
        } catch (Exception e) {
            log.error("LongMemEval Benchmark Execution Failed: {}", e.getMessage(), e);
            return BenchmarkExitCode.SETUP_FAILED;
        }
    }
}
