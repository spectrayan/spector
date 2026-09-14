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
package com.spectrayan.spector.bench.cognitive.mindspan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.bench.cognitive.CachedEmbeddingProvider;
import com.spectrayan.spector.bench.cognitive.MetricsComputer;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkCorpusRecord;
import com.spectrayan.spector.bench.cognitive.model.PersonaDef;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorConfigSource;
import com.spectrayan.spector.config.model.TextSearchMode;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.config.properties.CircadianProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;

/**
 * Dedicated test runner for empirically validating the Active Inference Self-Model Engine (AISME)
 * pathway against the MindSpan benchmark dataset.
 *
 * <p>Executes dual-track comparative evaluation:
 * <ol>
 *   <li>Condition A (Baseline): Standard Cognitive Recall without active inference self-modeling.</li>
 *   <li>Condition B (AISME): Active Inference pathway with Homeostatic Bias, Free-Energy Guided Relays (FERS),
 *       Continuous Hopfield Attractors, and Global Workspace conscious access capacity bounding ($K \le 7$).</li>
 * </ol>
 */
public final class MindSpanAismeRunner {

    private static final Logger log = LoggerFactory.getLogger(MindSpanAismeRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path datasetDir;
    private final Path outputDir;
    private final int queryLimit;
    private final MetricsComputer metricsComputer;

    public record ComparisonResult(
            String queryId,
            String queryText,
            String goldAnswer,
            int baselineCandidateCount,
            int aismeCandidateCount,
            String baselineTopId,
            String aismeTopId,
            float baselineTopScore,
            float aismeTopScore,
            float deltaF,
            float fersScore,
            boolean workspaceBounded
    ) {}

    public record MindSpanAismeReport(
            int totalMemoriesIngested,
            int queriesEvaluated,
            int workspaceGatedQueries,
            double averageDeltaF,
            List<ComparisonResult> comparisons
    ) {}

    public MindSpanAismeRunner(Path datasetDir, Path outputDir, int queryLimit) {
        this.datasetDir = datasetDir;
        this.outputDir = outputDir;
        this.queryLimit = queryLimit > 0 ? queryLimit : 10;
        this.metricsComputer = new MetricsComputer();
    }

    public static Path resolveMindSpanDataDir() {
        String prop = System.getProperty("datasetDir");
        if (prop != null && !prop.isBlank()) {
            Path p = Paths.get(prop).toAbsolutePath().normalize();
            return p.getFileName().toString().equals("data") ? p : p.resolve("data");
        }
        String env = System.getenv("MINDSPAN_DATASET_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env).toAbsolutePath().normalize();
            return p.getFileName().toString().equals("data") ? p : p.resolve("data");
        }
        Path curr = Paths.get(".").toAbsolutePath().normalize();
        while (curr != null) {
            Path candidate = curr.resolve("spector-datasets").resolve("mindspan").resolve("data");
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
            Path sibling = curr.resolve("..").resolve("spector-datasets").resolve("mindspan").resolve("data").normalize();
            if (Files.exists(sibling)) {
                return sibling;
            }
            curr = curr.getParent();
        }
        return Paths.get("..", "spector-datasets", "mindspan", "data").toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = resolveMindSpanDataDir();
        Path outDir = dataDir.resolve("results").resolve("aisme-runner");
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : 10;

        MindSpanAismeRunner runner = new MindSpanAismeRunner(dataDir, outDir, limit);
        runner.run();
    }

    public MindSpanAismeReport run() throws Exception {
        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  MindSpan AISME Pathway Empirical Validation Runner                ║");
        log.info("║  Data Dir: {} (Queries={})                                         ║", datasetDir, queryLimit);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        if (Files.exists(outputDir)) {
            try (var s = Files.walk(outputDir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(outputDir);

        // 1. Load configuration from spector-bench.yml if present
        Path ymlFile = datasetDir.resolve("spector-bench.yml");
        SpectorConfigSource configSource = null;
        AismeProperties aismeProps = null;
        if (Files.exists(ymlFile)) {
            try {
                configSource = SpectorConfigSource.load(ymlFile);
                aismeProps = SpectorConfigFactory.aismeProperties(configSource);
            } catch (Exception e) {
                log.warn("Could not load spector-bench.yml: {}", e.getMessage());
            }
        }
        if (aismeProps == null) {
            aismeProps = AismeProperties.builder()
                    .enabled(true)
                    .enableHomeostasis(true)
                    .enableFreeEnergy(true)
                    .enableHopfield(true)
                    .enableManifold(true)
                    .enableGlobalWorkspace(true)
                    .globalWorkspaceCapacity(7)
                    .build();
        } else {
            aismeProps.setEnabled(true);
        }

        // 2. Load Persona definition
        Path personaFile = datasetDir.resolve("persona.json");
        PersonaDef persona = null;
        if (Files.exists(personaFile)) {
            persona = MAPPER.readValue(personaFile.toFile(), PersonaDef.class);
            log.info("Loaded Persona: {} (age {})", persona.name(), persona.age());
        }

        // 3. Load milestone memories (corpus-biographical.jsonl)
        Path bioCorpus = datasetDir.resolve("corpus-biographical.jsonl");
        if (!Files.exists(bioCorpus)) {
            bioCorpus = datasetDir.resolve("corpus.jsonl");
        }
        List<BenchmarkCorpusRecord> records = loadCorpusRecords(bioCorpus);
        log.info("Loaded {} milestone records for AISME pathway testing", records.size());

        // 4. Setup embedding provider (Ollama with cached backing if available)
        Path cacheFile = datasetDir.resolve("embeddings.bin");
        EmbeddingProvider rawEmbedder = OllamaEmbeddingProvider.createDefault();
        EmbeddingProvider embedder = Files.exists(cacheFile)
                ? new CachedEmbeddingProvider(rawEmbedder, cacheFile)
                : rawEmbedder;

        // 5. Build identity context (UserSoul & AgentSoul)
        float[] identityVec = new float[768];
        try {
            if (persona != null && persona.lifeContext() != null) {
                identityVec = embedder.embed(persona.lifeContext()).vector();
            }
        } catch (Exception e) {
            log.warn("Could not compute identity embedding via Ollama: {}", e.getMessage());
        }

        UserSoul userSoul = new UserSoul(
                "user-mike-thompson",
                persona != null ? persona.name() : "Mike Thompson",
                persona != null ? persona.lifeContext() : "MindSpan Persona",
                null,
                identityVec
        );

        AgentSoul agentSoul = AgentSoul.builder()
                .id("jarvis-mindspan")
                .name("Jarvis")
                .systemPrompt(persona != null ? persona.companionRelationship() : "Spector Companion")
                .purposeEmbedding(identityVec)
                .expertiseEmbedding(identityVec)
                .build();

        // 6. Build SpectorMemory instance
        MemoryProperties memProps = new MemoryProperties()
                .setDimensions(768)
                .setMaxNamespaces(1)
                .setEpisodicPartitionCapacity(Math.max(10_000, records.size() + 100))
                .setSemanticCapacity(Math.max(10_000, records.size() + 100))
                .setCircadian(CircadianProperties.builder().volumeTrigger(Integer.MAX_VALUE).build());
        memProps.setAisme(aismeProps);

        try (SpectorMemory memory = SpectorMemory.builder(memProps)
                .embeddingProvider(embedder)
                .persistence(outputDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .bundleMode(true)
                .soul(agentSoul)
                .soulContexts(List.of(userSoul, agentSoul))
                .build()) {

            // Ingest records
            log.info("Ingesting {} memories into Spector...", records.size());
            for (BenchmarkCorpusRecord r : records) {
                String text = r.text();
                long ts = r.timestampMs() > 0 ? r.timestampMs() : System.currentTimeMillis();
                MemorySource source = MemorySource.OBSERVED;
                if (text != null) {
                    if (text.startsWith("user:") || text.startsWith("User:")) {
                        source = MemorySource.USER_STATED;
                    } else if (text.startsWith("assistant:") || text.startsWith("Jarvis:")) {
                        source = MemorySource.INFERRED;
                    }
                }
                RememberHints hints = new RememberHints(
                        r.interest(), r.challenge(), r.urgency(),
                        r.valence(), (byte) r.arousal()
                );
                com.spectrayan.spector.memory.model.RememberContext ctx =
                        com.spectrayan.spector.memory.model.RememberContext.builder()
                                .hints(hints)
                                .overrideTimestampMs(ts)
                                .build();
                List<String> tags = r.synapticTags() != null ? r.synapticTags() : List.of();
                memory.remember(r.id(), r.text(), r.memoryType(), source, ctx, tags.toArray(String[]::new));
            }
            log.info("Ingestion complete. Total memories in store: {}", memory.totalMemories());

            // 7. Load queries
            Path queriesFile = datasetDir.resolve("queries.jsonl");
            List<MindSpanQuery> queries = loadQueries(queriesFile, queryLimit);
            log.info("Loaded {} queries for comparative validation", queries.size());

            // 8. Run Dual-Condition Evaluation
            List<ComparisonResult> comparisons = new ArrayList<>();
            int workspaceGatedCount = 0;
            double totalDeltaF = 0.0;

            for (MindSpanQuery query : queries) {
                String qText = query.text();

                // Condition A: Baseline Recall (AISME Disabled, TopK=15)
                RecallOptions baseOptions = RecallOptions.builder()
                        .topK(15)
                        .recallMode(RecallMode.OBSERVE)
                        .enableAisme(false)
                        .enableMmr(true)
                        .build();
                List<CognitiveResult> baseResults = memory.recall(qText, baseOptions);

                // Condition B: AISME Pathway (AISME Active, GWS Capacity=7)
                RecallOptions aismeOptions = RecallOptions.builder()
                        .topK(15)
                        .recallMode(RecallMode.OBSERVE)
                        .enableAisme(true)
                        .aismeConfig(aismeProps)
                        .enableLateralInhibition(true)
                        .enableMmr(true)
                        .build();
                List<CognitiveResult> aismeResults = memory.recall(qText, aismeOptions);

                String baseTopId = !baseResults.isEmpty() ? baseResults.get(0).id() : "NONE";
                float baseTopScore = !baseResults.isEmpty() ? baseResults.get(0).score() : 0.0f;

                String aismeTopId = !aismeResults.isEmpty() ? aismeResults.get(0).id() : "NONE";
                float aismeTopScore = !aismeResults.isEmpty() ? aismeResults.get(0).score() : 0.0f;

                float deltaF = 0.0f;
                float fersScore = aismeTopScore;
                if (!aismeResults.isEmpty() && aismeResults.get(0).breakdown() != null) {
                    ScoreBreakdown sb = aismeResults.get(0).breakdown();
                    deltaF = sb.teleologicalWeight() > 0 ? (sb.finalScore() - sb.similarity() * sb.epistemicWeight()) : 0.0f;
                    fersScore = sb.finalScore();
                }

                boolean wsBounded = aismeResults.size() <= aismeProps.globalWorkspaceCapacity();
                if (wsBounded) {
                    workspaceGatedCount++;
                }
                totalDeltaF += deltaF;

                ComparisonResult cmp = new ComparisonResult(
                        query.id(),
                        qText,
                        query.goldAnswer(),
                        baseResults.size(),
                        aismeResults.size(),
                        baseTopId,
                        aismeTopId,
                        baseTopScore,
                        aismeTopScore,
                        deltaF,
                        fersScore,
                        wsBounded
                );
                comparisons.add(cmp);

                log.info("Query [{}]: BaseCount={}, AismeCount={} (GWS bounded={}), BaseTop=[{}] (score={}), AismeTop=[{}] (score={})",
                        query.id(), baseResults.size(), aismeResults.size(), wsBounded,
                        baseTopId, String.format(java.util.Locale.ROOT, "%.4f", baseTopScore),
                        aismeTopId, String.format(java.util.Locale.ROOT, "%.4f", aismeTopScore));

                if (!aismeResults.isEmpty() && aismeResults.get(0).breakdown() != null) {
                    ScoreBreakdown sb = aismeResults.get(0).breakdown();
                    log.info("  ↳ AISME Telemetry [{}]: regime={}, sim={}, epistemic(α)={}, teleological(β)={}, pragmatic(γ)={}, finalScore={}",
                            aismeTopId, sb.scoringRegime(),
                            String.format(java.util.Locale.ROOT, "%.4f", sb.similarity()),
                            String.format(java.util.Locale.ROOT, "%.4f", sb.epistemicWeight()),
                            String.format(java.util.Locale.ROOT, "%.4f", sb.teleologicalWeight()),
                            String.format(java.util.Locale.ROOT, "%.4f", sb.pragmaticWeight()),
                            String.format(java.util.Locale.ROOT, "%.4f", sb.finalScore()));
                }
                log.info("  ↳ Baseline Top-5 Candidates: {}", baseResults.stream().limit(5).map(r -> r.id() + "(" + String.format(java.util.Locale.ROOT, "%.4f", r.score()) + ")").toList());
                log.info("  ↳ AISME GWS-7 Candidates:    {}", aismeResults.stream().limit(7).map(r -> r.id() + "(" + String.format(java.util.Locale.ROOT, "%.4f", r.score()) + ")").toList());
            }

            double avgDeltaF = comparisons.isEmpty() ? 0.0 : totalDeltaF / comparisons.size();
            MindSpanAismeReport report = new MindSpanAismeReport(
                    memory.totalMemories(),
                    comparisons.size(),
                    workspaceGatedCount,
                    avgDeltaF,
                    comparisons
            );

            log.info("\n════════════════════════════════════════════════════════════════════");
            log.info("AISME Validation Summary: Ingested={}, Queries={}, GWS Bounded={}/{}, Avg ΔF={}",
                    report.totalMemoriesIngested(), report.queriesEvaluated(),
                    report.workspaceGatedQueries(), report.queriesEvaluated(),
                    String.format(java.util.Locale.ROOT, "%.4f", report.averageDeltaF()));
            log.info("════════════════════════════════════════════════════════════════════\n");

            return report;
        }
    }

    private static List<BenchmarkCorpusRecord> loadCorpusRecords(Path path) throws IOException {
        List<BenchmarkCorpusRecord> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    list.add(MAPPER.readValue(line, BenchmarkCorpusRecord.class));
                }
            }
        }
        return list;
    }

    private static List<MindSpanQuery> loadQueries(Path path, int limit) throws IOException {
        List<MindSpanQuery> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && list.size() < limit) {
                line = line.trim();
                if (!line.isEmpty()) {
                    JsonNode n = MAPPER.readTree(line);
                    String id = n.path("id").asText();
                    String text = n.path("text").asText();
                    String gold = n.path("goldAnswer").asText("");
                    list.add(new MindSpanQuery(id, text, gold));
                }
            }
        }
        return list;
    }

    public record MindSpanQuery(String id, String text, String goldAnswer) {}
}
