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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.BigFiveTraits;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.UserSoul;
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

        MfPersona persona = null;
        Path personaFile = fixtureDir.resolve("persona.json");
        if (Files.exists(personaFile)) {
            persona = loadPersona(personaFile);
        }
        UserSoul userSoul = createUserSoul(persona);

        Path tempDir = Files.createTempDirectory("spector-mf-store-");
        // Create independent SpectorMemory instance backed by on-disk mmap storage and configured soul
        try (SpectorMemory memory = createMemoryInstance(tempDir, userSoul)) {
            // Ingest all corpus records preserving exact headers
            ingestCorpus(memory, corpus);

            // Execute assertions
            return evaluateAssertions(testId, condition, expected, queryMap, memory, null, corpus);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * Runs MF-T10 Isolation fixture with independent persistent on-disk storage units for rho-a and rho-b.
     *
     * <p><b>NF3 / M10 Multi-Tenant Persistence Validation:</b><br>
     * This test validates on-disk tenant isolation across separate {@link SpectorMemory} persistent units.
     * It proves that distinct stores maintain segregated on-disk index maps, write to separate directory
     * roots, and do not leak query results across scoped sessions or open queries.</p>
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

        MfPersona personaA = null;
        Path personaFileA = dirA.resolve("persona.json");
        if (Files.exists(personaFileA)) {
            personaA = loadPersona(personaFileA);
        }
        UserSoul soulA = createUserSoul(personaA);

        MfPersona personaB = null;
        Path personaFileB = dirB.resolve("persona.json");
        if (Files.exists(personaFileB)) {
            personaB = loadPersona(personaFileB);
        }
        UserSoul soulB = createUserSoul(personaB);

        Path tempDirA = Files.createTempDirectory("spector-mft10-store-a-");
        Path tempDirB = Files.createTempDirectory("spector-mft10-store-b-");

        // Two distinct, isolated on-disk persistence units (NF3 / M10) with distinct souls
        try (SpectorMemory memoryA = createMemoryInstance(tempDirA, soulA);
             SpectorMemory memoryB = createMemoryInstance(tempDirB, soulB)) {

            ingestCorpus(memoryA, corpusA);
            ingestCorpus(memoryB, corpusB);

            Map<String, SpectorMemory> storeMap = Map.of(
                    "rho-a", memoryA,
                    "rho-b", memoryB
            );

            List<MfCorpusRecord> allCorpus = new ArrayList<>(corpusA);
            allCorpus.addAll(corpusB);

            return evaluateAssertions(expected.testId(), condition, expected, queryMap, memoryA, storeMap, allCorpus);
        } finally {
            cleanupTempDir(tempDirA);
            cleanupTempDir(tempDirB);
        }
    }

    private SpectorMemory createMemoryInstance(Path storePath, UserSoul soul) {
        SpectorMemoryBuilder builder = SpectorMemoryBuilder.create()
                .embeddingProvider(this.embedder);
        if (storePath != null) {
            builder.persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(storePath);
        } else {
            builder.persistenceMode(MemoryPersistenceMode.IN_MEMORY);
        }
        if (soul != null) {
            builder.soul(soul);
        }
        return builder.build();
    }

    private UserSoul createUserSoul(MfPersona persona) {
        if (persona == null) return null;

        BigFiveTraits b5 = null;
        if (persona.bigFive() != null && !persona.bigFive().isEmpty()) {
            double o = persona.bigFive().getOrDefault("openness", 0.5);
            double c = persona.bigFive().getOrDefault("conscientiousness", 0.5);
            double e = persona.bigFive().getOrDefault("extraversion", 0.5);
            double a = persona.bigFive().getOrDefault("agreeableness", 0.5);
            double n = persona.bigFive().getOrDefault("neuroticism", 0.5);
            float oF = (float) (o <= 1.0 ? o * 100.0 : o);
            float cF = (float) (c <= 1.0 ? c * 100.0 : c);
            float eF = (float) (e <= 1.0 ? e * 100.0 : e);
            float aF = (float) (a <= 1.0 ? a * 100.0 : a);
            float nF = (float) (n <= 1.0 ? n * 100.0 : n);
            b5 = new BigFiveTraits(oF, cF, eF, aF, nF);
        }

        String about = persona.lifeContext() != null && !persona.lifeContext().isBlank()
                ? persona.lifeContext()
                : (persona.soulRule() != null ? persona.soulRule() : "");

        PersonaContext personaContext = PersonaContext.builder()
                .occupation(persona.occupation())
                .about(about)
                .values(persona.likes() != null ? persona.likes() : List.of())
                .fears(persona.dislikes() != null ? persona.dislikes() : List.of())
                .aspirations(persona.interests() != null ? persona.interests() : List.of())
                .bigFive(b5)
                .build();

        float[] identityVec = this.embedder.embed(about).vector();
        return new UserSoul(
                persona.rememberer() != null ? persona.rememberer() : "rho-a",
                persona.name() != null ? persona.name() : "Persona",
                persona.soulRule() != null ? persona.soulRule() : "",
                personaContext,
                identityVec
        );
    }

    private static void cleanupTempDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception ignored) {}
    }

    /**
     * Evaluates all assertions defined in the fixture expected.json against the memory store.
     */
    private MfReport evaluateAssertions(
            String testId,
            String condition,
            MfExpected expected,
            Map<String, MfQuery> queryMap,
            SpectorMemory defaultMemory,
            Map<String, SpectorMemory> storeMap,
            List<MfCorpusRecord> corpus) {

        List<String> passed = new ArrayList<>();
        List<MfReport.FailedAssertion> failed = new ArrayList<>();
        Map<String, MfCorpusRecord> corpusMap = corpus.stream()
                .collect(Collectors.toMap(MfCorpusRecord::id, r -> r, (r1, r2) -> r1));

        for (MfAssertion assertion : expected.assertions()) {
            String assertionId = assertion.id();

            // Handle engine-property assertions
            if ("engine-property".equalsIgnoreCase(assertion.require())) {
                boolean verified = verifyEngineProperty(assertion.property(), storeMap);
                if (verified) {
                    passed.add(assertionId);
                } else {
                    failed.add(new MfReport.FailedAssertion(
                            assertionId,
                            Map.of("property", assertion.property()),
                            "Failed engine property: " + assertion.because()));
                }
                continue;
            }

            SpectorMemory targetMemory = defaultMemory;
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
                    boolean pass = assertion.soft()
                            ? (rankHigher > 0 && (rankLower <= 0 || rankHigher < rankLower))
                            : (rankHigher > 0 && rankLower > 0 && rankHigher < rankLower);
                    if (pass) {
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

    /**
     * Verifies engine properties specified in fixture manifests.
     * Note: This currently tests in-memory multi-instance index/query boundaries (first CI gate),
     * not shared-directory mmap partition layouts.
     */
    private boolean verifyEngineProperty(String property, Map<String, SpectorMemory> storeMap) {
        if ("omitting-rememberer-does-not-union-stores".equalsIgnoreCase(property)) {
            if (storeMap == null || storeMap.size() < 2) return true;
            SpectorMemory memA = storeMap.get("rho-a");
            SpectorMemory memB = storeMap.get("rho-b");
            if (memA == null || memB == null || memA == memB) return false;

            // Query memA with no rememberer predicate
            RecallOptions openOpts = RecallOptions.builder()
                    .topK(10)
                    .profile(CognitiveProfile.BALANCED)
                    .enableTextSearch(false)
                    .build();

            List<CognitiveResult> aResults = memA.recall("How long did I wait?", openOpts);
            for (CognitiveResult r : aResults) {
                if (r.id().startsWith("b-")) {
                    log.error("Isolation failure: Rememberer B's trace '{}' leaked into Rememberer A's query", r.id());
                    return false;
                }
            }

            List<CognitiveResult> bResults = memB.recall("How long did I wait?", openOpts);
            for (CognitiveResult r : bResults) {
                if (r.id().startsWith("a-")) {
                    log.error("Isolation failure: Rememberer A's trace '{}' leaked into Rememberer B's query", r.id());
                    return false;
                }
            }

            // Verify index separation
            if (memA.admin() != null && memA.admin().index() != null) {
                if (memA.admin().index().locate("b-mortgage-wait") != null) return false;
            }
            if (memB.admin() != null && memB.admin().index() != null) {
                if (memB.admin().index().locate("a-asylum-wait") != null) return false;
            }

            return true;
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
            // Negative Control 2: True ablation (beta=0 flat importance)
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
                .allowSimulated(query.allowSimulated())
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

        return memory.recall(query.text(), builder.build());
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
                .allowSimulated(query.allowSimulated())
                .build();

        List<CognitiveResult> stage1 = memory.recall(query.text(), pureCosineOpts);

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

        // Flat importance ablation (beta = 0) with identical retrieval parameters
        RecallOptions.Builder builder = RecallOptions.builder()
                .topK(query.topK())
                .profile(query.cognitiveProfile())
                .alpha(1.0f)
                .beta(0.0f) // Zero importance weight
                .replayTimestamp(Instant.ofEpochMilli(evalAsOfMs))
                .recallMode(RecallMode.OBSERVE)
                .enableTextSearch(false)
                .autoProfile(false)
                .allowSimulated(query.allowSimulated())
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

        return memory.recall(query.text(), builder.build());
    }

    /**
     * Ingests a corpus into the Spector memory store, faithfully applying synaptic header properties.
     */
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

            // Ingest raw body text only (no title or tag concatenation into embedder input)
            String textToIngest = record.text();

            memory.remember(
                    record.id(),
                    textToIngest,
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
                        if (record.memoryType() != null) {
                            flags = SynapticHeaderConstants.withMemoryType(flags, record.memoryType().ordinal());
                        }
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
     * General deterministic subword & character n-gram embedding provider for unit/conformance testing.
     * Generates a 384-dimensional dense semantic vector without domain synonym tables.
     */
    public static final class DeterministicConformanceEmbedder implements EmbeddingProvider {

        private static final Set<String> STOPWORDS = Set.of(
                "user", "assistant", "simulated", "the", "a", "an", "is", "was", "are", "were",
                "be", "been", "being", "have", "has", "had", "do", "does", "did", "of", "to",
                "in", "for", "with", "on", "at", "by", "from", "and", "or", "but", "if",
                "it", "its", "i", "me", "my", "myself", "we", "our", "you", "your", "he", "him",
                "his", "she", "her", "they", "them", "their"
        );

        private final int dimensions;

        public DeterministicConformanceEmbedder(int dimensions) {
            this.dimensions = dimensions;
        }

        private String stemToken(String token) {
            if (token == null || token.isBlank()) return "";
            String t = token.toLowerCase().trim();
            if (t.endsWith("ing") && t.length() > 5) {
                t = t.substring(0, t.length() - 3);
            } else if (t.endsWith("ed") && t.length() > 4) {
                t = t.substring(0, t.length() - 2);
            } else if (t.endsWith("es") && t.length() > 4) {
                t = t.substring(0, t.length() - 2);
            } else if (t.endsWith("s") && !t.endsWith("ss") && t.length() > 3) {
                t = t.substring(0, t.length() - 1);
            }
            return t;
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
                    continue;
                }

                String token = stemToken(raw);
                if (token.isBlank()) continue;

                addTokenProjections(vec, token, 1.0f);
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
            long seed = token.hashCode();
            // Xorshift64 PRNG for deterministic, zero-allocation dense token projection
            long s = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
            for (int i = 0; i < dimensions; i++) {
                s ^= (s << 13);
                s ^= (s >>> 7);
                s ^= (s << 17);
                float val = ((s & 0xFFFF) / 32768.0f) - 1.0f; // [-1.0, +1.0]
                vec[i] += val * weight;
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
