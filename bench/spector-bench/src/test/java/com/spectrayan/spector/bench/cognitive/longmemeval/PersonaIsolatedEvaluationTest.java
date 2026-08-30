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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("benchmark")
public class PersonaIsolatedEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(PersonaIsolatedEvaluationTest.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Test
    public void testPersonaIsolatedIngestionAndEvaluation() throws Exception {
        String apiKey = System.getProperty("geminiApiKey", System.getenv("GEMINI_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Skipping testPersonaIsolatedIngestionAndEvaluation: No Gemini API key provided (-DgeminiApiKey or GEMINI_API_KEY env)");
            return;
        }
        String model = System.getProperty("geminiModel", "gemini-3.1-flash-lite");
        Path datasetDir = Paths.get(System.getProperty("datasetDir", "data/longmemeval"));
        Path outputDir = Paths.get(System.getProperty("outputDir", "target/longmemeval/natural_results"));
        Path personasDir = outputDir.resolve("personas");

        if (!Files.exists(personasDir)) {
            log.warn("Personas directory not found at: {}, skipping test", personasDir);
            return;
        }

        GoogleProviderFactory googleFactory = new GoogleProviderFactory();
        ProviderConfig providerConfig = new ProviderConfig(
                "google", "google", model, apiKey,
                "", 0, Map.of("temperature", "0.1", "maxOutputTokens", "1024")
        );

        LlmProvider llm = googleFactory.createGenerationProvider(providerConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to instantiate Google Gemini LLM Provider"));

        Path cacheFile = datasetDir.resolve("embeddings-cache.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();

        List<Path> personaPaths = new ArrayList<>();
        String specificPersona = System.getProperty("persona", null);
        String personaListStr = System.getProperty("personaList", null);
        if (specificPersona != null && !specificPersona.isBlank()) {
            personaPaths.add(personasDir.resolve(specificPersona));
        } else if (personaListStr != null && !personaListStr.isBlank()) {
            for (String p : personaListStr.split(",")) {
                if (!p.isBlank()) {
                    personaPaths.add(personasDir.resolve(p.trim()));
                }
            }
        } else {
            try (Stream<Path> stream = Files.list(personasDir)) {
                personaPaths.addAll(stream.filter(Files::isDirectory).sorted().toList());
            }
        }
        int maxPersonas = Integer.parseInt(System.getProperty("maxPersonas", "-1"));
        boolean forceReevaluate = Boolean.parseBoolean(System.getProperty("forceReevaluate", "false"));
        int newlyEvaluated = 0;

        log.info("Evaluating isolated personas (total available: {}, maxNewToEvaluate: {}, forceReevaluate: {})...",
                personaPaths.size(), maxPersonas > 0 ? maxPersonas : "ALL", forceReevaluate);

        DatasetLoader loader = new DatasetLoader();
        AtomicInteger passedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicLong totalAnswerPromptTokens = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong totalAnswerCompletionTokens = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong totalJudgePromptTokens = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong totalJudgeCompletionTokens = new java.util.concurrent.atomic.AtomicLong(0);
        List<String> summaryLines = Collections.synchronizedList(new ArrayList<>());

        try (CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, cacheFile)) {

            for (Path personaPath : personaPaths) {
                String personaName = personaPath.getFileName().toString();
                Path corpusFile = personaPath.resolve("corpus.jsonl");
                Path queriesFile = personaPath.resolve("queries.jsonl");
                Path memoryDir = personaPath.resolve("memory");
                Path evalLogFile = personaPath.resolve("eval_results.jsonl");

                if (!Files.exists(corpusFile) || !Files.exists(queriesFile)) {
                    log.warn("Skipping persona '{}' - missing corpus.jsonl or queries.jsonl", personaName);
                    continue;
                }

                // Resumption check: If already evaluated and valid, load results and skip re-evaluation
                if (!forceReevaluate && Files.exists(evalLogFile) && Files.size(evalLogFile) > 0) {
                    try (BufferedReader reader = Files.newBufferedReader(evalLogFile)) {
                        String line;
                        boolean hasResults = false;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            var node = jsonMapper.readTree(line);
                            boolean isCorrect = node.path("is_correct").asBoolean(false);
                            String qid = node.path("query_id").asText();
                            String reason = node.path("judge_reason").asText("");
                            String modelAns = node.path("generated_answer").asText("");

                            // Extract tokens if present, or estimate from text lengths
                            var tokensNode = node.path("tokens");
                            if (!tokensNode.isMissingNode()) {
                                totalAnswerPromptTokens.addAndGet(tokensNode.path("answer_prompt_tokens_est").asLong(0));
                                totalAnswerCompletionTokens.addAndGet(tokensNode.path("answer_completion_tokens_est").asLong(0));
                                totalJudgePromptTokens.addAndGet(tokensNode.path("judge_prompt_tokens_est").asLong(0));
                                totalJudgeCompletionTokens.addAndGet(tokensNode.path("judge_completion_tokens_est").asLong(0));
                            } else {
                                int ansCompTok = (int) Math.ceil(modelAns.length() / 4.0);
                                totalAnswerPromptTokens.addAndGet(1500); // average prompt tokens
                                totalAnswerCompletionTokens.addAndGet(ansCompTok);
                                totalJudgePromptTokens.addAndGet(300);
                                totalJudgeCompletionTokens.addAndGet(50);
                            }

                            if (isCorrect) {
                                passedCount.incrementAndGet();
                                summaryLines.add(String.format("PASS (RESUMED): [%s] %s -> %s", personaName, qid, modelAns));
                            } else {
                                failedCount.incrementAndGet();
                                summaryLines.add(String.format("FAIL (RESUMED): [%s] %s -> Reason: %s", personaName, qid, reason));
                            }
                            hasResults = true;
                        }
                        if (hasResults) {
                            log.info("  [RESUMED] Persona '{}' already evaluated. Loaded cached results.", personaName);
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse cached eval_results for '{}': {}. Re-evaluating.", personaName, e.getMessage());
                    }
                }

                if (maxPersonas > 0 && newlyEvaluated >= maxPersonas) {
                    log.info("Reached batch limit of {} newly evaluated personas. Stopping current run.", maxPersonas);
                    break;
                }

                log.info("==================================================");
                log.info("Processing Persona [{}/{}]: {}", newlyEvaluated + 1, maxPersonas > 0 ? maxPersonas : personaPaths.size(), personaName);

                // 1. Ingest into isolated memory store if not already ingested
                boolean needsIngest = !Files.exists(memoryDir.resolve("runtime").resolve("runtime.bundle"));
                if (needsIngest) {
                    log.info("Ingesting {} into isolated memory store at {}", personaName, memoryDir);
                    SpectorMemoryBuilder builder = SpectorMemoryBuilder.create()
                            .dimensions(embedder.dimensions())
                            .embeddingProvider(embedder)
                            .persistence(memoryDir)
                            .persistenceMode(MemoryPersistenceMode.DISK)
                            .episodicPartitionCapacity(2_000)
                            .semanticCapacity(2_000);

                    try (SpectorMemory ingestMemory = builder.build()) {
                        List<BenchmarkCorpusRecord> corpus = loader.loadCorpus(corpusFile);
                        String prevTurnId = null;
                        String currentSessionId = null;

                        for (BenchmarkCorpusRecord record : corpus) {
                            IngestionHints hints = new IngestionHints(
                                    record.interest(), record.challenge(), record.urgency(),
                                    record.valence(), (byte) record.arousal()
                            );

                            var contextBuilder = IngestionContext.builder()
                                    .hints(hints)
                                    .overrideTimestampMs(record.timestampMs());

                            if (record.sessionId() != null && record.sessionId().equals(currentSessionId) && prevTurnId != null) {
                                contextBuilder.temporalLink(prevTurnId, Math.abs(record.sessionId().hashCode()));
                            }

                            Set<String> tags = new LinkedHashSet<>();
                            if (record.synapticTags() != null) {
                                for (String t : record.synapticTags()) {
                                    if (t != null && !t.isBlank()) {
                                        tags.add(t.toLowerCase().trim());
                                    }
                                }
                            }

                            ingestMemory.remember(
                                    record.id(),
                                    record.text(),
                                    MemoryType.SEMANTIC,
                                    MemorySource.OBSERVED,
                                    contextBuilder.build(),
                                    tags.toArray(String[]::new)
                            );

                            prevTurnId = record.id();
                            currentSessionId = record.sessionId();
                        }
                        log.info("Finished ingestion for {}: total memories = {}", personaName, ingestMemory.totalMemories());
                    }
                }

                // 2. Open isolated memory store for recall and evaluation
                SpectorMemoryBuilder evalBuilder = SpectorMemoryBuilder.create()
                        .dimensions(embedder.dimensions())
                        .embeddingProvider(embedder)
                        .persistence(memoryDir)
                        .persistenceMode(MemoryPersistenceMode.DISK)
                        .episodicPartitionCapacity(2_000)
                        .semanticCapacity(2_000);

                AismeConfig aismeConfig = AismeConfig.builder()
                        .enabled(true)
                        .enableHomeostasis(true)
                        .enableFreeEnergy(true)
                        .enableHopfield(true)
                        .enablePredictiveCoding(true)
                        .enableConsciousnessContinuity(true)
                        .enableGlobalWorkspace(true)
                        .globalWorkspaceCapacity(75)
                        .build();

                RecallOptions options = RecallOptions.builder()
                        .topK(60)
                        .semanticCandidateMultiplier(6)
                        .recallMode(RecallMode.OBSERVE)
                        .enableTextSearch(true)
                        .enableLateralInhibition(true)
                        .enableAisme(true)
                        .aismeConfig(aismeConfig)
                        .enableMmr(true)
                        .mmrLambda(0.65f)
                        .graphExpansionThreshold(0.40f)
                        .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE)
                        .profile(CognitiveProfile.BALANCED)
                        .build();

                try (SpectorMemory evalMemory = evalBuilder.build()) {
                    List<BenchmarkQuery> queries = loader.loadQueries(queriesFile);

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(evalLogFile.toFile(), false))) {
                        for (BenchmarkQuery q : queries) {
                            String goldAnswer = getGoldAnswer(queriesFile, q.id());
                            List<CognitiveResult> results = evalMemory.recall(q.text(), options);

                            List<String> candidateTexts = new ArrayList<>();
                            for (CognitiveResult res : results) {
                                if (res.text() != null && !res.text().isBlank()) {
                                    String clean = res.text().trim();
                                    var rec = evalMemory.inspect(res.id());
                                    String datePrefix = "";
                                    if (rec != null && rec.timestampMs() > 0) {
                                        String d = java.time.Instant.ofEpochMilli(rec.timestampMs())
                                                .atZone(java.time.ZoneId.of("UTC"))
                                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                                        datePrefix = "[" + d + "] ";
                                    }
                                    String entry = datePrefix + clean;
                                    if (!candidateTexts.contains(entry)) {
                                        candidateTexts.add(entry);
                                    }
                                }
                            }

                            GenerateResult genResult = generateAnswer(llm, q.text(), candidateTexts);
                            String modelAnswer = genResult.text();
                            JudgeResult judge = evaluateAnswerCorrectness(llm, q.text(), goldAnswer, modelAnswer);

                            totalAnswerPromptTokens.addAndGet(genResult.promptTokens());
                            totalAnswerCompletionTokens.addAndGet(genResult.completionTokens());
                            totalJudgePromptTokens.addAndGet(judge.promptTokens());
                            totalJudgeCompletionTokens.addAndGet(judge.completionTokens());

                            ObjectNode logEntry = jsonMapper.createObjectNode();
                            logEntry.put("persona", personaName);
                            logEntry.put("query_id", q.id());
                            logEntry.put("question", q.text());
                            logEntry.put("gold_answer", goldAnswer);
                            logEntry.put("generated_answer", modelAnswer);
                            logEntry.put("is_correct", judge.isCorrect());
                            logEntry.put("judge_reason", judge.reason());
                            logEntry.put("total_candidates", candidateTexts.size());

                            ObjectNode tokensNode = logEntry.putObject("tokens");
                            tokensNode.put("answer_prompt_chars", genResult.promptChars());
                            tokensNode.put("answer_completion_chars", genResult.completionChars());
                            tokensNode.put("answer_prompt_tokens_est", genResult.promptTokens());
                            tokensNode.put("answer_completion_tokens_est", genResult.completionTokens());
                            tokensNode.put("judge_prompt_chars", judge.promptChars());
                            tokensNode.put("judge_completion_chars", judge.completionChars());
                            tokensNode.put("judge_prompt_tokens_est", judge.promptTokens());
                            tokensNode.put("judge_completion_tokens_est", judge.completionTokens());
                            tokensNode.put("total_tokens_est", genResult.promptTokens() + genResult.completionTokens() + judge.promptTokens() + judge.completionTokens());

                            ArrayNode candsArray = logEntry.putArray("top_candidates");
                            for (int c = 0; c < Math.min(candidateTexts.size(), 10); c++) {
                                candsArray.add(candidateTexts.get(c));
                            }

                            writer.write(jsonMapper.writeValueAsString(logEntry));
                            writer.newLine();
                            writer.flush();

                            if (judge.isCorrect()) {
                                passedCount.incrementAndGet();
                                log.info("  [PASS] QID: {} | Ans: {}", q.id(), modelAnswer.replaceAll("[\\r\\n]+", " "));
                                summaryLines.add(String.format("PASS: [%s] %s -> %s", personaName, q.id(), modelAnswer));
                            } else {
                                failedCount.incrementAndGet();
                                log.info("  [FAIL] QID: {} | Reason: {}", q.id(), judge.reason());
                                summaryLines.add(String.format("FAIL: [%s] %s -> Reason: %s", personaName, q.id(), judge.reason()));
                            }
                        }
                    }
                }
                newlyEvaluated++;
            }
        }

        int totalEvaluated = passedCount.get() + failedCount.get();
        double accuracy = totalEvaluated > 0 ? (passedCount.get() * 100.0) / totalEvaluated : 0.0;
        long totalGenTokens = totalAnswerPromptTokens.get() + totalAnswerCompletionTokens.get();
        long totalJdgTokens = totalJudgePromptTokens.get() + totalJudgeCompletionTokens.get();
        long grandTotalTokens = totalGenTokens + totalJdgTokens;
        double avgTokensPerQuery = totalEvaluated > 0 ? (double) grandTotalTokens / totalEvaluated : 0.0;

        log.info("=================================================");
        log.info("PERSONA ISOLATED EVALUATION RESULTS:");
        log.info("Total Evaluated (cumulative): {}", totalEvaluated);
        log.info("Newly Evaluated in this run: {}", newlyEvaluated);
        log.info("Passed: {} / {} ({:.2f}%)", passedCount.get(), totalEvaluated, accuracy);
        log.info("Failed: {}", failedCount.get());
        log.info("Total Tokens Estimated: {} (Answer: {}, Judge: {}, Avg/Query: {:.1f})",
                grandTotalTokens, totalGenTokens, totalJdgTokens, avgTokensPerQuery);
        log.info("=================================================");

        // Write consolidated summary
        try {
            Path summaryJson = personasDir.resolve("persona_evaluation_summary.json");
            ObjectNode summaryNode = jsonMapper.createObjectNode();
            summaryNode.put("timestamp", java.time.Instant.now().toString());
            summaryNode.put("total_personas_available", personaPaths.size());
            summaryNode.put("cumulative_evaluated", totalEvaluated);
            summaryNode.put("newly_evaluated_in_run", newlyEvaluated);
            summaryNode.put("passed", passedCount.get());
            summaryNode.put("failed", failedCount.get());
            summaryNode.put("accuracy_percent", accuracy);

            ObjectNode tokenSummary = summaryNode.putObject("cumulative_tokens");
            tokenSummary.put("total_answer_prompt_tokens_est", totalAnswerPromptTokens.get());
            tokenSummary.put("total_answer_completion_tokens_est", totalAnswerCompletionTokens.get());
            tokenSummary.put("total_judge_prompt_tokens_est", totalJudgePromptTokens.get());
            tokenSummary.put("total_judge_completion_tokens_est", totalJudgeCompletionTokens.get());
            tokenSummary.put("total_tokens_est", grandTotalTokens);
            tokenSummary.put("avg_tokens_per_query", avgTokensPerQuery);

            Files.writeString(summaryJson, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summaryNode));
            log.info("Saved consolidated summary to {}", summaryJson);
        } catch (Exception e) {
            log.warn("Failed to write summary json: {}", e.getMessage());
        }

        for (String line : summaryLines) {
            log.info("  {}", line);
        }
    }

    private static String getGoldAnswer(Path queriesFile, String queryId) {
        try (BufferedReader reader = new BufferedReader(new FileReader(queriesFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    JsonNode n = jsonMapper.readTree(line);
                    if (queryId.equals(n.path("id").asText())) {
                        return n.path("goldAnswer").asText("");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read gold answer for {}: {}", queryId, e.getMessage());
        }
        return "";
    }

    record GenerateResult(String text, int promptChars, int completionChars, int promptTokens, int completionTokens) {}

    private static GenerateResult generateAnswer(LlmProvider llm, String question, List<String> candidateTexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful and truthful AI assistant with access to the user's long-term memory traces.\n");
        sb.append("Answer the user's question accurately, concisely, and specifically based on the provided memory traces.\n\n");
        sb.append("### Memory Traces:\n");
        for (int i = 0; i < candidateTexts.size(); i++) {
            sb.append(String.format("[%d] %s\n", i + 1, candidateTexts.get(i)));
        }
        sb.append("\n### Critical Reasoning & Arithmetic Instructions:\n");
        sb.append("1. Coherent User Narrative:\n");
        sb.append("   - All memory traces belong to the SAME user. Look across all traces to connect related events and details.\n");
        sb.append("2. Derived Facts & Multi-Trace Arithmetic:\n");
        sb.append("   - Calculate necessary values step-by-step: Age = (Reference Age) - (Years ago); Arrival Time = (Departure Time) + (Travel Duration); Date interval = (End Date) - (Start Date).\n");
        sb.append("3. Chronology & Temporal Ordering:\n");
        sb.append("   - Compare dates and timestamps. An earlier date/time occurred FIRST (e.g. May 4 happened before May 11).\n");
        sb.append("   - State the earliest event as happening first.\n");
        sb.append("4. Grounding & Formatting:\n");
        sb.append("   - First, briefly state the facts and calculation, then clearly state the final answer.\n\n");
        sb.append("User Question: ").append(question).append("\n");
        sb.append("Answer:");

        String prompt = sb.toString();
        GenerationOptions opts = GenerationOptions.builder()
                .temperature(0.0f)
                .maxTokens(800)
                .topP(0.95f)
                .build();

        String response = llm.generate(prompt, opts);
        int promptChars = prompt.length();
        int completionChars = response != null ? response.length() : 0;
        int promptTokens = (int) Math.ceil(promptChars / 4.0);
        int completionTokens = (int) Math.ceil(completionChars / 4.0);

        return new GenerateResult(response, promptChars, completionChars, promptTokens, completionTokens);
    }

    record JudgeResult(boolean isCorrect, String reason, int promptChars, int completionChars, int promptTokens, int completionTokens) {}

    private static JudgeResult evaluateAnswerCorrectness(LlmProvider llm, String question, String goldAnswer, String modelAnswer) {
        if (modelAnswer == null || modelAnswer.isBlank()) return new JudgeResult(false, "Model answer was null or blank", 0, 0, 0, 0);
        if (modelAnswer.contains("I do not have enough information") && !goldAnswer.contains("I do not have enough information")) {
            return new JudgeResult(false, "Model refused with 'I do not have enough information'", 0, 0, 0, 0);
        }

        String prompt = String.format("""
                You are an impartial and rigorous judge evaluating an AI assistant's memory-based answer.
                
                Question: %s
                Gold Ground-Truth Answer: %s
                Model's Answer: %s
                
                Criteria:
                1. Does the Model's Answer accurately convey the key factual information, entities, dates, counts, recommendations, or preferences specified in the Gold Ground-Truth Answer?
                2. Minor stylistic differences, extra helpful details, or slightly different phrasing are fully acceptable as long as the core factual answer is correct and does not contradict the gold answer.
                
                Respond ONLY with a valid JSON object in this exact format:
                {
                  "is_correct": true or false,
                  "reason": "Brief 1-sentence explanation of why the answer is correct or incorrect"
                }
                """, question, goldAnswer, modelAnswer);

        GenerationOptions opts = GenerationOptions.builder()
                .temperature(0.0f)
                .maxTokens(250)
                .build();

        String response = llm.generate(prompt, opts);
        int promptChars = prompt.length();
        int completionChars = response != null ? response.length() : 0;
        int promptTokens = (int) Math.ceil(promptChars / 4.0);
        int completionTokens = (int) Math.ceil(completionChars / 4.0);

        try {
            String cleanJson = response.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JsonNode root = jsonMapper.readTree(cleanJson);
            boolean isCorrect = root.path("is_correct").asBoolean(false);
            String reason = root.path("reason").asText("No reason provided");
            return new JudgeResult(isCorrect, reason, promptChars, completionChars, promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("Failed to parse judge JSON response: {}", response);
            boolean textMatches = modelAnswer.toLowerCase().contains(goldAnswer.toLowerCase().trim());
            return new JudgeResult(textMatches, "Fallback match: " + textMatches, promptChars, completionChars, promptTokens, completionTokens);
        }
    }
}
