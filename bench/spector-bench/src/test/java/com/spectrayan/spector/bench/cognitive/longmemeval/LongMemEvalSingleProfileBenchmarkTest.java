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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.BenchmarkSetup;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.CognitiveBenchmarkHarness;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkExitCode;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bench")
public class LongMemEvalSingleProfileBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalSingleProfileBenchmarkTest.class);
    private static final ObjectMapper jsonMapper = JsonMapper.builder().build();

    private static Path resolveBaseDir() {
        String sysProp = System.getProperty("datasets.base.dir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }
        String envVar = System.getenv("DATASETS_BASE_DIR");
        if (envVar != null && !envVar.isBlank()) {
            return Paths.get(envVar);
        }
        Path current = Paths.get("").toAbsolutePath();
        for (Path dir = current; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("spector-datasets");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path siblingCandidate = dir.getParent() != null ? dir.getParent().resolve("spector-datasets") : null;
            if (siblingCandidate != null && Files.exists(siblingCandidate)) {
                return siblingCandidate;
            }
        }
        return Paths.get("..", "spector-datasets");
    }

    private static Path resolveDatasetDir() {
        String specificEnv = System.getenv("DATASET_DIR");
        if (specificEnv != null && !specificEnv.isBlank()) {
            return Paths.get(specificEnv);
        }
        String sysProp = System.getProperty("datasetDir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }
        Path base = resolveBaseDir();
        return base.resolve("longmemeval-single-profile");
    }

    @Test
    void runSingleProfileBenchmark() throws IOException {
        Path datasetDir = resolveDatasetDir();

        if (!Files.exists(datasetDir)) {
            log.warn("Skipping single profile benchmark: dataset directory not found at {}", datasetDir);
            return;
        }

        Path outputDir = Paths.get(System.getProperty(
                "outputDir",
                datasetDir.resolve("results").toString()
        ));

        log.info("Starting Single Profile LongMemEval Benchmark Execution:");
        log.info("  Dataset: {}", datasetDir.toAbsolutePath());
        log.info("  Output: {}", outputDir.toAbsolutePath());

        CognitiveBenchmarkHarness harness = new CognitiveBenchmarkHarness(
                datasetDir,
                outputDir,
                0.0,
                10,
                null
        );

        BenchmarkExitCode exitCode = harness.run();
        log.info("Single Profile LongMemEval Benchmark Exit Code: {}", exitCode);
        assertTrue(exitCode == BenchmarkExitCode.SUCCESS || exitCode == BenchmarkExitCode.EFFECT_SIZE_INSUFFICIENT,
                "Benchmark exit code must be SUCCESS or EFFECT_SIZE_INSUFFICIENT (got " + exitCode + ")");

        Path summaryJson = outputDir.resolve("summary.json");
        assertTrue(Files.exists(summaryJson), "summary.json must be generated");

        Path detailCsv = outputDir.resolve("detail.csv");
        assertTrue(Files.exists(detailCsv), "detail.csv must be generated");

        JsonNode root = jsonMapper.readTree(Files.readString(summaryJson));
        double cogNdcg = root.path("cognitive_metrics").path("ndcg_at_10").asDouble(0.0);
        double cogMrr = root.path("cognitive_metrics").path("mrr_at_10").asDouble(0.0);
        double cogRecall = root.path("cognitive_metrics").path("recall_at_10").asDouble(0.0);
        int losses = root.path("win_tie_loss").path("losses").asInt(99);
        int wins = root.path("win_tie_loss").path("wins").asInt(0);

        log.info("Benchmark Run Result: nDCG@10={}, MRR@10={}, Recall@10={}, Wins={}, Losses={}",
                cogNdcg, cogMrr, cogRecall, wins, losses);
    }

    @Test
    void runSingleProfileQaBenchmark() throws Exception {
        Path datasetDir = resolveDatasetDir();
        if (!Files.exists(datasetDir)) {
            log.warn("Skipping single profile QA benchmark: dataset directory not found at {}", datasetDir);
            return;
        }

        String apiKey = System.getProperty("geminiApiKey", System.getenv().getOrDefault("GEMINI_API_KEY", ""));
        if (apiKey.isBlank()) {
            log.warn("Skipping single profile QA benchmark: Gemini API key not provided (-DgeminiApiKey=... or GEMINI_API_KEY env)");
            return;
        }

        Path outputDir = Paths.get(System.getProperty(
                "outputDir",
                datasetDir.resolve("results").toString()
        ));
        Files.createDirectories(outputDir);

        // Enforce graph expansion (Hebbian, Temporal, Entity) for holistic dialogue context
        System.setProperty("spector.memory.graphExpansionMode", "ALWAYS");
        System.setProperty("graphExpansionThreshold", "2.0");
        System.setProperty("spector.benchmark.graphExpansionThreshold", "2.0");

        String model = System.getProperty("geminiModel", "gemini-3.1-flash-lite");
        GoogleProviderFactory googleFactory = new GoogleProviderFactory();
        ProviderConfig providerConfig = new ProviderConfig(
                "google", "google", model, apiKey,
                "", 0, Map.of("temperature", "0.1", "maxOutputTokens", "1024", "insecure", "true")
        );
        LlmProvider llm = googleFactory.createGenerationProvider(providerConfig)
                .orElseThrow(() -> new IllegalStateException("Failed to instantiate Google Gemini LLM Provider"));

        DatasetLoader loader = new DatasetLoader();
        LoadedDataset dataset = loader.load(datasetDir);

        Map<String, String> goldAnswerMap = loadGoldAnswers(datasetDir.resolve("queries.jsonl"));

        log.info("Starting Single Profile Generative QA-J Benchmark with Graph Expansion (ALWAYS)...");
        log.info("  Dataset: {}", datasetDir.toAbsolutePath());
        log.info("  Output:  {}", outputDir.toAbsolutePath());
        log.info("  Model:   {}", model);

        try (BenchmarkSetup setup = new BenchmarkSetup();
             EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();
             CachedEmbeddingProvider embedder = new CachedEmbeddingProvider(rawEmbedder, datasetDir.resolve("embeddings.bin"))) {

            com.spectrayan.spector.memory.aisme.config.AismeConfig aismeConfig =
                    com.spectrayan.spector.memory.aisme.config.AismeConfig.builder()
                            .enabled(true)
                            .globalWorkspaceCapacity(30)
                            .build();

            SpectorMemory memory = setup.createMemoryInstance(dataset, embedder, datasetDir, aismeConfig);
            log.info("Memory instance loaded with {} total memories", memory.totalMemories());

            RecallOptions recallOptions = RecallOptions.builder()
                    .topK(30)
                    .semanticCandidateMultiplier(6)
                    .recallMode(RecallMode.OBSERVE)
                    .enableTextSearch(true)
                    .enableLateralInhibition(true)
                    .aismeConfig(aismeConfig)
                    .graphExpansionThreshold(2.0f) // forces mode = ALWAYS
                    .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE)
                    .profile(CognitiveProfile.BALANCED)
                    .build();

            int totalQueries = dataset.queries().size();
            int correctCount = 0;
            Path qaResultsFile = outputDir.resolve("qa_judge_results.jsonl");

            List<Map<String, Object>> qaResults = new ArrayList<>();

            for (BenchmarkQuery q : dataset.queries()) {
                String gold = goldAnswerMap.getOrDefault(q.id(), "");
                List<CognitiveResult> results = memory.recall(q.text(), recallOptions);
                log.info("Query '{}' retrieved {} results:", q.id(), results.size());
                for (CognitiveResult res : results) {
                    log.info("  Candidate: id={}, type={}, score={}, breakdown={}",
                            res.id(), res.memoryType(), res.score(), res.breakdown());
                }

                int graphExpandedCount = 0;
                List<String> candidateTexts = new ArrayList<>();
                for (CognitiveResult res : results) {
                    if (res.hasBreakdown() && res.breakdown().graphBoost() > 0.001f) {
                        graphExpandedCount++;
                    }
                    if (res.text() != null && !res.text().isBlank()) {
                        candidateTexts.add(res.text().trim());
                    }
                }

                // Generative Answer via LLM
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("You are an attentive and intelligent personal memory companion with access to the user's autobiographical history.\n");
                promptBuilder.append("Answer the user's question accurately and concisely based strictly on the retrieved memories below.\n\n");
                promptBuilder.append("Retrieved Memories:\n");
                for (int i = 0; i < candidateTexts.size(); i++) {
                    promptBuilder.append(String.format("[%d] %s\n", i + 1, candidateTexts.get(i)));
                }
                promptBuilder.append("\nQuestion: ").append(q.text()).append("\n\n");
                promptBuilder.append("Direct Answer:\n");

                String generatedAnswer = llm.generate(promptBuilder.toString(), GenerationOptions.CONCISE);
                if (generatedAnswer != null && generatedAnswer.startsWith("Direct Answer:")) {
                    generatedAnswer = generatedAnswer.substring("Direct Answer:".length()).trim();
                }

                // Impartial LLM Judge
                String judgePrompt = String.format("""
                        Evaluate whether the candidate model answer accurately conveys and satisfies the core facts required by the question and ground truth expected answer.
                        Guidelines:
                        - If the candidate model answer correctly identifies the core subject/item/action/location/number asked in the question, mark it correct (true).
                        - Minor differences in phrasing, omitted unasked background details, or equivalent synonyms should be accepted as correct.
                        - Only mark false if the candidate answer is factually contradictory, completely wrong, or refused to answer.

                        Respond in valid JSON format:
                        {
                          "is_correct": true or false,
                          "reason": "Brief explanation of why it is correct or incorrect"
                        }

                        Question: %s
                        Ground Truth: %s
                        Candidate Answer: %s
                        """, q.text(), gold, generatedAnswer);

                String judgeResponse = llm.generate(judgePrompt, GenerationOptions.CONCISE);
                boolean isCorrect = false;
                String reason = "";
                try {
                    String cleanJson = judgeResponse.trim();
                    if (cleanJson.startsWith("```json")) {
                        cleanJson = cleanJson.substring(7, cleanJson.lastIndexOf("```")).trim();
                    } else if (cleanJson.startsWith("```")) {
                        cleanJson = cleanJson.substring(3, cleanJson.lastIndexOf("```")).trim();
                    }
                    JsonNode node = jsonMapper.readTree(cleanJson);
                    isCorrect = node.path("is_correct").asBoolean(false);
                    reason = node.path("reason").asText("");
                } catch (Exception e) {
                    isCorrect = judgeResponse.toUpperCase().contains("\"IS_CORRECT\": TRUE") || judgeResponse.toUpperCase().contains("YES");
                    reason = "Fallback parse: " + judgeResponse;
                }

                if (isCorrect) {
                    correctCount++;
                }

                log.info("Query [{}]: is_correct={}, graph_expanded_candidates={}, gold='{}', gen='{}'",
                        q.id(), isCorrect, graphExpandedCount, gold, generatedAnswer);

                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("query_id", q.id());
                rec.put("question", q.text());
                rec.put("gold_answer", gold);
                rec.put("generated_answer", generatedAnswer);
                rec.put("is_correct", isCorrect);
                rec.put("reason", reason);
                rec.put("graph_expanded_count", graphExpandedCount);
                rec.put("total_candidates", results.size());
                qaResults.add(rec);
            }

            double accuracy = totalQueries > 0 ? (correctCount * 100.0) / totalQueries : 0.0;
            log.info("════════════════════════════════════════════════════════════════════");
            log.info("🎉 Single Profile QA-J Benchmark Complete:");
            log.info("   Total Queries:    {}", totalQueries);
            log.info("   Correct Answers:  {}", correctCount);
            log.info("   Accuracy:         {}%", String.format("%.2f", accuracy));
            log.info("════════════════════════════════════════════════════════════════════");

            // Write JSONL & Summary
            try (java.io.BufferedWriter w = Files.newBufferedWriter(qaResultsFile)) {
                for (var r : qaResults) {
                    w.write(jsonMapper.writeValueAsString(r));
                    w.newLine();
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("timestamp", java.time.Instant.now().toString());
            summary.put("total_queries", totalQueries);
            summary.put("correct_answers", correctCount);
            summary.put("accuracy_pct", accuracy);
            summary.put("model", model);
            summary.put("graph_expansion_mode", "ALWAYS");

            Path summaryFile = outputDir.resolve("qa_summary.json");
            Files.writeString(summaryFile, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));

            assertTrue(accuracy >= 80.0, "QA-J Accuracy must be at least 80% (got " + accuracy + "%)");
        }
    }

    private static Map<String, String> loadGoldAnswers(Path queriesFile) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(queriesFile)) {
            return map;
        }
        try (java.io.BufferedReader reader = Files.newBufferedReader(queriesFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    JsonNode node = jsonMapper.readTree(line);
                    String qid = node.path("id").asText();
                    String gold = node.path("goldAnswer").asText(node.path("gold_answer").asText(""));
                    if (qid != null && !qid.isBlank()) {
                        map.put(qid, gold);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read gold answers: {}", e.getMessage());
        }
        return map;
    }
}
