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
import com.spectrayan.spector.bench.cognitive.NaturalDatasetLoader.NaturalLoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Executes 100% natural, production ingestion of conversational dialogues using pure
 * {@code memory.remember(id, text, type, source, context)}.
 *
 * <p>Features:
 * 1. Autonomous entity and relationship extraction via Google Gemini LLM Provider.
 * 2. Natural synaptic tag extraction via ContentTagExtractor.
 * 3. Autonomous Hebbian association, STDP plasticity, and Temporal Knowledge Graph construction.
 * 4. Separate, isolated disk persistence.
 * 5. Retrieval candidate export for downstream Generative QA evaluation.
 * </p>
 */
public final class NaturalIngestionRunner {

    private static final Logger log = LoggerFactory.getLogger(NaturalIngestionRunner.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final Path datasetDir;
    private final Path outputDir;
    private final String geminiApiKey;
    private final String geminiModel;
    private final int topK;
    private final int limit;

    public NaturalIngestionRunner(Path datasetDir, Path outputDir, String geminiApiKey, String geminiModel, int topK, int limit) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel != null && !geminiModel.isBlank() ? geminiModel : "gemini-3.1-flash-lite";
        this.topK = topK > 0 ? topK : 30;
        this.limit = limit;
    }

    public static void main(String[] args) {
        String dataDir = System.getProperty("datasetDir", "data/locomo");
        String outDir = System.getProperty("outputDir", "target/natural_results");
        String apiKey = System.getProperty("geminiApiKey", System.getenv("GEMINI_API_KEY"));
        String model = System.getProperty("geminiModel", "gemini-3.1-flash-lite");
        int topK = Integer.parseInt(System.getProperty("topK", "30"));
        int limit = Integer.parseInt(System.getProperty("limit", "0"));

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is required (-DgeminiApiKey=... or GEMINI_API_KEY env)");
        }

        new NaturalIngestionRunner(Path.of(dataDir), Path.of(outDir), apiKey, model, topK, limit).run();
    }

    public void run() {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  Spector Memory — Natural Ingestion & Autonomous LLM Extraction    ║");
        log.info("║  Dataset: {} | Output: {} | Limit: {} turns                        ║", datasetDir, outputDir, limit > 0 ? limit : "ALL");
        log.info("║  Gemini Model: {} | Top-K: {}                                      ║", geminiModel, topK);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        NaturalDatasetLoader loader = new NaturalDatasetLoader();
        NaturalLoadedDataset dataset = loader.load(datasetDir);

        List<BenchmarkCorpusRecord> corpusToIngest = dataset.corpus();
        if (limit > 0 && limit < corpusToIngest.size()) {
            corpusToIngest = corpusToIngest.subList(0, limit);
            log.info("Smoke test active: Ingesting only first {} of {} corpus turns", limit, dataset.corpus().size());
        }

        // Initialize Gemini LLM Provider via GoogleProviderFactory
        GoogleProviderFactory googleFactory = new GoogleProviderFactory();
        ProviderConfig providerConfig = new ProviderConfig(
                "google", "google", geminiModel, geminiApiKey,
                "", 0, Map.of("temperature", "0.2", "maxOutputTokens", "1024")
        );

        LlmProvider geminiLlm = googleFactory.createGenerationProvider(providerConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to instantiate Google Gemini LLM Provider"));
        log.info("Initialized Google Gemini LLM Provider with model '{}'", geminiModel);

        Path cacheFile = datasetDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();

        Path naturalMemoryDir = outputDir.resolve("ingested-memory");
        try {
            Files.createDirectories(outputDir);
            Files.createDirectories(naturalMemoryDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create output directory: " + outputDir, e);
        }

        try (CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {

            SpectorMemory memory = SpectorMemoryBuilder.create()
                    .dimensions(embedder.dimensions())
                    .embeddingProvider(embedder)
                    .persistence(naturalMemoryDir)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .entityExtractor(new LlmEntityExtractor(geminiLlm))
                    .entityExtractionParallelism(4)
                    .entityExtractionQueueCapacity(1000)
                    .build();

            if (dataset.persona() != null && dataset.persona().hasSalienceProfile()) {
                memory.setSalienceProfile(dataset.persona().salienceProfile());
                log.info("Configured persona salience profile for '{}'", dataset.persona().name());
            }

            log.info("Starting natural ingestion of {} turns...", corpusToIngest.size());
            long startIngest = System.currentTimeMillis();

            int count = 0;
            for (BenchmarkCorpusRecord record : corpusToIngest) {
                IngestionHints hints = new IngestionHints(
                        record.interest(), record.challenge(), record.urgency(),
                        record.valence(), (byte) record.arousal()
                );
                var context = IngestionContext.builder()
                        .hints(hints)
                        .overrideTimestampMs(record.timestampMs())
                        .build();

                memory.remember(
                        record.id(),
                        record.text(),
                        MemoryType.SEMANTIC,
                        MemorySource.OBSERVED,
                        context
                );
                count++;
                if (count % 25 == 0 || count == corpusToIngest.size()) {
                    log.info("Ingested {} / {} turns...", count, corpusToIngest.size());
                }
            }

            long ingestElapsed = System.currentTimeMillis() - startIngest;
            log.info("Completed synchronous ingestion of {} turns in {} ms.", count, ingestElapsed);

            if (memory.admin() != null && memory.admin().rememberPathway() != null) {
                var pathway = memory.admin().rememberPathway();
                if (pathway.asyncEntityExtractionQueue() != null) {
                    var queue = pathway.asyncEntityExtractionQueue();
                    log.info("Waiting for AsyncEntityExtractionQueue to complete (submitted={})...", queue.stats().totalSubmitted());
                    while (queue.stats().queueSize() > 0 || (queue.stats().totalProcessed() + queue.stats().totalFailed() < queue.stats().totalSubmitted())) {
                        Thread.sleep(300);
                    }
                    log.info("AsyncEntityExtraction complete: processed={}, failed={}, entitiesExtracted={}",
                            queue.stats().totalProcessed(), queue.stats().totalFailed(), queue.stats().totalEntitiesExtracted());
                }
            }

            // Closing memory cleanly drains remaining tasks and commits all graph structures to bundle
            memory.close();
            log.info("Memory closed and flushed successfully. Re-opening for retrieval candidate export...");

            // Load gold answers from queries.jsonl if present
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

            // Re-open memory instance in DISK mode to verify persistent reload and export query candidates
            try (SpectorMemory queryMemory = SpectorMemoryBuilder.create()
                    .dimensions(embedder.dimensions())
                    .embeddingProvider(embedder)
                    .persistence(naturalMemoryDir)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .entityExtractionMode(EntityExtractionMode.CUSTOM)
                    .build()) {

                if (dataset.persona() != null && dataset.persona().hasSalienceProfile()) {
                    queryMemory.setSalienceProfile(dataset.persona().salienceProfile());
                }

                log.info("Memory reopened. Stats: totalMemories={}, entities={}, tkgFacts={}",
                        queryMemory.totalMemories(),
                        queryMemory.admin().entityDirectory() != null ? queryMemory.admin().entityDirectory().entityCount() : 0,
                        queryMemory.admin().temporalKnowledgeGraph() != null
                                ? queryMemory.admin().temporalKnowledgeGraph().factCount() : 0);

                exportCandidates(queryMemory, dataset.queries(), goldAnswerMap, outputDir.resolve("natural_retrieved_candidates.jsonl"));
            }

        } catch (Exception e) {
            log.error("Natural ingestion failed", e);
            throw new RuntimeException("Natural ingestion failed", e);
        }
    }

    private void exportCandidates(SpectorMemory memory, List<BenchmarkQuery> queries, Map<String, String> goldAnswerMap, Path outputFile) {
        log.info("Exporting candidate sets for {} queries to {} (topK={})", queries.size(), outputFile, topK);

        AismeConfig aismeConfig = AismeConfig.builder()
                .enabled(true)
                .enableHomeostasis(true)
                .enableFreeEnergy(true)
                .enableHopfield(true)
                .enablePredictiveCoding(true)
                .enableConsciousnessContinuity(true)
                .enableGlobalWorkspace(true)
                .globalWorkspaceCapacity(topK)
                .build();

        long totalSearchTimeNanos = 0;
        int queriesEvaluated = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile.toFile(), false))) {
            for (BenchmarkQuery query : queries) {
                RecallOptions.Builder optBuilder = RecallOptions.builder()
                        .topK(topK)
                        .recallMode(RecallMode.OBSERVE)
                        .enableTextSearch(true)
                        .enableLateralInhibition(true)
                        .enableAisme(true)
                        .aismeConfig(aismeConfig)
                        .enableMmr(true)
                        .mmrLambda(0.7f)
                        .graphExpansionThreshold(1.0f)
                        .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE);

                if (query.cognitiveProfile() != null) {
                    optBuilder.profile(query.cognitiveProfile());
                } else {
                    optBuilder.profile(CognitiveProfile.BALANCED);
                }

                if (query.synapticFilterTags() != null && !query.synapticFilterTags().isEmpty()) {
                    optBuilder.synapticFilter(query.synapticFilterTags().toArray(String[]::new));
                }

                RecallOptions options = optBuilder.build();

                long startNanos = System.nanoTime();
                List<CognitiveResult> results = memory.recall(query.text(), options);
                long elapsedNanos = System.nanoTime() - startNanos;

                totalSearchTimeNanos += elapsedNanos;
                queriesEvaluated++;

                String gold = goldAnswerMap.getOrDefault(query.id(), "");

                Map<String, Object> recordJson = new HashMap<>();
                recordJson.put("query_id", query.id());
                recordJson.put("question", query.text());
                recordJson.put("category", query.expectedSubsystem() != null ? query.expectedSubsystem() : "UNKNOWN");
                recordJson.put("gold_answer", gold);
                recordJson.put("search_latency_ms", elapsedNanos / 1_000_000.0);
                recordJson.put("recall_latency_ms", elapsedNanos / 1_000_000.0);

                List<Map<String, Object>> candidateList = new ArrayList<>();
                for (CognitiveResult res : results) {
                    Map<String, Object> cand = new HashMap<>();
                    cand.put("id", res.id());
                    cand.put("text", res.text());
                    cand.put("score", res.score());
                    cand.put("source", res.source() != null ? res.source() : "OBSERVED");
                    cand.put("importance", res.importance());
                    cand.put("valence", res.valence());
                    candidateList.add(cand);
                }
                recordJson.put("candidates", candidateList);

                writer.write(jsonMapper.writeValueAsString(recordJson));
                writer.newLine();
            }
            writer.flush();

            double avgLatencyMs = queriesEvaluated > 0 ? (totalSearchTimeNanos / (double) queriesEvaluated) / 1_000_000.0 : 0.0;
            log.info("Export complete: {} queries written. Avg pure search latency: {} ms",
                    queriesEvaluated, String.format(java.util.Locale.ROOT, "%.2f", avgLatencyMs));

        } catch (IOException e) {
            throw new RuntimeException("Failed to export candidate sets to " + outputFile, e);
        }
    }
}
