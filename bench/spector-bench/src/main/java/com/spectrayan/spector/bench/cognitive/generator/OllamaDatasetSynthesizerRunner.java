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
package com.spectrayan.spector.bench.cognitive.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.DatasetLoader;
import com.spectrayan.spector.bench.cognitive.DatasetLoader.LoadedDataset;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkQuery;
import com.spectrayan.spector.bench.cognitive.model.EntityMention;
import com.spectrayan.spector.bench.cognitive.model.EntityRelation;
import com.spectrayan.spector.bench.cognitive.model.HebbianEdgeDef;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.bench.cognitive.model.RelevanceJudgment;
import com.spectrayan.spector.bench.cognitive.model.TemporalChainDef;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Robust, sequential, checkpoint-resumable dataset generation engine that calls local Ollama
 * LLMs to synthesize authentic multi-turn conversations grounded in a 4-generation kinship graph
 * and 3-year narrative timeline, followed by vector pre-caching.
 */
public final class OllamaDatasetSynthesizerRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaDatasetSynthesizerRunner.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static void main(String[] args) {
        String defaultDir = System.getProperty(
                "datasetDir",
                System.getProperty("spector.bench.dataset.dir",
                        System.getenv().getOrDefault("DATASET_DIR", "../spector-datasets/balanced-baseline/data")));
        Path datasetDir = Paths.get(defaultDir);
        String chatModel = System.getProperty("chatModel", System.getenv().getOrDefault("CHAT_MODEL", "llama3.1:latest"));
        String embeddingModel = System.getProperty("embedModel",
                System.getProperty("spector.embed.model", System.getenv().getOrDefault("EMBED_MODEL", "nomic-embed-text:latest")));
        String ollamaUrl = System.getProperty("ollamaUrl", System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434"));
        int targetRecords = Integer.parseInt(System.getProperty("targetRecords", System.getenv().getOrDefault("TARGET_RECORDS", "50000")));
        int totalDays = Integer.parseInt(System.getProperty("totalDays", System.getenv().getOrDefault("TOTAL_DAYS", "1050")));

        for (String arg : args) {
            if (arg.startsWith("--dataset-dir=")) datasetDir = Paths.get(arg.substring(arg.indexOf('=') + 1));
            if (arg.startsWith("--chat-model=")) chatModel = arg.substring(arg.indexOf('=') + 1);
            if (arg.startsWith("--embed-model=")) embeddingModel = arg.substring(arg.indexOf('=') + 1);
            if (arg.startsWith("--ollama-url=")) ollamaUrl = arg.substring(arg.indexOf('=') + 1);
            if (arg.startsWith("--target-records=")) targetRecords = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
            if (arg.startsWith("--total-days=")) totalDays = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
        }

        log.info("=== Starting Ollama Sequential Dataset Synthesizer ===");
        log.info("Dataset Directory: {}", datasetDir);
        log.info("Chat LLM Model: {}", chatModel);
        log.info("Embedding Model: {}", embeddingModel);
        log.info("Ollama Base URL: {}", ollamaUrl);
        log.info("Target Records: {}", targetRecords);
        log.info("Timeline Duration: {} days (~3 years)", totalDays);

        OllamaDatasetSynthesizerRunner synthesizer = new OllamaDatasetSynthesizerRunner(
                datasetDir, chatModel, embeddingModel, ollamaUrl, targetRecords, totalDays);
        synthesizer.run();
    }

    private final Path datasetDir;
    private final String chatModel;
    private final String embeddingModel;
    private final String ollamaUrl;
    private final int targetRecords;
    private final int totalDays;
    private final HttpClient httpClient;
    private final Random rnd = new Random(42);

    public OllamaDatasetSynthesizerRunner(Path datasetDir, String chatModel, String embeddingModel,
                                         String ollamaUrl, int targetRecords, int totalDays) {
        this.datasetDir = datasetDir;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.ollamaUrl = ollamaUrl;
        this.targetRecords = targetRecords;
        this.totalDays = totalDays;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void run() {
        try {
            DatasetLoader loader = new DatasetLoader();
            LoadedDataset existing = loader.load(datasetDir);
            List<BenchmarkCorpusRecord> corpus = new ArrayList<>(existing.corpus());
            log.info("Loaded {} existing corpus records", corpus.size());

            Path checkpointFile = datasetDir.resolve(".synthesis_checkpoint.json");
            int startDay = 1;
            int lastMemoryNum = corpus.size();

            if (Files.exists(checkpointFile)) {
                try {
                    JsonNode cpNode = MAPPER.readTree(checkpointFile);
                    startDay = cpNode.path("completedDay").asInt(0) + 1;
                    lastMemoryNum = cpNode.path("lastMemoryNum").asInt(corpus.size());
                    log.info("Found synthesis checkpoint. Resuming from Day {}, Memory #{}", startDay, lastMemoryNum);
                } catch (Exception e) {
                    log.warn("Could not read checkpoint, starting from current corpus count", e);
                }
            }

            LocalDate timelineStart = LocalDate.of(2024, 1, 1);
            int currentMemoryNum = lastMemoryNum;

            log.info("Starting sequential Ollama generation across remaining days...");
            long overallStartTime = System.currentTimeMillis();

            for (int day = startDay; day <= totalDays && corpus.size() < targetRecords; day++) {
                LocalDate currentDate = timelineStart.plusDays(day - 1);
                long dayBaseEpochMs = currentDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

                // 3 realistic conversational sessions per day
                String[] sessionTimes = {"morning-briefing", "afternoon-collaboration", "evening-journal"};
                List<BenchmarkCorpusRecord> dayRecords = new ArrayList<>();

                for (String sessionTime : sessionTimes) {
                    String sessionId = String.format("session-%s-%s", currentDate.toString(), sessionTime);
                    List<BenchmarkCorpusRecord> sessionRecords = generateOllamaSessionWithRetry(
                            currentDate, day, sessionTime, sessionId, currentMemoryNum, dayBaseEpochMs);

                    if (!sessionRecords.isEmpty()) {
                        currentMemoryNum += sessionRecords.size();
                        corpus.addAll(sessionRecords);
                        dayRecords.addAll(sessionRecords);
                    }
                }

                // Append new day records to daily partition file
                if (!dayRecords.isEmpty()) {
                    Path dailyFile = datasetDir.resolve("daily").resolve(String.format("corpus-day-%04d.jsonl", day));
                    writeJsonl(dailyFile, dayRecords);
                }

                // Save Checkpoint every 5 days
                if (day % 5 == 0 || corpus.size() >= targetRecords || day == totalDays) {
                    saveCheckpoint(checkpointFile, day, currentMemoryNum, corpus.size());
                    writeMasterDatasetFiles(corpus);

                    double elapsedMin = (System.currentTimeMillis() - overallStartTime) / 60000.0;
                    double rateRecsPerMin = corpus.size() / Math.max(0.1, elapsedMin);
                    log.info(">>> Progress: Day {}/{} | Corpus: {}/{} records ({}) | Elapsed: {:.1f} min ({:.1f} rec/min)",
                            day, totalDays, corpus.size(), targetRecords,
                            String.format(Locale.ROOT, "%.1f%%", corpus.size() * 100.0 / targetRecords),
                            elapsedMin, rateRecsPerMin);
                }
            }

            // Final Write & Embedding Precache
            log.info("Finalizing dataset files and precaching embeddings...");
            writeMasterDatasetFiles(corpus);
            precacheEmbeddings(corpus);

            log.info("=== Ollama Dataset Synthesis Run COMPLETE ===");

        } catch (Exception e) {
            log.error("Ollama dataset synthesis encountered an error", e);
            throw new RuntimeException(e);
        }
    }

    private List<BenchmarkCorpusRecord> generateOllamaSessionWithRetry(
            LocalDate date, int day, String timeOfDay, String sessionId, int startMemoryNum, long baseTimestampMs) {

        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String prompt = buildSessionPrompt(date, day, timeOfDay);
                String rawJson = callOllamaChatApi(prompt);
                List<BenchmarkCorpusRecord> records = parseOllamaSessionResponse(
                        rawJson, sessionId, startMemoryNum, baseTimestampMs, date);

                if (!records.isEmpty()) {
                    return records;
                }
                log.warn("Empty session parsed on attempt {} for Day {}, retrying...", attempt, day);
            } catch (Exception e) {
                log.warn("Ollama call failed on attempt {}/{} for Day {} ({}): {}",
                        attempt, maxRetries, day, timeOfDay, e.getMessage());
                try {
                    Thread.sleep(1500L * attempt);
                } catch (InterruptedException ignored) {}
            }
        }

        // Fallback to grounded biographical generator if Ollama is unreachable
        return fallbackSession(date, day, timeOfDay, sessionId, startMemoryNum, baseTimestampMs);
    }

    private String buildSessionPrompt(LocalDate date, int day, String timeOfDay) {
        return """
        Generate 1 authentic multi-turn conversation session between Mike Thompson (Principal PM at Vertex Health) and his AI personal assistant Jarvis.
        Date: %s (Day %d of 3-year narrative), Time: %s.
        Narrative Context:
        - Protagonist: Mike Thompson (Principal PM at Vertex Health in Frisco, TX).
        - Family: Wife Sarah (UX Designer/Educator), Ethan (age 8-10, soccer captain, piano, STEM robotics), Lily (age 3-5, preschool, swim, ballet), Cooper (Golden Retriever).
        - Extended Kinship: Great-grandpa Arthur (1944 Elgin watch), Great-grandpa Sal Moretti (Sunday gravy), Dad Tom (rehab post knee replacement), Mom Linda (librarian), late Robert Miller (Lie-Nielsen hand planes), Dr. Emily Reed (Denver ER), cousin Leo Moretti (Austin brewmaster), cousin Jessica Reed (veterinarian).
        - Career: CareConnect patient portal, FHIR schemas, VP Greg Holloway, junior PM Anika Patel.
        - Woodworking: Live-edge walnut dining table, Lie-Nielsen bench chisels, Texas brisket smoking.

        Output ONLY valid JSON with this schema:
        {
          "turns": [
            {
              "speaker": "Mike or Jarvis",
              "text": "Exact dialogue utterance",
              "title": "Short title",
              "tags": ["careconnect", "work", "kinship"],
              "valence": 25,
              "importance": 1.5,
              "arousal": 35,
              "type": "EPISODIC or SEMANTIC or PROCEDURAL",
              "entities": [{"name": "CareConnect", "type": "SOFTWARE"}]
            }
          ]
        }
        """.formatted(date.toString(), day, timeOfDay);
    }

    private String callOllamaChatApi(String userPrompt) throws IOException, InterruptedException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", chatModel);
        root.put("stream", false);
        root.put("format", "json");

        ArrayNode messages = root.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "You are a specialized AI benchmark dataset synthesizer. You output strictly valid, parseable JSON.");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        ObjectNode options = root.putObject("options");
        options.put("temperature", 0.7);

        String requestBody = MAPPER.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode resNode = MAPPER.readTree(response.body());
        return resNode.path("message").path("content").asText();
    }

    private List<BenchmarkCorpusRecord> parseOllamaSessionResponse(
            String rawJson, String sessionId, int startMemoryNum, long baseTimestampMs, LocalDate date) {

        List<BenchmarkCorpusRecord> list = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            JsonNode turnsNode = root.path("turns");
            if (!turnsNode.isArray() || turnsNode.isEmpty()) {
                return list;
            }

            int memNum = startMemoryNum;
            long currentTimestamp = baseTimestampMs;

            for (JsonNode turn : turnsNode) {
                String id = String.format("mem_%05d", ++memNum);
                String text = turn.path("text").asText("");
                if (text.isBlank()) continue;

                String title = turn.path("title").asText(text.length() > 30 ? text.substring(0, 30) + "..." : text);
                byte valence = (byte) Math.max(-128, Math.min(127, turn.path("valence").asInt(10)));
                float importance = (float) Math.max(0.1, Math.min(10.0, turn.path("importance").asDouble(1.0)));
                int arousal = Math.max(0, Math.min(255, turn.path("arousal").asInt(30)));

                String typeStr = turn.path("type").asText("EPISODIC").toUpperCase();
                MemoryType type = MemoryType.EPISODIC;
                try {
                    type = MemoryType.valueOf(typeStr);
                } catch (Exception ignored) {}

                List<String> tags = new ArrayList<>();
                for (JsonNode tagNode : turn.path("tags")) {
                    tags.add(tagNode.asText().toLowerCase());
                }
                if (tags.isEmpty()) tags.add("daily-dialogue");

                List<EntityMention> entities = new ArrayList<>();
                for (JsonNode entNode : turn.path("entities")) {
                    String eName = entNode.path("name").asText();
                    String eType = entNode.path("type").asText("CONCEPT");
                    if (!eName.isBlank()) {
                        entities.add(new EntityMention(eName, eType));
                    }
                }
                if (entities.isEmpty()) {
                    entities.add(new EntityMention("Mike Thompson", "PERSON"));
                    entities.add(new EntityMention("Jarvis", "SOFTWARE"));
                }

                currentTimestamp += (30 + rnd.nextInt(90)) * 1000L;

                BenchmarkCorpusRecord record = new BenchmarkCorpusRecord(
                        id, text, title, tags, valence, importance, arousal,
                        sessionId, currentTimestamp, entities, type, 0
                );
                list.add(record);
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON response: {}", e.getMessage());
        }
        return list;
    }

    private List<BenchmarkCorpusRecord> fallbackSession(
            LocalDate date, int day, String timeOfDay, String sessionId, int startMemoryNum, long baseTimestampMs) {

        List<BenchmarkCorpusRecord> list = new ArrayList<>();
        int memNum = startMemoryNum;
        long ts = baseTimestampMs + (timeOfDay.equals("morning-briefing") ? 8 * 3600000L : (timeOfDay.equals("afternoon-collaboration") ? 14 * 3600000L : 20 * 3600000L));

        String text = String.format("Discussed %s logistics and life updates with Jarvis on %s [Day %d].", timeOfDay, date, day);
        BenchmarkCorpusRecord rec = new BenchmarkCorpusRecord(
                String.format("mem_%05d", ++memNum), text, "Daily Conversation",
                List.of("routine", "life-log"), (byte) 15, 1.2f, 25,
                sessionId, ts, List.of(new EntityMention("Mike Thompson", "PERSON"), new EntityMention("Jarvis", "SOFTWARE")),
                MemoryType.EPISODIC, 0
        );
        list.add(rec);
        return list;
    }

    private void saveCheckpoint(Path cpFile, int completedDay, int lastMemoryNum, int totalCount) {
        try {
            ObjectNode cp = MAPPER.createObjectNode();
            cp.put("completedDay", completedDay);
            cp.put("lastMemoryNum", lastMemoryNum);
            cp.put("totalCorpusCount", totalCount);
            cp.put("timestamp", System.currentTimeMillis());
            Files.writeString(cpFile, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(cp));
        } catch (Exception e) {
            log.warn("Failed to write checkpoint", e);
        }
    }

    private void writeMasterDatasetFiles(List<BenchmarkCorpusRecord> corpus) throws IOException {
        log.info("Writing master corpus.jsonl ({} records)...", corpus.size());
        writeJsonl(datasetDir.resolve("corpus.jsonl"), corpus);

        GraphBuilder graphBuilder = new GraphBuilder();
        List<EntityRelation> entityRelations = graphBuilder.buildEntityGraph(corpus);
        List<TemporalChainDef> temporalChains = graphBuilder.buildTemporalChains(corpus);
        List<HebbianEdgeDef> hebbianEdges = graphBuilder.buildHebbianEdges(corpus);

        writeJsonl(datasetDir.resolve("entities.jsonl"), entityRelations);
        writeJsonl(datasetDir.resolve("temporal_chains.jsonl"), temporalChains);
        writeJsonl(datasetDir.resolve("hebbian_edges.jsonl"), hebbianEdges);
    }

    private void precacheEmbeddings(List<BenchmarkCorpusRecord> corpus) {
        Path cacheFile = datasetDir.resolve("embeddings.bin");
        Set<String> uniqueTexts = new LinkedHashSet<>();
        for (BenchmarkCorpusRecord r : corpus) {
            if (r.text() != null && !r.text().isBlank()) {
                uniqueTexts.add(r.text());
            }
        }
        EmbeddingProvider raw = OllamaEmbeddingProvider.create(embeddingModel);
        try (CachedEmbeddingProvider cached = new CachedEmbeddingProvider(raw, cacheFile)) {
            List<String> list = new ArrayList<>(uniqueTexts);
            int batchSize = 128;
            int total = list.size();
            long start = System.currentTimeMillis();

            for (int i = 0; i < total; i += batchSize) {
                int end = Math.min(i + batchSize, total);
                List<String> batch = list.subList(i, end);

                int maxAttempts = 5;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    try {
                        cached.embedBatch(batch);
                        break;
                    } catch (Exception ex) {
                        if (attempt == maxAttempts) {
                            log.warn("Failed embedding batch {}-{} after {} attempts: {}", i, end, maxAttempts, ex.getMessage());
                        } else {
                            try { Thread.sleep(3000L * attempt); } catch (InterruptedException ignored) {}
                        }
                    }
                }

                // Small 50ms pause between batches to prevent Windows ephemeral socket exhaustion
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ignored) {}

                if ((i + batchSize) % 640 == 0 || end == total) {
                    double elapsedSec = (System.currentTimeMillis() - start) / 1000.0;
                    double rate = end / Math.max(0.1, elapsedSec);
                    double pct = (end * 100.0 / total);
                    log.info("Precached embeddings {}/{} items ({}%) -- rate: {} items/sec",
                            end, total, String.format(Locale.ROOT, "%.1f", pct),
                            String.format(Locale.ROOT, "%.1f", rate));
                }
            }
            log.info("Vector pre-caching finished successfully!");
        } catch (Exception e) {
            log.error("Embedding precaching error", e);
        }
    }

    private <T> void writeJsonl(Path path, List<T> items) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (T item : items) {
                writer.write(MAPPER.writeValueAsString(item));
                writer.newLine();
            }
        }
    }
}