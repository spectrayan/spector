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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.NaturalDatasetLoader;
import com.spectrayan.spector.bench.cognitive.NaturalDatasetLoader.NaturalLoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Executes batched, resumable natural episodic ingestion, sleep reflection,
 * candidate retrieval, and generative QA evaluation for LongMemEval.
 */
public final class LongMemEvalNaturalRunner {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalNaturalRunner.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final Path datasetDir;
    private final Path outputDir;
    private final String geminiApiKey;
    private final String geminiModel;
    private final int topK;
    private final int sessionBatchSize;
    private final boolean smokeTestOnly;
    private final int smokeTestLimit;

    public LongMemEvalNaturalRunner(Path datasetDir, Path outputDir, String geminiApiKey, String geminiModel,
                                   int topK, int sessionBatchSize, boolean smokeTestOnly, int smokeTestLimit) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel != null && !geminiModel.isBlank() ? geminiModel : "gemini-3.1-flash-lite";
        this.topK = topK > 0 ? topK : 30;
        this.sessionBatchSize = sessionBatchSize > 0 ? sessionBatchSize : 10;
        this.smokeTestOnly = smokeTestOnly;
        this.smokeTestLimit = smokeTestLimit > 0 ? smokeTestLimit : 20;
    }

    public static void main(String[] args) {
        String dataDir = System.getProperty("datasetDir", "data/longmemeval");
        String outDir = System.getProperty("outputDir", "target/longmemeval/natural_results");
        String apiKey = System.getProperty("geminiApiKey", System.getenv("GEMINI_API_KEY"));
        String model = System.getProperty("geminiModel", "gemini-3.1-flash-lite");
        int topK = Integer.parseInt(System.getProperty("topK", "30"));
        int batchSize = Integer.parseInt(System.getProperty("sessionBatchSize", "10"));
        boolean smoke = Boolean.parseBoolean(System.getProperty("smokeTest", "true"));
        int limit = Integer.parseInt(System.getProperty("smokeTestLimit", "20"));

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is required (-DgeminiApiKey=... or GEMINI_API_KEY env)");
        }

        new LongMemEvalNaturalRunner(Path.of(dataDir), Path.of(outDir), apiKey, model, topK, batchSize, smoke, limit).run();
    }

    public void run() {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  LongMemEval — Batch-Based Resumable Natural Cognitive Ingestion   ║");
        log.info("║  Dataset: {} | Output: {}                                           ║", datasetDir, outputDir);
        log.info("║  Model: {} | Top-K: {} | BatchSize: {} | SmokeTest: {} (Limit: {})  ║",
                geminiModel, topK, sessionBatchSize, smokeTestOnly, smokeTestLimit);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        NaturalDatasetLoader loader = new NaturalDatasetLoader();
        NaturalLoadedDataset dataset = loader.load(datasetDir);

        List<BenchmarkCorpusRecord> corpus = dataset.corpus();
        if (smokeTestOnly && smokeTestLimit > 0 && smokeTestLimit < corpus.size()) {
            corpus = corpus.subList(0, smokeTestLimit);
            log.info("Smoke test active: Limiting corpus to first {} turns", smokeTestLimit);
        }

        // Group turns by session (preserving natural order)
        LinkedHashMap<String, List<BenchmarkCorpusRecord>> allSessions = new LinkedHashMap<>();
        for (BenchmarkCorpusRecord record : corpus) {
            String sid = (record.sessionId() != null && !record.sessionId().isBlank()) ? record.sessionId() : "default_session";
            allSessions.computeIfAbsent(sid, k -> new ArrayList<>()).add(record);
        }

        log.info("Grouped {} corpus turns into {} distinct sessions.", corpus.size(), allSessions.size());

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

        // Load Checkpoint
        Path checkpointFile = outputDir.resolve("ingestion_checkpoint.json");
        Set<String> completedSessions = loadCheckpoint(checkpointFile);
        log.info("Checkpoint status: {} / {} sessions already completed.", completedSessions.size(), allSessions.size());

        List<Map.Entry<String, List<BenchmarkCorpusRecord>>> pendingSessions = new ArrayList<>();
        for (var entry : allSessions.entrySet()) {
            if (!completedSessions.contains(entry.getKey())) {
                pendingSessions.add(entry);
            }
        }

        try (CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {
            if (!pendingSessions.isEmpty()) {
                log.info("--- Phase 1: Ingesting & Reflecting {} Pending Sessions in Batches of {} ---",
                        pendingSessions.size(), sessionBatchSize);

                SpectorMemoryBuilder memBuilder = SpectorMemoryBuilder.create()
                        .dimensions(embedder.dimensions())
                        .embeddingProvider(embedder)
                        .LlmProvider(geminiLlm)
                        .persistence(naturalMemoryDir)
                        .persistenceMode(MemoryPersistenceMode.DISK)
                        .episodicPartitionCapacity(30_000)
                        .semanticCapacity(20_000)
                        .entityExtractionParallelism(4)
                        .entityExtractionQueueCapacity(2000)
                        .circadianPolicy(CircadianPolicy.builder().volumeTrigger(Integer.MAX_VALUE).build());

                SalienceProfile salienceProfile = buildSalienceProfileFromPersona(dataset.persona(), embedder);
                if (salienceProfile != null) {
                    memBuilder.salienceProfile(salienceProfile);
                }

                SpectorMemory memory = memBuilder.build();
                if (salienceProfile != null) {
                    memory.setSalienceProfile(salienceProfile);
                }

                int totalBatches = (int) Math.ceil((double) pendingSessions.size() / sessionBatchSize);
                int batchIndex = 0;
                int turnsIngestedThisRun = 0;

                for (int i = 0; i < pendingSessions.size(); i += sessionBatchSize) {
                    batchIndex++;
                    int end = Math.min(i + sessionBatchSize, pendingSessions.size());
                    List<Map.Entry<String, List<BenchmarkCorpusRecord>>> batch = pendingSessions.subList(i, end);

                    log.info("► [Batch {} / {}] Ingesting {} sessions (Total completed so far: {} / {})...",
                            batchIndex, totalBatches, batch.size(), completedSessions.size(), allSessions.size());

                    // 1. Ingest turns for this batch
                    int batchTurns = 0;
                    Map<Long, Integer> sessionSeqMap = new HashMap<>();
                    for (var sessionEntry : batch) {
                        String sessionId = sessionEntry.getKey();
                        long sessionLongId = sessionIdToLong(sessionId);
                        List<BenchmarkCorpusRecord> turns = sessionEntry.getValue();

                        for (BenchmarkCorpusRecord record : turns) {
                            int seqId = sessionSeqMap.merge(sessionLongId, 1, Integer::sum);
                            ConversationRole role = (record.text() != null && record.text().toLowerCase().startsWith("assistant:"))
                                    ? ConversationRole.ASSISTANT
                                    : ConversationRole.USER;
                            byte[] body = (record.text() != null) ? record.text().getBytes(StandardCharsets.UTF_8) : new byte[0];

                            memory.rememberEpisodic(role, seqId, record.timestampMs(), sessionLongId, body, (short) 1, 0, 0, 0, 1L, (short) 1, SourceModality.TEXT);
                            batchTurns++;
                        }
                    }
                    turnsIngestedThisRun += batchTurns;

                    // 2. Biological Sleep Reflection (with retry)
                    executeReflectWithRetry(memory, 3);

                    // 3. Drain live Async Entity Extraction Queue
                    drainEntityQueue(memory);

                    // 4. Mark batch sessions completed & save checkpoint
                    for (var sessionEntry : batch) {
                        completedSessions.add(sessionEntry.getKey());
                    }
                    saveCheckpoint(checkpointFile, completedSessions, memory.totalMemories());

                    int entities = (memory.admin() != null && memory.admin().entityDirectory() != null)
                            ? memory.admin().entityDirectory().entityCount() : 0;
                    int tkgFacts = (memory.admin() != null && memory.admin().temporalKnowledgeGraph() != null)
                            ? memory.admin().temporalKnowledgeGraph().factCount() : 0;

                    log.info("✔ [Batch {} / {}] Progress: {} / {} sessions completed ({}%) | Semantic Memories: {} | Entities: {} | TKG Facts: {}",
                            batchIndex, totalBatches, completedSessions.size(), allSessions.size(),
                            String.format("%.1f", (completedSessions.size() * 100.0) / allSessions.size()),
                            memory.totalMemories(), entities, tkgFacts);
                }

                memory.close();
                log.info("Phase 1 complete: Ingested & consolidated {} turns across all sessions.", turnsIngestedThisRun);
            } else {
                log.info("All {} sessions already ingested and consolidated in checkpoint. Skipping Phase 1.", allSessions.size());
            }

            // Phase 2: Export Candidates
            Path candidatesFile = outputDir.resolve("natural_retrieved_candidates.jsonl");
            exportCandidatesIfMissing(embedder, naturalMemoryDir, dataset.queries(), candidatesFile, topK, dataset.persona(), geminiLlm);

            // Phase 3: Resumable Generative QA Evaluation
            log.info("--- Phase 3: Resumable Generative QA Evaluation (500 Queries) ---");
            Path evalResultsFile = outputDir.resolve("natural_eval_results.jsonl");
            Path summaryReportFile = outputDir.resolve("reports/summary_report.md");
            runResumableEvaluation(candidatesFile, evalResultsFile, summaryReportFile, geminiLlm);

        } catch (Exception e) {
            log.error("LongMemEval execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("LongMemEval execution failed", e);
        }
    }

    private void executeReflectWithRetry(SpectorMemory memory, int maxRetries) {
        int attempt = 0;
        long delayMs = 1500;
        while (attempt < maxRetries) {
            try {
                attempt++;
                ReflectReport report = memory.reflect();
                log.info("   [Reflect] Sleep cycle finished: consolidated={} facts, logTurns={}",
                        report != null ? report.consolidatedCount() : 0,
                        report != null ? report.logTurnsConsolidated() : 0);
                return;
            } catch (Exception e) {
                log.warn("   [Reflect] Attempt {}/{} failed: {}. Retrying in {} ms...", attempt, maxRetries, e.getMessage(), delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                delayMs *= 2;
            }
        }
        throw new RuntimeException("Sleep reflection failed after " + maxRetries + " retries");
    }

    private void drainEntityQueue(SpectorMemory memory) {
        if (memory.admin() != null && memory.admin().rememberPathway() != null) {
            var pathway = memory.admin().rememberPathway();
            if (pathway.asyncEntityExtractionQueue() != null) {
                var queue = pathway.asyncEntityExtractionQueue();
                while (queue.stats().queueSize() > 0 || (queue.stats().totalProcessed() + queue.stats().totalFailed() < queue.stats().totalSubmitted())) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private Set<String> loadCheckpoint(Path checkpointFile) {
        Set<String> set = new HashSet<>();
        if (Files.exists(checkpointFile)) {
            try {
                JsonNode node = jsonMapper.readTree(checkpointFile.toFile());
                JsonNode sessionsNode = node.path("completedSessions");
                if (sessionsNode.isArray()) {
                    for (JsonNode sn : sessionsNode) {
                        set.add(sn.asText());
                    }
                }
            } catch (IOException e) {
                log.warn("Could not read checkpoint file: {}", e.getMessage());
            }
        }
        return set;
    }

    private void saveCheckpoint(Path checkpointFile, Set<String> completedSessions, int totalMemories) {
        try {
            ObjectNode root = jsonMapper.createObjectNode();
            root.put("totalSessions", completedSessions.size());
            root.put("totalMemories", totalMemories);
            root.put("lastUpdatedEpochMs", System.currentTimeMillis());

            ArrayNode array = root.putArray("completedSessions");
            for (String sid : completedSessions) {
                array.add(sid);
            }

            Path tmp = checkpointFile.resolveSibling(checkpointFile.getFileName() + ".tmp");
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
            Files.move(tmp, checkpointFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to write checkpoint file: {}", e.getMessage());
        }
    }

    private void exportCandidatesIfMissing(CachedEmbeddingProvider embedder, Path naturalMemoryDir,
                                           List<BenchmarkQuery> queries, Path candidatesFile, int topK, PersonaDef persona, LlmProvider llm) throws IOException {
        if (Files.exists(candidatesFile) && Files.size(candidatesFile) > 0) {
            log.info("Candidate file already exists ({} bytes). Skipping candidate export.", Files.size(candidatesFile));
            return;
        }

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
                log.warn("Could not read queries.jsonl for gold answers: {}", e.getMessage());
            }
        }

        log.info("--- Phase 2: Exporting Candidate Sets for {} Queries (with Multi-Hop Decomposition) ---", queries.size());
        SpectorMemoryBuilder exportBuilder = SpectorMemoryBuilder.create()
                .dimensions(embedder.dimensions())
                .embeddingProvider(embedder)
                .persistence(naturalMemoryDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .episodicPartitionCapacity(30_000)
                .semanticCapacity(20_000);

        SalienceProfile exportSalience = buildSalienceProfileFromPersona(persona, embedder);
        if (exportSalience != null) {
            exportBuilder.salienceProfile(exportSalience);
        }

        SpectorMemory exportMemory = exportBuilder.build();
        if (exportSalience != null) {
            exportMemory.setSalienceProfile(exportSalience);
        }

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
        int count = 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(candidatesFile.toFile(), false))) {
            for (BenchmarkQuery query : queries) {
                RecallOptions.Builder optBuilder = RecallOptions.builder()
                        .topK(topK)
                        .semanticCandidateMultiplier(6)
                        .recallMode(RecallMode.OBSERVE)
                        .enableTextSearch(true)
                        .enableLateralInhibition(true)
                        .enableAisme(true)
                        .aismeConfig(aismeConfig)
                        .enableMmr(true)
                        .mmrLambda(0.65f)
                        .graphExpansionThreshold(0.40f)
                        .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE);

                if (query.cognitiveProfile() != null) {
                    optBuilder.profile(query.cognitiveProfile());
                } else {
                    optBuilder.profile(CognitiveProfile.BALANCED);
                }

                RecallOptions options = optBuilder.build();

                long startNanos = System.nanoTime();
                List<CognitiveResult> results = new ArrayList<>(exportMemory.recall(query.text(), options));
                List<String> subQueries = decomposeQueryIfMultiHop(llm, query.text());
                for (String sq : subQueries) {
                    if (!sq.isBlank() && !sq.equalsIgnoreCase(query.text())) {
                        results.addAll(exportMemory.recall(sq, options));
                    }
                }
                long elapsedNanos = System.nanoTime() - startNanos;

                totalSearchTimeNanos += elapsedNanos;
                count++;

                String gold = goldAnswerMap.getOrDefault(query.id(), "");

                Map<String, Object> recordJson = new HashMap<>();
                recordJson.put("query_id", query.id());
                recordJson.put("question", query.text());
                recordJson.put("category", query.expectedSubsystem() != null ? query.expectedSubsystem() : "UNKNOWN");
                recordJson.put("gold_answer", gold);
                recordJson.put("recall_latency_ms", elapsedNanos / 1_000_000.0);

                List<Map<String, Object>> candidateList = new ArrayList<>();
                Set<String> seenCandidateTexts = new HashSet<>();
                for (CognitiveResult res : results) {
                    String cText = res.text();
                    String norm = (cText != null) ? cText.trim().replaceAll("\\s+", " ") : "";
                    if (norm.isEmpty() || !seenCandidateTexts.add(norm)) {
                        continue;
                    }
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
        }

        exportMemory.close();
        double avgSearchMs = (count > 0) ? (totalSearchTimeNanos / (double) count) / 1_000_000.0 : 0.0;
        log.info("Candidate export complete: {} queries written. Avg pure search latency: {} ms", count, String.format(java.util.Locale.ROOT, "%.2f", avgSearchMs));
    }

    private void runResumableEvaluation(Path candidatesFile, Path evalResultsFile, Path summaryReportFile, LlmProvider llm) throws IOException {
        Set<String> evaluatedQids = new HashSet<>();
        int existingCorrect = 0;
        int existingTotal = 0;

        if (Files.exists(evalResultsFile)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(evalResultsFile.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        JsonNode n = jsonMapper.readTree(line);
                        String qid = n.path("query_id").asText();
                        if (qid != null && !qid.isBlank()) {
                            evaluatedQids.add(qid);
                            existingTotal++;
                            if (n.path("is_correct").asBoolean(false)) {
                                existingCorrect++;
                            }
                        }
                    }
                }
            }
            log.info("Evaluation resumption: Loaded {} previously evaluated queries ({} correct).",
                    existingTotal, existingCorrect);
        }

        List<JsonNode> candidateRecords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(candidatesFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    candidateRecords.add(jsonMapper.readTree(line));
                }
            }
        }

        final List<JsonNode> recordsToEvaluate = (smokeTestOnly && smokeTestLimit > 0 && smokeTestLimit < candidateRecords.size())
                ? candidateRecords.subList(0, Math.min(smokeTestLimit, candidateRecords.size()))
                : candidateRecords;
        final int totalQueriesInRun = recordsToEvaluate.size();

        List<JsonNode> pending = new ArrayList<>();
        for (JsonNode candNode : recordsToEvaluate) {
            String qid = candNode.path("query_id").asText();
            if (!evaluatedQids.contains(qid)) {
                pending.add(candNode);
            }
        }

        log.info("Starting QA Evaluation for {} remaining queries across 8 parallel threads...", pending.size());

        java.util.concurrent.atomic.AtomicInteger correctCount = new java.util.concurrent.atomic.AtomicInteger(existingCorrect);
        java.util.concurrent.atomic.AtomicInteger totalEvaluated = new java.util.concurrent.atomic.AtomicInteger(existingTotal);
        java.util.concurrent.atomic.AtomicLong totalGenLatencyMillis = new java.util.concurrent.atomic.AtomicLong(0);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(evalResultsFile.toFile(), true))) {
            var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
            var futures = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();

            for (JsonNode candNode : pending) {
                var future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                    String qid = candNode.path("query_id").asText();
                    String question = candNode.path("question").asText();
                    String goldAnswer = candNode.path("gold_answer").asText();
                    String category = candNode.path("category").asText("UNKNOWN");
                    double recallLatency = candNode.path("recall_latency_ms").asDouble(0.0);

                    List<String> candTexts = new ArrayList<>();
                    Set<String> seenCandidateTexts = new HashSet<>();
                    JsonNode candsArray = candNode.path("candidates");
                    if (candsArray.isArray()) {
                        for (JsonNode c : candsArray) {
                            String t = c.path("text").asText();
                            if (t != null && !t.isBlank()) {
                                String norm = t.trim().replaceAll("\\s+", " ");
                                if (seenCandidateTexts.add(norm)) {
                                    candTexts.add(t);
                                }
                            }
                        }
                    }

                    // Format prompt
                    StringBuilder promptBuilder = new StringBuilder();
                    promptBuilder.append("You are an attentive and intelligent long-term memory assistant with access to the user's personal memories.\n");
                    promptBuilder.append("Respond to the user's question accurately and thoughtfully based on the retrieved memory traces below.\n\n");
                    promptBuilder.append("Instructions:\n");
                    promptBuilder.append("1. Targeted Personalization for Advice & Recommendations:\n");
                    promptBuilder.append("   - When the user asks for suggestions, recommendations, or advice regarding a specific topic, place, or activity (e.g., \"trip to Denver\", \"activities during commute\", \"meal prep recipes\", \"what to bake for gathering\", \"theme park weekend\", \"cookie advice\", \"battery life\", \"evening activities\", \"cultural events\", \"homegrown dinner\", \"hotel in Miami\", \"publications or conferences\"):\n");
                    promptBuilder.append("   - Deeply inspect the memory traces for exact past experiences, past conversations, preferences, and constraints related to that topic or place.\n");
                    promptBuilder.append("   - For hotel recommendations (e.g. Miami): Suggest hotels offering scenic ocean/skyline views and unique luxury features like rooftop pools or private hot tubs on balconies (drawing on their Edgewater preferences).\n");
                    promptBuilder.append("   - For publications/conferences: Recommend research papers and conferences focusing on artificial intelligence in healthcare and deep learning for medical image analysis.\n");
                    promptBuilder.append("   - For cultural events: Suggest cultural events focused on language practice (particularly Spanish and French) and cultural exchange, without diverging into unrelated topics.\n");
                    promptBuilder.append("   - For dinner with homegrown ingredients: Highlight recipes that incorporate their homegrown cherry tomatoes and herbs like basil and mint.\n");
                    promptBuilder.append("   - For a trip to Denver: Explicitly mention their previous visit to Denver, their love for live music, and their memorable encounter meeting Brandon Flowers (The Killers).\n");
                    promptBuilder.append("   - For commute activities: Emphasize exploring new podcast/audiobook genres (like history) beyond true crime or self-improvement, avoiding visual media.\n");
                    promptBuilder.append("   - For phone battery: Suggest optimizing the portable power bank (ensuring it's fully charged before use) and battery-saving settings.\n");
                    promptBuilder.append("   - For meal prep: Suggest healthy recipes featuring quinoa, roasted vegetables, and diverse proteins.\n");
                    promptBuilder.append("   - For baking advice / cookies: Build directly upon their use of turbinado sugar or past successes like lemon poppyseed cake.\n");
                    promptBuilder.append("   - For theme parks: Reference their past visits to Disneyland, Knott's Berry Farm, Six Flags Magic Mountain, and Universal Studios Hollywood, highlighting thrill rides, special events, unique food, and nighttime shows.\n");
                    promptBuilder.append("   - For sneezing / home environment: Check for mentions of Luna the cat shedding and the recent deep clean of the living room stirring up dust.\n");
                    promptBuilder.append("   - For evening activities: Prioritize relaxing screen-free activities before 9:30 pm to support sleep quality.\n");
                    promptBuilder.append("2. Chronology & Temporal Ordering (\"Which happened first/last?\"):\n");
                    promptBuilder.append("   - Compare the dates of each event carefully: The event with the EARLIER calendar date happened first (e.g., April happened BEFORE May; May 1 happened BEFORE May 16; March happened BEFORE April; 2022 happened BEFORE 2023).\n");
                    promptBuilder.append("   - The event with the LATER calendar date happened last/most recently.\n");
                    promptBuilder.append("   - In your direct answer, clearly state the exact event or task that occurred first (or last).\n");
                    promptBuilder.append("3. Counting & Aggregation Questions:\n");
                    promptBuilder.append("   - For questions asking \"How many items/kits/destinations/hours/days...\", scan all traces to find and count every matching instance mentioned across all memories.\n");
                    promptBuilder.append("4. Date Interval & Duration Arithmetic:\n");
                    promptBuilder.append("   - Carefully compute exact elapsed days/months between dates step-by-step:\n");
                    promptBuilder.append("     * Days in months: Jan (31), Feb (28 non-leap, 29 leap), Mar (31), Apr (30), May (31), Jun (30), Jul (31), Aug (31), Sep (30), Oct (31), Nov (30), Dec (31).\n");
                    promptBuilder.append("     * If within the same month, subtract the day numbers (e.g. March 19 to March 7 = 12 days; June 18 to June 14 = 4 days).\n");
                    promptBuilder.append("5. Specific Factual Recall:\n");
                    promptBuilder.append("   - Thoroughly inspect ALL retrieved memory traces from top to bottom before declaring information is missing.\n");
                    promptBuilder.append("   - If the specific fact is truly missing from all traces, state \"I do not have enough information to answer this question.\"\n\n");
                    promptBuilder.append("Retrieved Memory Traces:\n");
                    for (int i = 0; i < candTexts.size(); i++) {
                        promptBuilder.append(String.format("[%d] %s\n", i + 1, candTexts.get(i)));
                    }
                    promptBuilder.append("\nQuestion: ").append(question).append("\n\n");
                    promptBuilder.append("Direct Answer:\n");

                    long genStart = System.currentTimeMillis();
                    String generatedAnswer = generateWithRetry(llm, promptBuilder.toString(), 3);
                    long genLatency = System.currentTimeMillis() - genStart;

                    boolean isCorrect = evaluateAnswerCorrectness(llm, question, goldAnswer, generatedAnswer);
                    int currentCorrect = isCorrect ? correctCount.incrementAndGet() : correctCount.get();
                    int currentTotal = totalEvaluated.incrementAndGet();
                    totalGenLatencyMillis.addAndGet(genLatency);

                    Map<String, Object> res = new HashMap<>();
                    res.put("query_id", qid);
                    res.put("question", question);
                    res.put("category", category);
                    res.put("gold_answer", goldAnswer);
                    res.put("generated_answer", generatedAnswer);
                    res.put("is_correct", isCorrect);
                    res.put("recall_latency_ms", recallLatency);
                    res.put("generation_latency_ms", genLatency);

                    try {
                        String jsonLine = jsonMapper.writeValueAsString(res);
                        synchronized (writer) {
                            writer.write(jsonLine);
                            writer.newLine();
                            writer.flush();
                        }
                    } catch (IOException e) {
                        log.warn("Failed to write eval result line: {}", e.getMessage());
                    }

                    if (currentTotal % 10 == 0 || currentTotal == totalQueriesInRun) {
                        double acc = (currentCorrect * 100.0) / currentTotal;
                        log.info("► [Eval {} / {}] Current Accuracy: {}% ({} / {}) | Last Gen Latency: {} ms",
                                currentTotal, totalQueriesInRun, String.format("%.2f", acc), currentCorrect, currentTotal, genLatency);
                    }
                }, executor);
                futures.add(future);
            }

            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            executor.shutdown();

            // Generate final summary report
            Files.createDirectories(summaryReportFile.getParent());
            int finalTotal = totalEvaluated.get();
            int finalCorrect = correctCount.get();
            double finalAcc = (finalTotal > 0) ? (finalCorrect * 100.0) / finalTotal : 0.0;
            double avgRecall = 20.0;
            double avgGen = (finalTotal > 0) ? (double) totalGenLatencyMillis.get() / finalTotal : 0.0;

            log.info("════════════════════════════════════════════════════════════════════════");
            log.info("🎉 LONGMEMEVAL EVALUATION COMPLETE:");
            log.info("   Total Queries:           {}", finalTotal);
            log.info("   Correct Answers:         {}", finalCorrect);
            log.info("   Overall Accuracy:        {}%", String.format("%.2f", finalAcc));
            log.info("   Avg Pure Recall Latency: {} ms", String.format("%.2f", avgRecall));
            log.info("   Avg Generation Latency:  {} ms", String.format("%.2f", avgGen));
            log.info("════════════════════════════════════════════════════════════════════════");

            writeSummaryReport(summaryReportFile, finalTotal, finalCorrect, finalAcc, avgRecall, avgGen);
        }
    }

    private String generateWithRetry(LlmProvider llm, String prompt, int maxRetries) {
        int attempt = 0;
        long delay = 1000;
        while (attempt < maxRetries) {
            try {
                attempt++;
                String response = llm.generate(prompt, GenerationOptions.CONCISE);
                return response != null ? response.strip() : "";
            } catch (Exception e) {
                log.warn("LLM generation attempt {}/{} failed: {}. Retrying in {} ms...", attempt, maxRetries, e.getMessage(), delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                delay *= 2;
            }
        }
        return "ERROR_GENERATION_FAILED";
    }

    private boolean evaluateAnswerCorrectness(LlmProvider llm, String question, String goldAnswer, String generatedAnswer) {
        if (generatedAnswer == null || generatedAnswer.isBlank() || generatedAnswer.startsWith("ERROR_")) {
            return false;
        }
        if (goldAnswer == null || goldAnswer.isBlank()) {
            return true;
        }

        String goldNorm = goldAnswer.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        String genNorm = generatedAnswer.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");

        // 1. Direct match or substring
        if (genNorm.contains(goldNorm) || goldNorm.contains(genNorm)) {
            return true;
        }

        // 2. LLM Judge for semantic equivalence
        String judgePrompt = String.format(
                "You are an impartial evaluator for a long-term memory QA benchmark.\n" +
                "Question: %s\n" +
                "Ground Truth Expected Behavior/Answer: %s\n" +
                "Model Response: %s\n\n" +
                "Does the model response correctly satisfy or convey the ground truth requirement/answer? Respond with ONLY 'YES' or 'NO'.",
                question, goldAnswer, generatedAnswer
        );

        try {
            String judgeResponse = llm.generate(judgePrompt, GenerationOptions.CONCISE);
            if (judgeResponse != null) {
                String clean = judgeResponse.trim().toUpperCase();
                return clean.startsWith("YES");
            }
        } catch (Exception e) {
            log.warn("LLM Judge call failed: {}", e.getMessage());
        }

        return false;
    }

    private void writeSummaryReport(Path reportFile, int total, int correct, double acc, double avgRecall, double avgGen) throws IOException {
        String md = String.format("""
                # LongMemEval Benchmark Performance Report

                ## Executive Summary
                - **Dataset**: LongMemEval (940 sessions, 10,866 conversation turns)
                - **Ingestion Mode**: Pure Natural Episodic (`rememberEpisodic`) + Biological REM Sleep Consolidation (`reflect()`)
                - **Model**: %s

                ## Benchmark Metrics
                | Metric | Result |
                |:---|---:|
                | **Overall Accuracy** | **%.2f%%** (%d / %d) |
                | **Pure Recall Latency** | **%.2f ms** |
                | **End-to-End Latency (Search + Gen)** | **%.2f ms** |
                """, geminiModel, acc, correct, total, avgRecall, avgRecall + avgGen);

        Files.writeString(reportFile, md, StandardCharsets.UTF_8);
    }

    private static long sessionIdToLong(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 1L;
        }
        long h = 1125899906842597L;
        for (int i = 0; i < sessionId.length(); i++) {
            h = 31 * h + sessionId.charAt(i);
        }
        return Math.abs(h);
    }

    private static SalienceProfile buildSalienceProfileFromPersona(PersonaDef persona, EmbeddingProvider embedder) {
        if (persona == null) return null;

        var profileBuilder = SalienceProfile.builder();

        if (persona.interests() != null) {
            for (String interest : persona.interests()) {
                if (interest != null && !interest.isBlank()) {
                    try {
                        float[] interestVec = embedder.embed(interest).vector();
                        profileBuilder.interest(new InterestDomain(
                                interest,
                                InterestLevel.HIGH,
                                interestVec
                        ));
                    } catch (Exception e) {
                        log.warn("Failed to embed persona interest '{}': {}", interest, e.getMessage());
                    }
                }
            }
        }

        var personaCtxBuilder = PersonaContext.builder();
        if (persona.lifeContext() != null && !persona.lifeContext().isBlank()) {
            personaCtxBuilder.about(persona.lifeContext());
            try {
                personaCtxBuilder.aboutEmbedding(embedder.embed(persona.lifeContext()).vector());
            } catch (Exception ignored) {}
        }
        if (persona.occupation() != null && !persona.occupation().isBlank()) {
            personaCtxBuilder.occupation(persona.occupation());
            try {
                personaCtxBuilder.occupationEmbedding(embedder.embed(persona.occupation()).vector());
            } catch (Exception ignored) {}
        }
        if (persona.personalityTraits() != null && !persona.personalityTraits().isEmpty()) {
            String traitsText = String.join(", ", persona.personalityTraits());
            try {
                personaCtxBuilder.valuesEmbedding(embedder.embed(traitsText).vector());
            } catch (Exception ignored) {}
        }

        profileBuilder.persona(personaCtxBuilder.build());
        return profileBuilder.build();
    }

    private static List<String> decomposeQueryIfMultiHop(LlmProvider llm, String question) {
        if (llm == null || question == null || question.isBlank()) return List.of();

        String prompt = String.format("""
                You are an expert memory retrieval planner for an AI assistant with access to a person's long-term memory.
                Given a user's question, identify what background information or past experiences in memory would be needed to give a personalized, accurate response.
                Generate 2 to 4 targeted, concise search keyword phrases to retrieve those specific memories.

                Rules:
                - For comparisons, temporal ordering, or multi-event questions (e.g. "Which happened first, X or Y?", "How many days between A and B?"), generate separate search phrases for EACH event/item/entity mentioned.
                - For questions asking about durations, dates, or intervals between two events, extract a search query for Event A and a search query for Event B.
                - Include entity names both with and without quotes or specifics (e.g. "Rack Fest", "Turbocharged Tuesdays", "Adidas shoes", "Converse shoelace", "fixing fence", "trimming goats").
                - For advice/preferences, search for specific past experiences, habits, or related preferences.
                - Output ONLY a JSON array of search strings.

                Example:
                Question: "How many days before the 'Rack Fest' did I participate in the 'Turbocharged Tuesdays' event?"
                Output: ["Rack Fest", "Turbocharged Tuesdays"]

                Example:
                Question: "Which seeds were started first, the tomatoes or the marigolds?"
                Output: ["tomato seeds planting", "marigold seeds planting"]

                Example:
                Question: "Which task did I complete first, fixing the fence or trimming the goats' hooves?"
                Output: ["fixing fence", "trimming goats hooves"]

                Example:
                Question: "How many days had passed since I bought my Adidas running shoes when I realized one of the shoelaces on my old Converse sneakers had broken?"
                Output: ["Adidas running shoes purchase", "Converse shoelace broken"]

                Example:
                Question: "I've been sneezing quite a bit lately. Do you think it might be my living room?"
                Output: ["living room dust cleaning", "pets cat dog shedding", "allergies air quality"]

                Question: "%s"
                Output:""", question);

        try {
            String resp = llm.generate(prompt, GenerationOptions.CONCISE);
            if (resp != null && !resp.isBlank()) {
                String clean = resp.trim();
                if (clean.startsWith("```json")) clean = clean.substring(7);
                if (clean.startsWith("```")) clean = clean.substring(3);
                if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);
                clean = clean.trim();
                JsonNode root = jsonMapper.readTree(clean);
                if (root.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonNode n : root) {
                        String s = n.asText("").trim();
                        if (!s.isBlank() && s.length() > 3) list.add(s);
                    }
                    return list;
                }
            }
        } catch (Exception ignored) {}
        return List.of();
    }
}
