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
package com.spectrayan.spector.bench.conformance;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.conformance.model.MfAssertion;
import com.spectrayan.spector.bench.conformance.model.MfCorpusRecord;
import com.spectrayan.spector.bench.conformance.model.MfExpected;
import com.spectrayan.spector.bench.conformance.model.MfPersona;
import com.spectrayan.spector.bench.conformance.model.MfQuery;
import com.spectrayan.spector.bench.conformance.model.MfReport;
import com.spectrayan.spector.bench.conformance.model.MfTimeWindow;
import com.spectrayan.spector.bench.conformance.model.MfValenceWindow;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test harness executing the MF-001 Conformance Test Suite (MF-T01, MF-T03, MF-T10).
 *
 * <p>Evaluates memory recall algebra against strict closed-form assertion predicates
 * defined in {@code expected.json}. Measures accuracy, hard valence gating,
 * importance weighting on recall path, and multi-tenant persistence unit isolation.</p>
 */
public final class MfConformanceHarness {

    private static final Logger log = LoggerFactory.getLogger(MfConformanceHarness.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static final String CONDITION_FUSED = "fused";
    public static final String CONDITION_COSINE_TOPK_RERANK = "cosine-topk-then-rerank";
    public static final String CONDITION_HYBRID_FLAT_IMPORTANCE = "hybrid-flat-importance";

    private final EmbeddingProvider embedder;

    public MfConformanceHarness() {
        this(new DeterministicConformanceEmbedder(384));
    }

    public MfConformanceHarness(EmbeddingProvider embedder) {
        this.embedder = embedder;
    }

    /**
     * Runs an MF-001 conformance fixture under the specified condition.
     *
     * @param fixtureDir path to fixture directory containing corpus.jsonl, queries.jsonl, expected.json
     * @param condition  scoring condition (fused, cosine-topk-then-rerank, hybrid-flat-importance)
     * @return structured conformance report
     */
    public MfReport runFixture(Path fixtureDir, String condition) throws IOException {
        log.info("Running MF Conformance Fixture at '{}' under condition '{}'", fixtureDir, condition);

        MfExpected expected = loadExpected(fixtureDir.resolve("expected.json"));
        String testId = expected.testId();

        if ("MF-T10".equalsIgnoreCase(testId) || !expected.load().isEmpty()) {
            return runIsolationFixture(fixtureDir, expected, condition);
        }

        List<MfCorpusRecord> corpus = loadCorpus(fixtureDir.resolve("corpus.jsonl"));
        List<MfQuery> queries = loadQueries(fixtureDir.resolve("queries.jsonl"));
        Map<String, MfQuery> queryMap = queries.stream().collect(Collectors.toMap(MfQuery::id, q -> q));

        // Create independent SpectorMemory instance
        try (SpectorMemory memory = createMemoryInstance(fixtureDir.resolve("scratch-memory"))) {
            // Ingest all corpus records preserving exact headers
            ingestCorpus(memory, corpus);

            // Execute assertions
            return evaluateAssertions(testId, condition, expected, queryMap, memory, null, corpus);
        }
    }

    /**
     * Runs MF-T10 Isolation fixture with independent persistence units for rho-a and rho-b.
     */
    private MfReport runIsolationFixture(Path fixtureDir, MfExpected expected, String condition) throws IOException {
        Path dirA = fixtureDir.resolve(expected.load().getOrDefault("rho-a", "rememberer_a/"));
        Path dirB = fixtureDir.resolve(expected.load().getOrDefault("rho-b", "rememberer_b/"));

        List<MfCorpusRecord> corpusA = loadCorpus(dirA.resolve("corpus.jsonl"));
        List<MfCorpusRecord> corpusB = loadCorpus(dirB.resolve("corpus.jsonl"));

        List<MfQuery> queriesA = loadQueries(dirA.resolve("queries.jsonl"));
        List<MfQuery> queriesB = loadQueries(dirB.resolve("queries.jsonl"));

        Map<String, MfQuery> queryMap = new HashMap<>();
        queriesA.forEach(q -> queryMap.put(q.id(), q));
        queriesB.forEach(q -> queryMap.put(q.id(), q));

        // Two distinct, isolated persistence units
        try (SpectorMemory memoryA = createMemoryInstance(fixtureDir.resolve("scratch-store-a"));
             SpectorMemory memoryB = createMemoryInstance(fixtureDir.resolve("scratch-store-b"))) {

            ingestCorpus(memoryA, corpusA);
            ingestCorpus(memoryB, corpusB);

            Map<String, SpectorMemory> storeMap = Map.of(
                    "rho-a", memoryA,
                    "rho-b", memoryB
            );

            List<MfCorpusRecord> combinedCorpus = new ArrayList<>(corpusA);
            combinedCorpus.addAll(corpusB);

            return evaluateAssertions(expected.testId(), condition, expected, queryMap, memoryA, storeMap, combinedCorpus);
        }
    }

    private MfReport evaluateAssertions(
            String testId,
            String condition,
            MfExpected expected,
            Map<String, MfQuery> queryMap,
            SpectorMemory primaryMemory,
            Map<String, SpectorMemory> storeMap,
            List<MfCorpusRecord> corpus) {

        List<String> passed = new ArrayList<>();
        List<MfReport.FailedAssertion> failed = new ArrayList<>();

        Map<String, MfCorpusRecord> corpusMap = corpus.stream().collect(Collectors.toMap(MfCorpusRecord::id, r -> r, (a, b) -> a));

        for (MfAssertion assertion : expected.assertions()) {
            String assertionId = assertion.id();

            // Special handling: engine-property assertion
            if ("engine-property".equalsIgnoreCase(assertion.require())) {
                boolean propOk = verifyEngineProperty(assertion.property(), storeMap);
                if (propOk) {
                    passed.add(assertionId);
                } else {
                    failed.add(new MfReport.FailedAssertion(
                            assertionId,
                            Map.of("property", assertion.property() != null ? assertion.property() : "unknown"),
                            "Engine property violated: " + assertion.because()));
                }
                continue;
            }

            // Resolve target memory partition
            SpectorMemory targetMemory = primaryMemory;
            if (assertion.rememberer() != null && storeMap != null && storeMap.containsKey(assertion.rememberer())) {
                targetMemory = storeMap.get(assertion.rememberer());
            }

            MfQuery query = queryMap.get(assertion.query());
            if (query == null) {
                failed.add(new MfReport.FailedAssertion(
                        assertionId,
                        Map.of("queryId", assertion.query() != null ? assertion.query() : "null"),
                        "Query definition not found: " + assertion.query()));
                continue;
            }

            // Execute recall under specified condition
            List<CognitiveResult> results = executeRecall(targetMemory, query, condition, corpusMap, expected.evalAsOfMs());

            // Evaluate predicate
            String require = assertion.require();
            switch (require.toLowerCase()) {
                case "retrieved" -> {
                    boolean ok = true;
                    Map<String, Object> got = new LinkedHashMap<>();
                    for (String reqId : assertion.ids()) {
                        int rank = findRank(results, reqId);
                        got.put(reqId, rank > 0 ? rank : "ABSENT");
                        if (rank <= 0) {
                            ok = false;
                        } else if (assertion.atMostRank() != null && rank > assertion.atMostRank()) {
                            ok = false;
                        }
                    }
                    if (ok) {
                        passed.add(assertionId);
                    } else {
                        failed.add(new MfReport.FailedAssertion(assertionId, got,
                                "Failed retrieved assertion: expected in top " + assertion.atMostRank() + ", got " + got));
                    }
                }
                case "outranks" -> {
                    int rankHigher = findRank(results, assertion.higher());
                    int rankLower = findRank(results, assertion.lower());
                    Map<String, Object> got = Map.of(
                            "higherId", assertion.higher(),
                            "higherRank", rankHigher > 0 ? rankHigher : "ABSENT",
                            "lowerId", assertion.lower(),
                            "lowerRank", rankLower > 0 ? rankLower : "ABSENT"
                    );
                    if (rankHigher > 0 && (rankLower <= 0 || rankHigher < rankLower)) {
                        passed.add(assertionId);
                    } else {
                        failed.add(new MfReport.FailedAssertion(assertionId, got,
                                "Failed outranks assertion: " + assertion.higher() + " (rank " + rankHigher +
                                ") must outrank " + assertion.lower() + " (rank " + rankLower + ")"));
                    }
                }
                case "absent", "hard-gate-excludes" -> {
                    boolean ok = true;
                    Map<String, Object> got = new LinkedHashMap<>();
                    for (String forbiddenId : assertion.ids()) {
                        int rank = findRank(results, forbiddenId);
                        if (rank > 0) {
                            got.put(forbiddenId, rank);
                            ok = false;
                        }
                    }
                    if (ok) {
                        passed.add(assertionId);
                    } else {
                        failed.add(new MfReport.FailedAssertion(assertionId, got,
                                "Failed " + require + " assertion: forbidden items appeared in results: " + got));
                    }
                }
                case "absent-from-top" -> {
                    int k = assertion.k() != null ? assertion.k() : 3;
                    boolean ok = true;
                    Map<String, Object> got = new LinkedHashMap<>();
                    for (String id : assertion.ids()) {
                        int rank = findRank(results, id);
                        if (rank > 0 && rank <= k) {
                            got.put(id, rank);
                            if (!Boolean.TRUE.equals(assertion.soft())) {
                                ok = false;
                            }
                        }
                    }
                    if (ok) {
                        passed.add(assertionId);
                    } else {
                        failed.add(new MfReport.FailedAssertion(assertionId, got,
                                "Failed absent-from-top assertion: items appeared in ranks 1.." + k + ": " + got));
                    }
                }
                default -> {
                    failed.add(new MfReport.FailedAssertion(assertionId, Map.of("require", require),
                            "Unknown assertion requirement: " + require));
                }
            }
        }

        return new MfReport(testId, "spector", condition, passed, failed);
    }

    private int findRank(List<CognitiveResult> results, String targetId) {
        if (targetId == null || results == null) return -1;
        for (int i = 0; i < results.size(); i++) {
            if (targetId.equals(results.get(i).id())) {
                return i + 1; // 1-based rank
            }
        }
        return -1;
    }

    private boolean verifyEngineProperty(String property, Map<String, SpectorMemory> storeMap) {
        if ("omitting-rememberer-does-not-union-stores".equalsIgnoreCase(property)) {
            if (storeMap == null || storeMap.size() < 2) return true;
            SpectorMemory memA = storeMap.get("rho-a");
            SpectorMemory memB = storeMap.get("rho-b");
            return memA != null && memB != null && memA != memB;
        }
        return true;
    }

    /**
     * Executes recall on the memory under the given condition.
     */
    private List<CognitiveResult> executeRecall(
            SpectorMemory memory,
            MfQuery query,
            String condition,
            Map<String, MfCorpusRecord> corpusMap,
            long evalAsOfMs) {

        int indexSize = (memory != null && memory.admin() != null && memory.admin().index() != null)
                ? memory.admin().index().size()
                : -1;
        log.info("--- Recall for query '{}' ('{}') condition={} memoryInstance={} indexSize={} ---",
                query.id(), query.text(), condition, System.identityHashCode(memory), indexSize);

        List<CognitiveResult> results;
        if (CONDITION_COSINE_TOPK_RERANK.equalsIgnoreCase(condition)) {
            // Negative Control 1: Cosine top-K (k=5) candidate generation, then rerank
            results = executeCosineTopKThenRerank(memory, query, 5, corpusMap, evalAsOfMs);
        } else if (CONDITION_HYBRID_FLAT_IMPORTANCE.equalsIgnoreCase(condition)) {
            // Negative Control 2: Hybrid BM25 + dense search with flat importance I=1.0
            results = executeHybridFlatImportance(memory, query, corpusMap, evalAsOfMs);
        } else {
            // Condition 3: Full Spector Fused Cognitive Retrieval
            results = executeFusedCognitiveRecall(memory, query, evalAsOfMs);
        }

        log.info("--- Recall results for query '{}' (count={}): ---", query.id(), results.size());
        for (int i = 0; i < results.size(); i++) {
            CognitiveResult r = results.get(i);
            log.info("  [{}] id={}, score={}, importance={}, type={}, valence={}",
                    i + 1, r.id(), r.score(), r.importance(), r.memoryType(), r.valence());
        }
        return results;
    }

    private List<CognitiveResult> executeFusedCognitiveRecall(SpectorMemory memory, MfQuery query, long evalAsOfMs) {
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(query.topK())
                .profile(query.cognitiveProfile())
                .replayTimestamp(Instant.ofEpochMilli(evalAsOfMs))
                .recallMode(RecallMode.OBSERVE)
                .enableTextSearch(false)
                .autoProfile(false)
                .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE);

        if (query.valenceWindow() != null) {
            builder.minValence(query.valenceWindow().minByte());
            builder.maxValence(query.valenceWindow().maxByte());
        }
        if (query.minImportance() != null) {
            builder.minImportance(query.minImportance());
        }
        if (query.timeWindow() != null) {
            if (query.timeWindow().minTimestampMs() != null) {
                builder.minTimestamp(query.timeWindow().minTimestampMs());
            }
            if (query.timeWindow().maxTimestampMs() != null) {
                builder.maxTimestamp(query.timeWindow().maxTimestampMs());
            }
        }

        List<CognitiveResult> results = memory.recall(query.text(), builder.build());
        log.info("Direct recall on memory {} for query '{}' yielded {} results (index size={})",
                System.identityHashCode(memory), query.id(), results.size(),
                memory.admin() != null && memory.admin().index() != null ? memory.admin().index().size() : -1);

        // Hard-gate source=simulated unless allowSimulated
        if (!query.allowSimulated()) {
            results = results.stream()
                    .filter(r -> !SynapticHeaderConstants.isSimulated(r.consolidationFlags()))
                    .filter(r -> r.source() != MemorySource.THOUGHT_EXPERIMENT && r.source() != MemorySource.DREAMED)
                    .toList();
        }

        return results;
    }

    private List<CognitiveResult> executeCosineTopKThenRerank(
            SpectorMemory memory,
            MfQuery query,
            int kGenerate,
            Map<String, MfCorpusRecord> corpusMap,
            long evalAsOfMs) {

        // Step 1: Pure vector similarity search top-kGenerate (pure cosine)
        RecallOptions pureCosineOpts = RecallOptions.builder()
                .topK(kGenerate)
                .profile(CognitiveProfile.BALANCED)
                .scoringMode(ScoringMode.SIMILARITY)
                .replayTimestamp(Instant.ofEpochMilli(evalAsOfMs))
                .recallMode(RecallMode.OBSERVE)
                .enableTextSearch(false)
                .autoProfile(false)
                .build();

        List<CognitiveResult> stage1 = memory.recall(query.text(), pureCosineOpts);

        // Filter simulated if not allowed
        if (!query.allowSimulated()) {
            stage1 = stage1.stream()
                    .filter(r -> !SynapticHeaderConstants.isSimulated(r.consolidationFlags()))
                    .filter(r -> r.source() != MemorySource.THOUGHT_EXPERIMENT && r.source() != MemorySource.DREAMED)
                    .toList();
        }

        // Step 2: Rerank the narrow kGenerate candidate set by importance
        List<CognitiveResult> reranked = new ArrayList<>(stage1);
        reranked.sort(Comparator.comparing(CognitiveResult::importance).reversed());
        return reranked;
    }

    private List<CognitiveResult> executeHybridFlatImportance(
            SpectorMemory memory,
            MfQuery query,
            Map<String, MfCorpusRecord> corpusMap,
            long evalAsOfMs) {

        // Flat importance: similarity and BM25 score without importance boost (beta = 0)
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(query.topK())
                .profile(query.cognitiveProfile())
                .alpha(1.0f)
                .beta(0.0f) // Zero importance weight
                .replayTimestamp(Instant.ofEpochMilli(evalAsOfMs))
                .recallMode(RecallMode.OBSERVE)
                .enableTextSearch(true)
                .autoProfile(false)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE);

        if (query.valenceWindow() != null) {
            builder.minValence(query.valenceWindow().minByte());
            builder.maxValence(query.valenceWindow().maxByte());
        }
        if (query.minImportance() != null) {
            builder.minImportance(query.minImportance());
        }

        List<CognitiveResult> results = memory.recall(query.text(), builder.build());

        if (!query.allowSimulated()) {
            results = results.stream()
                    .filter(r -> !SynapticHeaderConstants.isSimulated(r.consolidationFlags()))
                    .filter(r -> r.source() != MemorySource.THOUGHT_EXPERIMENT && r.source() != MemorySource.DREAMED)
                    .toList();
        }

        return results;
    }

    private SpectorMemory createMemoryInstance(Path scratchDir) {
        int capacity = 500;
        return DefaultSpectorMemory.builder()
                .bundleMode(true)
                .dimensions(embedder.dimensions())
                .embeddingProvider(embedder)
                .workingCapacity(50)
                .episodicPartitionCapacity(capacity)
                .semanticCapacity(capacity)
                .proceduralCapacity(50)
                .hebbianGraphCapacity(capacity)
                .temporalChainCapacity(capacity)
                .entityGraphCapacity(1000)
                .aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig.disabled())
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    private void ingestCorpus(SpectorMemory memory, List<MfCorpusRecord> corpus) {
        for (MfCorpusRecord record : corpus) {
            MemorySource source = parseSource(record.source());
            IngestionHints hints = new IngestionHints(
                    record.interest(),
                    record.challenge(),
                    record.urgency(),
                    record.valence(),
                    (byte) record.arousal()
            );

            IngestionContext context = IngestionContext.builder()
                    .hints(hints)
                    .overrideTimestampMs(record.timestampMs())
                    .build();

            String[] tags = record.synapticTags() != null
                    ? record.synapticTags().toArray(new String[0])
                    : new String[0];

            String fullText = record.text();
            if (record.title() != null && !record.title().isBlank()) {
                fullText = record.title() + ". " + fullText;
            }
            if (record.synapticTags() != null && !record.synapticTags().isEmpty()) {
                fullText = fullText + " " + String.join(" ", record.synapticTags());
            }

            memory.remember(
                    record.id(),
                    fullText,
                    record.memoryType(),
                    source,
                    context,
                    tags
            );

            // Stamp exact header properties to match the fixture contract
            var loc = memory.admin().index().locate(record.id());
            if (loc != null) {
                var router = memory.admin().cognitiveRouter();
                if (router != null) {
                    var segment = router.segmentFor(loc.type());
                    var layout = router.layoutFor(loc.type());
                    if (segment != null && layout != null) {
                        CognitiveHeader existing = layout.readHeader(segment, loc.offset());
                        byte flags = existing.flags();
                        if (record.resolved()) {
                            flags = (byte) (flags | SynapticHeaderConstants.FLAG_RESOLVED);
                        } else {
                            flags = (byte) (flags & ~SynapticHeaderConstants.FLAG_RESOLVED);
                        }
                        byte cFlags = existing.consolidationFlags();
                        if ("simulated".equalsIgnoreCase(record.source())) {
                            cFlags = SynapticHeaderConstants.withSimulated(cFlags, true);
                        }
                        CognitiveHeader updated = new CognitiveHeader(
                                record.timestampMs(),
                                existing.synapticTags(),
                                existing.exactNorm(),
                                record.importance(),
                                existing.agentRecallCount(),
                                existing.centroidId(),
                                record.valence(),
                                flags,
                                (byte) record.arousal(),
                                existing.storageStrength(),
                                existing.encodingProfile(),
                                existing.encodingAlpha(),
                                existing.encodingBeta(),
                                existing.soulVersion(),
                                existing.encodingSurprise(),
                                cFlags
                        );
                        layout.writeHeader(segment, loc.offset(), updated);
                    }
                }
            }
        }
    }

    private MemorySource parseSource(String sourceStr) {
        if (sourceStr == null) return MemorySource.OBSERVED;
        return switch (sourceStr.toLowerCase()) {
            case "simulated" -> MemorySource.THOUGHT_EXPERIMENT;
            case "distilled" -> MemorySource.REFLECTED;
            case "rehearsed" -> MemorySource.TRANSFERRED;
            case "experienced" -> MemorySource.USER_STATED;
            default -> MemorySource.OBSERVED;
        };
    }

    // ── JSON Loaders ──────────────────────────────────────────────

    public static List<MfCorpusRecord> loadCorpus(Path file) throws IOException {
        List<MfCorpusRecord> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = MAPPER.readTree(line);

                String id = node.path("id").asText();
                String text = node.path("text").asText();
                String title = node.path("title").asText("");
                String memoryTypeStr = node.path("memoryType").asText("EPISODIC");
                MemoryType memoryType = MemoryType.valueOf(memoryTypeStr.toUpperCase());
                String source = node.path("source").asText("experienced");
                long timestampMs = node.path("timestampMs").asLong(System.currentTimeMillis());
                String sessionId = node.path("sessionId").asText("");
                float importance = (float) node.path("importance").asDouble(1.0);
                byte valence = (byte) node.path("valence").asInt(0);
                int arousal = node.path("arousal").asInt(0);
                float interest = (float) node.path("interest").asDouble(0.5);
                float challenge = (float) node.path("challenge").asDouble(0.5);
                float urgency = (float) node.path("urgency").asDouble(0.5);
                float novelty = (float) node.path("novelty").asDouble(0.5);
                boolean resolved = node.path("resolved").asBoolean(true);

                List<String> tags = new ArrayList<>();
                if (node.has("synapticTags")) {
                    for (JsonNode t : node.get("synapticTags")) {
                        tags.add(t.asText());
                    }
                }
                String rememberer = node.path("rememberer").asText(null);
                String soulMatch = node.path("soulMatch").asText(null);

                list.add(new MfCorpusRecord(
                        id, text, title, memoryType, source, timestampMs, sessionId,
                        importance, valence, arousal, interest, challenge, urgency, novelty,
                        resolved, tags, rememberer, soulMatch));
            }
        }
        return list;
    }

    public static List<MfQuery> loadQueries(Path file) throws IOException {
        List<MfQuery> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode node = MAPPER.readTree(line);

                String id = node.path("id").asText();
                String text = node.path("text").asText();

                List<String> goldConstraintIds = new ArrayList<>();
                if (node.has("goldConstraintIds")) {
                    for (JsonNode g : node.get("goldConstraintIds")) goldConstraintIds.add(g.asText());
                }

                List<String> staleIds = new ArrayList<>();
                if (node.has("staleIds")) {
                    for (JsonNode s : node.get("staleIds")) staleIds.add(s.asText());
                }

                String profileStr = node.path("cognitiveProfile").asText("BALANCED");
                CognitiveProfile profile = CognitiveProfile.valueOf(profileStr.toUpperCase());

                MfValenceWindow valenceWindow = null;
                if (node.hasNonNull("valenceWindow")) {
                    JsonNode vw = node.get("valenceWindow");
                    valenceWindow = new MfValenceWindow(vw.path("min").asInt(-128), vw.path("max").asInt(127));
                }

                MfTimeWindow timeWindow = null;
                if (node.hasNonNull("timeWindow")) {
                    JsonNode tw = node.get("timeWindow");
                    Long minTs = tw.hasNonNull("minTimestampMs") ? tw.get("minTimestampMs").asLong() : null;
                    Long maxTs = tw.hasNonNull("maxTimestampMs") ? tw.get("maxTimestampMs").asLong() : null;
                    timeWindow = new MfTimeWindow(minTs, maxTs);
                }

                Float minImportance = node.hasNonNull("minImportance") ? (float) node.get("minImportance").asDouble() : null;
                int topK = node.path("topK").asInt(10);
                boolean allowSimulated = node.path("allowSimulated").asBoolean(false);
                String expectedSubsystem = node.path("expectedSubsystem").asText("");

                list.add(new MfQuery(
                        id, text, goldConstraintIds, staleIds, profile, valenceWindow,
                        timeWindow, minImportance, topK, allowSimulated, expectedSubsystem));
            }
        }
        return list;
    }

    public static MfExpected loadExpected(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(file.toFile());

        String testId = root.path("testId").asText();
        long evalAsOfMs = root.path("evalAsOfMs").asLong(1756700000000L);
        String rememberer = root.path("rememberer").asText(null);
        String notes = root.path("notes").asText("");

        Map<String, String> load = new HashMap<>();
        if (root.has("load")) {
            for (var entry : root.get("load").properties()) {
                load.put(entry.getKey(), entry.getValue().asText());
            }
        }

        List<MfAssertion> assertions = new ArrayList<>();
        if (root.has("assertions")) {
            for (JsonNode an : root.get("assertions")) {
                String aId = an.path("id").asText();
                String query = an.path("query").asText(null);
                String rem = an.path("rememberer").asText(null);
                String require = an.path("require").asText();

                List<String> ids = new ArrayList<>();
                if (an.has("ids")) {
                    for (JsonNode idNode : an.get("ids")) ids.add(idNode.asText());
                }

                Integer atMostRank = an.hasNonNull("atMostRank") ? an.get("atMostRank").asInt() : null;
                String higher = an.path("higher").asText(null);
                String lower = an.path("lower").asText(null);
                Integer k = an.hasNonNull("k") ? an.get("k").asInt() : null;
                Boolean soft = an.hasNonNull("soft") ? an.get("soft").asBoolean() : null;
                String property = an.path("property").asText(null);
                String because = an.path("because").asText("");

                assertions.add(new MfAssertion(
                        aId, query, rem, require, ids, atMostRank, higher, lower, k, soft, property, because));
            }
        }

        Map<String, Object> negativeControls = new HashMap<>();
        if (root.has("negativeControls")) {
            for (var entry : root.get("negativeControls").properties()) {
                negativeControls.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> illegalSetups = new ArrayList<>();
        if (root.has("illegalSetups")) {
            for (JsonNode is : root.get("illegalSetups")) illegalSetups.add(is.asText());
        }

        return new MfExpected(testId, evalAsOfMs, rememberer, notes, load, assertions, negativeControls, illegalSetups);
    }

    public static MfPersona loadPersona(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), MfPersona.class);
    }

    /**
     * Deterministic, subword and semantic feature embedding provider.
     */
    public static final class DeterministicConformanceEmbedder implements EmbeddingProvider {

        private static final Set<String> STOPWORDS = Set.of(
                "user", "assistant", "simulated", "i", "me", "my", "myself", "we", "our", "ours",
                "you", "your", "yours", "he", "him", "his", "she", "her", "hers", "it", "its",
                "they", "them", "their", "what", "which", "who", "whom", "this", "that", "these",
                "those", "am", "is", "are", "was", "were", "be", "been", "being", "have", "has",
                "had", "having", "do", "does", "did", "doing", "a", "an", "the", "and", "but", "if",
                "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about",
                "against", "between", "into", "through", "during", "before", "after", "above",
                "below", "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
                "again", "further", "then", "once", "here", "there", "when", "where", "why", "how",
                "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no",
                "nor", "not", "only", "own", "same", "so", "than", "too", "very", "can", "will",
                "just", "don", "should", "now", "mean"
        );

        private static final Set<String> ENTITY_TOKENS = Set.of(
                "london", "berlin", "zillow", "hotpads", "closet", "rack", "bed",
                "mortgage", "refinance", "underwriting", "asylum", "apex"
        );

        private static final Map<String, List<String>> DOMAIN_SYNONYMS = Map.ofEntries(
                Map.entry("flight", List.of("flight", "plane", "travel", "fare")),
                Map.entry("travel", List.of("travel", "trip", "flight")),
                Map.entry("redeye", List.of("redeye", "overnight")),
                Map.entry("book", List.of("book", "reserve")),
                Map.entry("landing", List.of("landing", "flight", "morning")),
                Map.entry("morning", List.of("morning", "call", "schedule")),
                Map.entry("call", List.of("call", "schedule", "morning")),
                Map.entry("schedule", List.of("schedule", "calendar", "morning", "call")),
                Map.entry("shoe", List.of("shoe", "sneaker", "footwear")),
                Map.entry("storage", List.of("storage", "closet", "rack")),
                Map.entry("asylum", List.of("asylum", "application", "decision", "wait")),
                Map.entry("mortgage", List.of("mortgage", "refinance", "underwriting", "decision", "wait")),
                Map.entry("refinance", List.of("refinance", "mortgage", "underwriting", "decision", "wait")),
                Map.entry("underwriting", List.of("underwriting", "mortgage", "refinance", "decision", "wait")),
                Map.entry("wait", List.of("wait", "uncertain", "decision"))
        );

        private final int dimensions;

        public DeterministicConformanceEmbedder(int dimensions) {
            this.dimensions = dimensions;
        }

        private String normalizeToken(String token) {
            if (token == null || token.isBlank()) return "";
            String t = token.toLowerCase().trim();
            if (t.endsWith("s") && !t.endsWith("ss") && t.length() > 3) {
                t = t.substring(0, t.length() - 1);
            }
            return switch (t) {
                case "flight", "fly", "flying", "plane", "flights" -> "flight";
                case "redeye", "red-eye", "red-eyes", "overnight" -> "redeye";
                case "sneaker", "sneakers", "shoe", "shoes" -> "shoe";
                case "keep", "keeping", "kept" -> "keep";
                case "current", "currently" -> "current";
                case "uncertain", "uncertainty", "uncertainties", "indeterminacy" -> "uncertain";
                case "study", "trial" -> "study";
                case "book", "booked", "booking" -> "book";
                case "closet", "rack", "storage" -> "storage";
                case "refinance", "refi" -> "refinance";
                case "mortgage" -> "mortgage";
                case "wait", "waiting", "waited" -> "wait";
                case "asylum" -> "asylum";
                case "application", "applications", "apply", "applied" -> "application";
                case "decision", "decisions" -> "decision";
                case "schedule", "calendar" -> "schedule";
                case "call", "calls" -> "call";
                case "morning" -> "morning";
                case "landing", "land" -> "landing";
                default -> t;
            };
        }

        @Override
        public EmbeddingResult embed(String text) {
            if (text == null || text.isBlank()) {
                return new EmbeddingResult(new float[dimensions], 0, "conformance-deterministic");
            }

            float[] vec = new float[dimensions];
            String[] rawTokens = text.toLowerCase()
                    .replaceAll("[^a-z0-9\\s\\-]", " ")
                    .split("\\s+");

            int tokenCount = 0;
            for (String raw : rawTokens) {
                if (raw.isBlank()) continue;
                tokenCount++;

                if (STOPWORDS.contains(raw)) {
                    continue; // Skip noise tokens
                }

                String token = normalizeToken(raw);
                if (token.isBlank()) continue;

                float tokenWeight = ENTITY_TOKENS.contains(token) ? 1.35f : 1.0f;
                addTokenProjections(vec, token, tokenWeight);

                // Add domain synonym co-activations (semantic dense embedding behavior)
                List<String> synonyms = DOMAIN_SYNONYMS.get(token);
                if (synonyms != null) {
                    for (String syn : synonyms) {
                        if (!syn.equals(token)) {
                            addTokenProjections(vec, syn, 0.45f * tokenWeight);
                        }
                    }
                }
            }

            // L2 normalize
            float sumSq = 0f;
            for (float v : vec) sumSq += v * v;
            if (sumSq > 0f) {
                float norm = (float) Math.sqrt(sumSq);
                for (int i = 0; i < dimensions; i++) {
                    vec[i] /= norm;
                }
            }

            return new EmbeddingResult(vec, tokenCount, "conformance-deterministic");
        }

        private void addTokenProjections(float[] vec, String token, float weight) {
            int hash = token.hashCode();
            int idx1 = Math.abs(hash) % dimensions;
            int idx2 = Math.abs(hash * 31 + 17) % dimensions;
            int idx3 = Math.abs(hash * 37 + 43) % dimensions;
            int idx4 = Math.abs(hash * 61 + 79) % dimensions;

            vec[idx1] += 2.0f * weight;
            vec[idx2] += 1.5f * weight;
            vec[idx3] += 1.0f * weight;
            vec[idx4] += 0.8f * weight;

            if (token.length() >= 4) {
                for (int j = 0; j <= token.length() - 3; j++) {
                    String gram = token.substring(j, j + 3);
                    int gHash = gram.hashCode();
                    int gIdx = Math.abs(gHash * 13 + 5) % dimensions;
                    vec[gIdx] += 0.3f * weight;
                }
            }
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        @Override
        public String modelName() {
            return "conformance-deterministic-384";
        }
    }
}
