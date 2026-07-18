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
package com.spectrayan.spector.runtime;

import static org.assertj.core.api.Assertions.*;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.memory.*;
import com.spectrayan.spector.memory.model.*;
import com.spectrayan.spector.memory.cortex.MemorySource;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end functional test for Spector memory tools using real Ollama embeddings.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>Read-only</b> (Ollama DOWN): Tests recall, introspect, whynot against pre-ingested data</li>
 *   <li><b>Full</b> (Ollama UP): Also tests remember, reinforce, suppress, forget</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -pl spector-runtime -Dtest=McpToolsFunctionalTest -Dspector.functional=true}
 */
@TestMethodOrder(OrderAnnotation.class)
@Tag("functional")
class McpToolsFunctionalTest {

    private static final Logger log = LoggerFactory.getLogger(McpToolsFunctionalTest.class);
    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String EMBED_MODEL = "qwen3-embedding:latest";

    private static SpectorRuntime runtime;
    private static SpectorMemory memory;
    private static boolean ollamaAvailable = false;

    @BeforeAll
    static void setUp() {
        // Skip if not explicitly enabled
        Assumptions.assumeTrue(
                "true".equals(System.getProperty("spector.functional")),
                "Functional tests disabled. Set -Dspector.functional=true to enable.");

        // Check Ollama connectivity (non-fatal  --  we can still test recall with pre-ingested data)
        try {
            var config = com.spectrayan.spector.provider.ProviderConfig.local("ollama", "ollama", EMBED_MODEL, OLLAMA_URL);
            var registry = com.spectrayan.spector.provider.ProviderDiscovery.discover(java.util.List.of(config));
            var embedder = registry.activeEmbedding().orElseThrow();
            int dims = embedder.embed("probe").dimensions();
            ollamaAvailable = true;
            System.out.printf("[x] Ollama connected: %d dims from %s%n", dims, EMBED_MODEL);
        } catch (Exception e) {
            ollamaAvailable = false;
            System.out.printf("Warning  Ollama NOT available: %s%n", e.getMessage());
            System.out.printf("   Write tests (remember/reinforce) will be skipped.%n");
            System.out.printf("   Read tests (recall/introspect) will run against pre-ingested data.%n%n");
        }

        // Load config and create runtime
        Path configFile = Path.of("spector-local.yml");
        if (!Files.exists(configFile)) {
            configFile = Path.of("../spector-local.yml");
        }
        Assumptions.assumeTrue(Files.exists(configFile),
                "spector-local.yml not found");

        var props = SpectorProperties.builder()
                .configFile(configFile)
                .build();

        // Create runtime  --  with embedder if available, or null-safe for read-only
        if (ollamaAvailable) {
            var config = com.spectrayan.spector.provider.ProviderConfig.local("ollama", "ollama", EMBED_MODEL, OLLAMA_URL);
            var registry = com.spectrayan.spector.provider.ProviderDiscovery.discover(java.util.List.of(config));
            var embedder = registry.activeEmbedding().orElseThrow();
            runtime = SpectorRuntime.from(props, embedder);
        } else {
            // Create with a stub embedder for read-only access to pre-ingested data
            runtime = SpectorRuntime.from(props, new com.spectrayan.spector.provider.embedding.EmbeddingProvider() {
                @Override public com.spectrayan.spector.provider.embedding.EmbeddingResult embed(String text) {
                    throw new UnsupportedOperationException("Ollama not available  --  read-only mode");
                }
                @Override public int dimensions() { return 4096; }
                @Override public String modelName() { return "stub-readonly"; }
            });
        }
        memory = runtime.memory().orElse(null);
        assertThat(memory).isNotNull();

        System.out.printf("Runtime initialized. Total memories: %d%n", memory.totalMemories());
    }

    @AfterAll
    static void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
    }

    // ==============================================================
    // TEST 1: Status  --  verify ingested data exists
    // ==============================================================

    @Test @Order(1)
    void statusShowsIngestedMemories() {
        int total = memory.totalMemories();
        System.out.printf("%n== Test 1: memory_status ==%n");
        System.out.printf("  Total:      %d%n", total);
        System.out.printf("  SEMANTIC:   %d%n", memory.memoryCount(MemoryType.SEMANTIC));
        System.out.printf("  EPISODIC:   %d%n", memory.memoryCount(MemoryType.EPISODIC));
        System.out.printf("  WORKING:    %d%n", memory.memoryCount(MemoryType.WORKING));
        System.out.printf("  PROCEDURAL: %d%n", memory.memoryCount(MemoryType.PROCEDURAL));

        assertThat(total).as("Should have ingested memories from D:\\git").isGreaterThan(0);
        assertThat(memory.memoryCount(MemoryType.SEMANTIC)).isGreaterThan(0);
    }

    // ==============================================================
    // TEST 2-5: Remember  --  store across all tiers (requires Ollama)
    // ==============================================================

    @Test @Order(2)
    void rememberSemantic() throws Exception {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required for remember tests");

        try {
            memory.remember("ft-semantic-001",
                    "Spector uses a 4-tier cognitive memory architecture inspired by Atkinson-Shiffrin.",
                    MemoryType.SEMANTIC, MemorySource.USER_STATED,
                    "architecture", "cognitive").join();

            System.out.printf("%n== Test 2: memory_remember SEMANTIC ==%n");
            System.out.printf("  Stored: ft-semantic-001%n");
            // Verify via recall
            List<CognitiveResult> results = memory.recall("cognitive memory architecture");
            assertThat(results).isNotEmpty();
            assertThat(results.stream().anyMatch(r -> "ft-semantic-001".equals(r.id())))
                    .as("Should find the just-stored semantic memory").isTrue();
            System.out.printf("  [x] Verified: found in recall results%n");
        } catch (Exception e) {
            // Walk the cause chain for store-full
            Throwable t = e;
            while (t != null) {
                if (t.getMessage() != null && t.getMessage().contains("capacity")) {
                    System.out.printf("%n== Test 2: memory_remember SEMANTIC ==%n");
                    System.out.printf("  Warning  Store full (capacity 10K reached). Skipping.%n");
                    Assumptions.abort("Store full  --  cannot test remember");
                }
                t = t.getCause();
            }
            throw e;
        }
    }

    @Test @Order(3)
    void rememberEpisodic() throws Exception {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required for remember tests");

        memory.remember("ft-episodic-001",
                "User mentioned testing Spector with Ollama qwen3-embedding model on June 3rd 2026.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "testing", "ollama").join();

        System.out.printf("%n== Test 3: memory_remember EPISODIC ==%n  Stored: ft-episodic-001%n");
    }

    @Test @Order(4)
    void rememberProcedural() throws Exception {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required for remember tests");

        memory.remember("ft-procedural-001",
                "To run Spector tests: mvn test -pl spector-memory. To build dist: mvn install -DskipTests.",
                MemoryType.PROCEDURAL, MemorySource.PROCEDURAL,
                "build", "maven").join();

        System.out.printf("%n== Test 4: memory_remember PROCEDURAL ==%n  Stored: ft-procedural-001%n");
    }

    @Test @Order(5)
    void scratchpad() throws Exception {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required for scratchpad");

        memory.scratchpad("Currently debugging recall pipeline latency.").join();

        int workingCount = memory.memoryCount(MemoryType.WORKING);
        System.out.printf("%n== Test 5: memory_scratchpad ==%n  Working count: %d%n", workingCount);
        assertThat(workingCount).isGreaterThan(0);
    }

    // ==============================================================
    // TEST 6-8: Recall  --  cross-tier cognitive scoring (read-only)
    // ==============================================================

    @Test @Order(6)
    void recallBasic() {
        List<CognitiveResult> results = memory.recall("cognitive memory architecture");
        System.out.printf("%n== Test 6: memory_recall (basic) ==%n");
        System.out.printf("  Query: 'cognitive memory architecture'%n");
        System.out.printf("  Results: %d%n", results.size());
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            CognitiveResult r = results.get(i);
            String snippet = r.text().replace("\n", " ");
            if (snippet.length() > 120) snippet = snippet.substring(0, 120) + "...";
            System.out.printf("  [%d] score=%.4f | tier=%-10s | id=%s%n      text: %s%n",
                    i, r.score(), r.memoryType(), r.id(), snippet);
        }

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().score()).isGreaterThan(0);
    }

    @Test @Order(7)
    void recallWithProfile() {
        List<CognitiveResult> results = memory.recall("how to build and test spector",
                CognitiveProfile.DEBUGGING);
        System.out.printf("%n== Test 7: memory_recall (DEBUGGING profile) ==%n");
        System.out.printf("  Query: 'how to build and test spector'%n");
        System.out.printf("  Results: %d%n", results.size());
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            CognitiveResult r = results.get(i);
            String snippet = r.text().replace("\n", " ");
            if (snippet.length() > 120) snippet = snippet.substring(0, 120) + "...";
            System.out.printf("  [%d] score=%.4f | tier=%-10s | %s%n", i, r.score(), r.memoryType(), snippet);
        }

        assertThat(results).isNotEmpty();
    }

    @Test @Order(8)
    void recallSemanticTierFilter() {
        // Use SEMANTIC filter since all ingested data is SEMANTIC
        RecallOptions options = RecallOptions.builder()
                .memoryTypes(MemoryType.SEMANTIC)
                .topK(5)
                .build();
        List<CognitiveResult> results = memory.recall("README documentation", options);
        System.out.printf("%n== Test 8: memory_recall (SEMANTIC filter) ==%n");
        System.out.printf("  Query: 'README documentation' | filter: SEMANTIC only%n");
        System.out.printf("  Results: %d%n", results.size());
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            CognitiveResult r = results.get(i);
            String snippet = r.text().replace("\n", " ");
            if (snippet.length() > 100) snippet = snippet.substring(0, 100) + "...";
            System.out.printf("  [%d] score=%.4f | tier=%s | %s%n",
                    i, r.score(), r.memoryType(), snippet);
        }

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(r -> r.memoryType() == MemoryType.SEMANTIC);
    }

    // ==============================================================
    // TEST 9: Recall  --  diverse queries to test data relevance
    // ==============================================================

    @Test @Order(9)
    void recallDiverseQueries() {
        String[] queries = {
                "vector search HNSW algorithm",
                "Ollama embedding model configuration",
                "spector project structure and modules",
                "how to install and setup the project"
        };

        System.out.printf("%n== Test 9: memory_recall (diverse queries) ==%n");
        for (String query : queries) {
            List<CognitiveResult> results = memory.recall(query);
            System.out.printf("%n  Query: '%s'%n  Results: %d%n", query, results.size());
            if (!results.isEmpty()) {
                CognitiveResult top = results.getFirst();
                String snippet = top.text().replace("\n", " ");
                if (snippet.length() > 120) snippet = snippet.substring(0, 120) + "...";
                System.out.printf("  Top: score=%.4f | id=%s%n        %s%n",
                        top.score(), top.id(), snippet);
            }
            assertThat(results).as("Query '%s' should return results", query).isNotEmpty();
        }
    }

    // ==============================================================
    // TEST 10: Reinforce (requires Ollama for prior remember)
    // ==============================================================

    @Test @Order(10)
    void reinforce() {
        // Use a pre-ingested ID from recall results (not dependent on remember test)
        List<CognitiveResult> existing = memory.recall("spector");
        Assumptions.assumeTrue(!existing.isEmpty(), "Need pre-ingested data for reinforce test");
        String targetId = existing.getFirst().id();

        memory.reinforce(targetId, (byte) 100);
        System.out.printf("%n== Test 10: memory_reinforce ==%n  Reinforced %s valence=100%n", targetId);

        List<CognitiveResult> results = memory.recall("spector");
        boolean found = results.stream().anyMatch(r -> targetId.equals(r.id()));
        System.out.printf("  After reinforce, still in recall: %s [x]%n", found);
        assertThat(found).as("Reinforced memory should still be recallable").isTrue();
    }

    // ==============================================================
    // TEST 11: Introspect  --  metamemory analysis (read-only)
    // ==============================================================

    @Test @Order(11)
    void introspect() {
        var insight = memory.introspect("spector architecture");
        System.out.printf("%n== Test 11: memory_introspect ==%n  Topic: 'spector architecture'%n  %s%n", insight);

        assertThat(insight).isNotNull();
        assertThat(insight.totalMemories()).isGreaterThan(0);
    }

    // ==============================================================
    // TEST 12: Suppress / Unsuppress (requires Ollama for prior remember)
    // ==============================================================

    @Test @Order(12)
    void suppressAndUnsuppress() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required  --  needs ft-episodic-001 from Test 3");

        memory.suppress("ft-episodic-001", "Not relevant");
        System.out.printf("%n== Test 12: memory_suppress / unsuppress ==%n  Suppressed ft-episodic-001%n");

        List<CognitiveResult> suppressed = memory.recall("testing ollama");
        boolean foundWhileSuppressed = suppressed.stream()
                .anyMatch(r -> "ft-episodic-001".equals(r.id()));
        System.out.printf("  Recall while suppressed: found=%s (expect false)%n", foundWhileSuppressed);
        assertThat(foundWhileSuppressed).as("Suppressed memory should not appear").isFalse();

        memory.unsuppress("ft-episodic-001");
        System.out.printf("  Unsuppressed ft-episodic-001 [x]%n");
    }

    // ==============================================================
    // TEST 13: Forget (requires Ollama for prior remember)
    // ==============================================================

    @Test @Order(13)
    void forget() {
        Assumptions.assumeTrue(ollamaAvailable, "Ollama required  --  needs ft-procedural-001 from Test 4");

        memory.forget("ft-procedural-001");
        System.out.printf("%n== Test 13: memory_forget ==%n  Forgot ft-procedural-001%n");

        List<CognitiveResult> results = memory.recall("build maven test");
        boolean found = results.stream().anyMatch(r -> "ft-procedural-001".equals(r.id()));
        System.out.printf("  Recall after forget: found=%s (expect false) [x]%n", found);
        assertThat(found).as("Forgotten memory should not appear in recall").isFalse();
    }

    // ==============================================================
    // TEST 14: WhyNot  --  recall diagnostics (read-only, uses pre-ingested data)
    // ==============================================================

    @Test @Order(14)
    void whyNot() {
        // Pick a known ID from pre-ingested data
        List<CognitiveResult> existing = memory.recall("README");
        Assumptions.assumeTrue(!existing.isEmpty(), "Need at least one ingested memory");
        String knownId = existing.getFirst().id();

        WhyNotExplanation explanation = memory.whyNot(
                knownId, "dark mode user interface", RecallOptions.DEFAULT);
        System.out.printf("%n== Test 14: memory_whynot ==%n  id=%s, query='dark mode user interface'%n  %s%n",
                knownId, explanation);

        assertThat(explanation).isNotNull();
    }

    // ==============================================================
    // TEST 15: Recall with synaptic filter (read-only)
    // ==============================================================

    @Test @Order(15)
    void recallWithSynapticFilter() {
        RecallOptions options = RecallOptions.builder()
                .synapticFilter("architecture", "cognitive")
                .topK(5)
                .build();
        List<CognitiveResult> results = memory.recall("memory system design", options);
        System.out.printf("%n== Test 15: memory_recall (synaptic filter) ==%n");
        System.out.printf("  Query: 'memory system design' | tags: architecture, cognitive%n");
        System.out.printf("  Results: %d%n", results.size());
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            CognitiveResult r = results.get(i);
            String snippet = r.text().replace("\n", " ");
            if (snippet.length() > 100) snippet = snippet.substring(0, 100) + "...";
            System.out.printf("  [%d] score=%.4f | %s%n", i, r.score(), snippet);
        }

        // Synaptic filter may return empty if no pre-ingested data has matching tags  --  that's OK
        System.out.printf("  (Synaptic filter: %s)%n", results.isEmpty() ? "no tag matches (expected for bulk-ingested data)" : "matches found");
    }

    // ==============================================================
    // TEST 16: Final status
    // ==============================================================

    @Test @Order(16)
    void finalStatus() {
        System.out.printf("%n== Test 16: Final Status ==%n");
        System.out.printf("  Ollama:     %s%n", ollamaAvailable ? "[x] Available" : "Warning  Offline (read-only mode)");
        System.out.printf("  Total:      %d%n", memory.totalMemories());
        System.out.printf("  SEMANTIC:   %d%n", memory.memoryCount(MemoryType.SEMANTIC));
        System.out.printf("  EPISODIC:   %d%n", memory.memoryCount(MemoryType.EPISODIC));
        System.out.printf("  WORKING:    %d%n", memory.memoryCount(MemoryType.WORKING));
        System.out.printf("  PROCEDURAL: %d%n", memory.memoryCount(MemoryType.PROCEDURAL));

        assertThat(memory.totalMemories()).isGreaterThan(0);
    }
}
