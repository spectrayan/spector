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
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Executes high-performance off-heap memory recall across a benchmark dataset's queries
 * with full cognitive profiles, synaptic tag gating, AISME relays, and hypergraph entity traversal.
 * Exports retrieved context candidates and isolated search latency to a JSONL file
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

        Map<String, BenchmarkCorpusRecord> corpusMap = new HashMap<>(dataset.corpus().size());
        for (BenchmarkCorpusRecord r : dataset.corpus()) {
            if (r.id() != null) {
                corpusMap.put(r.id(), r);
            }
        }

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

            AismeConfig aismeConfig = AismeConfig.builder()
                    .enabled(true)
                    .enableHomeostasis(true)
                    .enableFreeEnergy(true)
                    .enableHopfield(true)
                    .enablePredictiveCoding(true)
                    .enableConsciousnessContinuity(true)
                    .enableGlobalWorkspace(true)
                    .globalWorkspaceCapacity(topK) // Match topK — default 7 (Miller's Law) silently truncates
                    .build();

            boolean enableMmr = Boolean.parseBoolean(System.getProperty("enableMmr", "true"));
            float mmrLambda = Float.parseFloat(System.getProperty("mmrLambda", "0.7"));
            boolean enableReranker = Boolean.parseBoolean(System.getProperty("enableReranker", "false"));
            String textSearchModeProp = System.getProperty("textSearchMode", enableReranker ? "COLBERT" : "HYBRID");
            com.spectrayan.spector.config.model.TextSearchMode defaultSearchMode =
                    com.spectrayan.spector.config.model.TextSearchMode.valueOf(textSearchModeProp.toUpperCase());

            String graphExpMode = System.getProperty("graphExpansionMode", "ALWAYS");
            System.setProperty(com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionMode.SYSTEM_PROPERTY, graphExpMode);
            float graphExpansionThreshold = Float.parseFloat(System.getProperty("graphExpansionThreshold", "0.85"));

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
                        .aismeConfig(aismeConfig)
                        .enableMmr(enableMmr)
                        .mmrLambda(mmrLambda)
                        .enableReranker(enableReranker)
                        .textSearchMode(defaultSearchMode)
                        .graphExpansionThreshold(graphExpansionThreshold)
                        .build();
                memory.recall(wq.text(), wOpt);
            }
            log.info("Warmup complete. Exporting retrieval contexts with full AISME, MMR ({}), GraphExpansion ({}), and cognitive filters...",
                    enableMmr, graphExpMode);

            int count = 0;
            double totalLatencyMs = 0.0;
            long totalTokens = 0;

            for (BenchmarkQuery query : queriesToRun) {
                RecallOptions.Builder optBuilder = RecallOptions.builder()
                        .topK(topK)
                        .recallMode(RecallMode.OBSERVE)
                        .enableTextSearch(true)
                        .enableLateralInhibition(true)
                        .enableAisme(true)
                        .aismeConfig(aismeConfig)
                        .enableMmr(enableMmr)
                        .mmrLambda(mmrLambda)
                        .enableReranker(enableReranker)
                        .graphExpansionThreshold(graphExpansionThreshold)
                        .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE);

                if (query.cognitiveProfile() != null) {
                    optBuilder.profile(query.cognitiveProfile());
                } else {
                    optBuilder.profile(CognitiveProfile.BALANCED);
                }

                if (query.synapticFilterTags() != null && !query.synapticFilterTags().isEmpty()) {
                    optBuilder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
                } else {
                    // Extract conversation scope from query ID (e.g. q_conv_26_1 -> conv_26) to prevent cross-conversation memory contamination
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^q_(conv_\\w+?)_\\d+").matcher(query.id());
                    if (m.find()) {
                        optBuilder.hyperfocusMask(m.group(1));
                    }
                }
                if (query.minValence() != null) {
                    optBuilder.minValence(query.minValence());
                }
                if (query.maxValence() != null) {
                    optBuilder.maxValence(query.maxValence());
                }
                if (query.entityHints() != null && !query.entityHints().isEmpty()) {
                    optBuilder.entityHints(query.entityHints());
                }
                if (query.textSearchMode() != null) {
                    optBuilder.textSearchMode(query.textSearchMode());
                } else {
                    optBuilder.textSearchMode(defaultSearchMode);
                }

                long startNs = System.nanoTime();
                List<CognitiveResult> results = memory.recall(query.text(), optBuilder.build());
                long elapsedNs = System.nanoTime() - startNs;
                double latencyMs = elapsedNs / 1_000_000.0;
                totalLatencyMs += latencyMs;

                List<Map<String, Object>> candidateList = new ArrayList<>();
                StringBuilder contextBuilder = new StringBuilder();
                int candIdx = 1;

                for (CognitiveResult res : results) {
                    Map<String, Object> cand = new HashMap<>();
                    cand.put("id", res.id());
                    cand.put("score", res.score());
                    cand.put("text", res.text());
                    cand.put("source", res.source() != null ? res.source().name() : "OBSERVED");
                    cand.put("importance", res.importance());
                    cand.put("ageDays", res.ageDays());
                    cand.put("valence", (int) res.valence());

                    BenchmarkCorpusRecord corpusRec = res.id() != null ? corpusMap.get(res.id()) : null;
                    String sessionDate = null;
                    if (corpusRec != null && corpusRec.timestampMs() > 0) {
                        java.time.Instant instant = java.time.Instant.ofEpochMilli(corpusRec.timestampMs());
                        sessionDate = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(instant);
                        cand.put("session_date", sessionDate);
                    }

                    candidateList.add(cand);

                    if (contextBuilder.length() > 0) {
                        contextBuilder.append("\n\n");
                    }
                    if (sessionDate != null) {
                        contextBuilder.append("[").append(candIdx++).append("] (Session: ").append(sessionDate).append(")\n")
                                .append(res.text() != null ? res.text().trim() : "");
                    } else {
                        contextBuilder.append("[").append(candIdx++).append("]\n")
                                .append(res.text() != null ? res.text().trim() : "");
                    }
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
                record.put("synaptic_tags", query.synapticFilterTags());
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
