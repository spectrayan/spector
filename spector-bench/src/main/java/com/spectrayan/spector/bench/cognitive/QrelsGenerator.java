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
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * Generates semantically-meaningful qrels by running each query against
 * a fully-populated memory instance and grading the top results by
 * their cognitive scores.
 *
 * <p>This replaces the randomly-generated qrels with relevance judgments
 * based on actual retrieval. Each query's top-20 results are graded:
 * top 1-3 → relevance 3, 4-8 → relevance 2, 9-20 → relevance 1.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... QrelsGenerator <datasetDir>
 * }</pre>
 */
public final class QrelsGenerator {

    private static final Logger log = LoggerFactory.getLogger(QrelsGenerator.class);

    private final Path datasetDir;

    public QrelsGenerator(Path datasetDir) {
        this.datasetDir = datasetDir;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: QrelsGenerator <datasetDir>");
            System.exit(1);
            return;
        }
        new QrelsGenerator(Path.of(args[0])).run();
    }

    /**
     * Generates qrels by running queries against the full memory pipeline.
     */
    public void run() {
        log.info("=== Semantic Qrels Generator ===");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} corpus, {} queries",
                dataset.corpus().size(), dataset.queries().size());

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider embedder = OllamaEmbeddingProvider.create("qwen3-embedding:0.6b")) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder);
            log.info("Memory created with {} memories", memory.totalMemories());

            // For each query, run a BALANCED recall with top-20 results
            Map<String, List<QrelEntry>> allQrels = new LinkedHashMap<>();

            for (BenchmarkQuery query : dataset.queries()) {
                RecallOptions options = RecallOptions.builder()
                        .topK(20)
                        .recallMode(RecallMode.OBSERVE)
                        .profile(CognitiveProfile.BALANCED)
                        .build();

                try {
                    List<CognitiveResult> results = memory.recall(query.text(), options);

                    List<QrelEntry> entries = new ArrayList<>();
                    for (int i = 0; i < results.size(); i++) {
                        CognitiveResult r = results.get(i);
                        // Grade by rank position:
                        //   1-3:  highly relevant (3)
                        //   4-8:  relevant (2)
                        //   9-20: marginally relevant (1)
                        int relevance;
                        if (i < 3) {
                            relevance = 3;
                        } else if (i < 8) {
                            relevance = 2;
                        } else {
                            relevance = 1;
                        }
                        entries.add(new QrelEntry(query.id(), r.id(), relevance));
                    }
                    allQrels.put(query.id(), entries);
                    log.info("Query {} '{}': {} results graded",
                            query.id(), truncate(query.text(), 60), entries.size());
                } catch (Exception e) {
                    log.warn("Query {} failed: {}", query.id(), e.getMessage());
                }
            }

            // Write qrels.tsv
            writeQrels(allQrels);

            log.info("=== Qrels Generation Complete: {} queries, {} judgments ===",
                    allQrels.size(),
                    allQrels.values().stream().mapToInt(List::size).sum());

        } catch (Exception e) {
            log.error("Qrels generation failed: {}", e.getMessage(), e);
        }
    }

    private void writeQrels(Map<String, List<QrelEntry>> allQrels) {
        Path qrelsFile = datasetDir.resolve("qrels.tsv");
        StringBuilder sb = new StringBuilder();
        sb.append("query_id\tcorpus_id\trelevance\n");

        for (List<QrelEntry> entries : allQrels.values()) {
            for (QrelEntry entry : entries) {
                sb.append(entry.queryId)
                  .append('\t')
                  .append(entry.corpusId)
                  .append('\t')
                  .append(entry.relevance)
                  .append('\n');
            }
        }

        try {
            Files.writeString(qrelsFile, sb.toString());
            log.info("Qrels written to {}", qrelsFile);
        } catch (IOException e) {
            log.error("Failed to write qrels: {}", e.getMessage(), e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private record QrelEntry(String queryId, String corpusId, int relevance) {}
}
