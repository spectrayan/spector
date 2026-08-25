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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Executes high-performance off-heap memory recall across a benchmark dataset's queries
 * and exports the retrieved context candidates and isolated search latency to a JSONL file
 * for downstream LLM Generative QA evaluation (J-Score).
 */
public final class ContextExportRunner {

    private static final Logger log = LoggerFactory.getLogger(ContextExportRunner.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final Path datasetDir;
    private final Path outputFile;
    private final int topK;
    private final int queryLimit;

    public ContextExportRunner(Path datasetDir, Path outputFile, int topK, int queryLimit) {
        this.datasetDir = datasetDir;
        this.outputFile = outputFile;
        this.topK = topK;
        this.queryLimit = queryLimit;
    }

    public static void main(String[] args) {
        String dataDir = args.length >= 1 ? args[0] : System.getProperty(
                "datasetDir",
                System.getProperty("spector.bench.dataset.dir", "../spector-datasets/locomo/data"));
        String outPath = args.length >= 2 ? args[1] : System.getProperty(
                "outputFile",
                System.getProperty("spector.bench.output.file", "../spector-datasets/locomo/results/retrieved_candidates.jsonl"));
        int topK = args.length >= 3 ? Integer.parseInt(args[2]) : Integer.parseInt(System.getProperty("topK", "10"));
        int limit = args.length >= 4 ? Integer.parseInt(args[3]) : Integer.parseInt(System.getProperty("limit", "0"));

        new ContextExportRunner(Path.of(dataDir), Path.of(outPath), topK, limit).run();
    }

    public void run() {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  Spector Memory — Retrieval Context Exporter for Generative QA     ║");
        log.info("║  Dataset: {} | Output: {} | TopK: {}                               ║", datasetDir, outputFile, topK);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);
        log.info("Dataset loaded: {} corpus records, {} queries",
                dataset.corpus().size(), dataset.queries().size());

        // Load raw queries to capture goldAnswer and additional JSON fields
        Map<String, String> goldAnswerMap = new HashMap<>();
        Path queriesFile = datasetDir.resolve("queries.jsonl");
        if (Files.exists(queriesFile)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(queriesFile.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        JsonNode node = jsonMapper.readTree(line);
                        String qid = node.path("id").asText(null);
                        String gold = node.path("goldAnswer").asText(node.path("gold_answer").asText(""));
                        if (qid != null) {
                            goldAnswerMap.put(qid, gold);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Could not read gold answers from {}", queriesFile, e);
            }
        }

        List<BenchmarkQuery> queriesToRun = dataset.queries();
        if (queryLimit > 0 && queryLimit < queriesToRun.size()) {
            queriesToRun = queriesToRun.subList(0, queryLimit);
            log.info("Limiting export to first {} queries", queryLimit);
        }

        Path cacheFile = datasetDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();

        if (outputFile.getParent() != null) {
            try {
                Files.createDirectories(outputFile.getParent());
            } catch (IOException e) {
                log.error("Failed to create parent directories for {}", outputFile, e);
            }
        }

        try (CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile);
             BenchmarkSetup setup = new BenchmarkSetup();
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile.toFile(), false))) {

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir);

            // Warmup pass: 10 queries
            int warmupCount = Math.min(10, dataset.queries().size());
            log.info("Running JIT and memory warmup ({} queries)...", warmupCount);
            for (int w = 0; w < warmupCount; w++) {
                BenchmarkQuery wq = dataset.queries().get(w);
                RecallOptions wOpt = RecallOptions.builder()
                        .topK(topK)
                        .recallMode(RecallMode.OBSERVE)
                        .enableTextSearch(true)
                        .enableLateralInhibition(true)
                        .enableAisme(true)
                        .build();
                memory.recall(wq.text(), wOpt);
            }
            log.info("Warmup complete. Exporting retrieval contexts...");

            RecallOptions recallOptions = RecallOptions.builder()
                    .topK(topK)
                    .recallMode(RecallMode.OBSERVE)
                    .enableTextSearch(true)
                    .enableLateralInhibition(true)
                    .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE)
                    .build();

            int count = 0;
            double totalLatencyMs = 0.0;
            long totalTokens = 0;

            for (BenchmarkQuery query : queriesToRun) {
                long startNs = System.nanoTime();
                List<CognitiveResult> results = memory.recall(query.text(), recallOptions);
                long elapsedNs = System.nanoTime() - startNs;
                double latencyMs = elapsedNs / 1_000_000.0;
                totalLatencyMs += latencyMs;

                List<Map<String, Object>> candidateList = new ArrayList<>();
                StringBuilder contextBuilder = new StringBuilder();

                for (CognitiveResult res : results) {
                    Map<String, Object> cand = new HashMap<>();
                    cand.put("id", res.id());
                    cand.put("score", res.score());
                    cand.put("text", res.text());
                    cand.put("source", res.source() != null ? res.source().name() : "OBSERVED");
                    cand.put("importance", res.importance());
                    cand.put("ageDays", res.ageDays());
                    candidateList.add(cand);

                    if (contextBuilder.length() > 0) {
                        contextBuilder.append("\n");
                    }
                    contextBuilder.append("- ").append(res.text() != null ? res.text().trim() : "");
                }

                String formattedContext = contextBuilder.toString();
                // Estimate tokens: ~4 chars per token
                int estimatedTokens = Math.max(1, formattedContext.length() / 4);
                totalTokens += estimatedTokens;

                String gold = goldAnswerMap.getOrDefault(query.id(), "");

                Map<String, Object> record = new HashMap<>();
                record.put("query_id", query.id());
                record.put("question", query.text());
                record.put("gold_answer", gold);
                record.put("cognitive_profile", query.cognitiveProfile() != null ? query.cognitiveProfile().name() : "BALANCED");
                record.put("expected_subsystem", query.expectedSubsystem());
                record.put("recall_latency_ms", latencyMs);
                record.put("context_tokens", estimatedTokens);
                record.put("context_text", formattedContext);
                record.put("candidates", candidateList);

                writer.write(jsonMapper.writeValueAsString(record));
                writer.newLine();
                count++;
            }

            writer.flush();
            double avgLatencyMs = count > 0 ? totalLatencyMs / count : 0.0;
            double avgTokens = count > 0 ? (double) totalTokens / count : 0.0;

            log.info("✅ Export completed: {} queries written to {}", count, outputFile);
            log.info("📊 Pure Memory Search Latency (Avg): {} ms | Mean Context Tokens: {} tokens",
                    String.format("%.2f", avgLatencyMs), String.format("%.0f", avgTokens));

        } catch (Exception e) {
            log.error("Context export failed", e);
            throw new RuntimeException("Context export failed", e);
        }
    }
}
