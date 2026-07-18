/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;
import com.spectrayan.spector.test.judge.LlmAssertions;
import com.spectrayan.spector.test.judge.LlmTestJudge;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Synapse memory APIs using a real Ollama embedding model.
 *
 * <h3>Prerequisites</h3>
 * <ul>
 *   <li>Ollama running on {@code localhost:11434}</li>
 *   <li>Models pulled: {@code ollama pull nomic-embed-text} and {@code ollama pull llama3.2}</li>
 *   <li>Set env var or system property: {@code OLLAMA_LIVE=true}</li>
 * </ul>
 *
 * <h3>Running</h3>
 * <pre>
 *   set OLLAMA_LIVE=true
 *   mvn test -pl spector-synapse -Dtest=SynapseMemoryIntegrationTest -am
 * </pre>
 *
 * <p>Tests are <b>skipped</b> automatically when {@code OLLAMA_LIVE} is not set.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spector.memory.enabled=true",
        "spector.memory.persistence-mode=IN_MEMORY",
        "spector.memory.dimensions=768",
        "spector.memory.capacity=1000",
        "spector.memory.splade-enabled=false",
        "spector.memory.colbert-enabled=false",
        "spector.embedding.model=nomic-embed-text",
        "spector.embedding.base-url=http://localhost:11434",
        "spector.ollama.base-url=http://localhost:11434",
        "spector.ollama.model=llama3.2",
        "spector.ollama.embed-model=nomic-embed-text",
        "spring.security.user.name=test",
        "spring.security.user.password=test"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Synapse Memory APIs — Integration Tests (Real Ollama)")
class SynapseMemoryIntegrationTest {

    @Autowired MemoryService memoryService;

    private static LlmTestJudge judge;

    /** Remembered memory IDs used across ordered tests. */
    private String semanticId;
    private String episodicId;
    private String workingId;

    @BeforeAll
    void checkOllamaAvailability() {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping integration tests — set OLLAMA_LIVE=true (env or -DOLLAMA_LIVE=true)");

        // Verify the bridge actually got a real SpectorMemory bean
        Assumptions.assumeTrue(memoryService.isEngineAvailable(),
                "SpectorMemory bean must be present — check Ollama is running and nomic-embed-text is pulled");

        // Initialize the LLM judge for semantic assertions (uses a fast small model)
        var llmProvider = OllamaLlmProvider.create("llama3.2");
        judge = LlmTestJudge.create(llmProvider)
                .withTemperature(0.1f)
                .withMaxRetries(2);
    }

    // ═══════════════════════════════════════════════════
    // 1. Remember — CRUD ingestion
    // ═══════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("remember: stores semantic memory and returns accepted response")
    void remember_semantic() throws Exception {
        var request = new RememberRequest(
                null,
                "Spector uses Panama Foreign Function & Memory API for off-heap synaptic storage. " +
                "This allows zero-copy access to 64-byte synaptic headers without GC pressure.",
                "SEMANTIC", "OBSERVED",
                null, // tags (comma-separated string)
                null, null, null, null, null
        );

        var response = memoryService.remember(request);

        assertThat(response.taskId()).isNotBlank();
        assertThat(response.id()).isNotBlank();
        semanticId = response.id();

        // Give the async remember time to complete
        TimeUnit.SECONDS.sleep(3);
    }

    @Test
    @Order(2)
    @DisplayName("remember: stores episodic memory for session context")
    void remember_episodic() throws Exception {
        var request = new RememberRequest(
                null,
                "User asked about memory table pagination in the Cortex UI. " +
                "Explained that the table uses server-side paging with page=0&pageSize=50.",
                "EPISODIC", "OBSERVED",
                "ui,cortex,pagination", // comma-separated tags
                null, null, null, null, null
        );

        var response = memoryService.remember(request);
        assertThat(response.id()).isNotBlank();
        episodicId = response.id();

        TimeUnit.SECONDS.sleep(3);
    }

    @Test
    @Order(3)
    @DisplayName("remember: stores working memory with cognitive hints")
    void remember_working() throws Exception {
        var request = new RememberRequest(
                null,
                "Currently debugging MemoryBridge — replaced manual @PostConstruct with Spring ObjectProvider injection.",
                "WORKING", "INFERRED",
                null, // tags
                0.9f, 0.7f, 0.8f, 100, 200
        );

        var response = memoryService.remember(request);
        assertThat(response.id()).isNotBlank();
        workingId = response.id();

        TimeUnit.SECONDS.sleep(3);
    }

    // ═══════════════════════════════════════════════════
    // 2. Recall with LLM judge
    // ═══════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("recall: returns semantically relevant results for Panama FFM query")
    void recall_panama_ffm_query() {
        var request = new RecallRequest("Panama foreign memory off-heap", 10, null);
        var results = memoryService.recall(request);

        assertThat(results).isNotEmpty();

        // Hard structural assertion
        assertThat(results).allSatisfy(r -> {
            assertThat(r.id()).isNotBlank();
            assertThat(r.text()).isNotBlank();
            assertThat(r.cognitiveScore()).isBetween(0.0, 1.0);
        });

        // LLM judge: soft semantic assertion
        var texts = results.stream().map(RecallResult::text).toList();
        LlmAssertions.assertTexts(judge, "Panama foreign memory off-heap", texts)
                .isRelevantTo("Results should mention off-heap memory, Panama, or synaptic storage")
                .hasGoodRanking();
    }

    @Test
    @Order(11)
    @DisplayName("recall: Cortex UI query retrieves pagination-related memory")
    void recall_cortex_ui_query() {
        var request = new RecallRequest("Cortex UI memory table pagination", 5, null);
        var results = memoryService.recall(request);

        assertThat(results).isNotEmpty();

        var texts = results.stream().map(RecallResult::text).toList();
        LlmAssertions.assertTexts(judge, "Cortex UI memory table pagination", texts)
                .isRelevantTo("Results should contain information about pagination or the memory table UI")
                .coversTopics("pagination", "memory table");
    }

    @Test
    @Order(12)
    @DisplayName("recall: topK limit is respected")
    void recall_topK_respected() {
        // Store 3 more memories to ensure we have enough
        for (int i = 0; i < 3; i++) {
            memoryService.store(new StoreRequest(
                    "Java " + i + " concurrency fact about virtual threads and structured concurrency",
                    List.of("java", "concurrency"), null, null
            ));
        }

        try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        var request = new RecallRequest("Java concurrency virtual threads", 2, null);
        var results = memoryService.recall(request);

        assertThat(results).hasSizeLessThanOrEqualTo(2);
    }

    // ═══════════════════════════════════════════════════
    // 3. Memory table
    // ═══════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("getMemoryTable: returns rows with all required fields")
    void getMemoryTable_allFields() {
        var table = memoryService.getMemoryTable(0, 50, null, false);

        assertThat(table.totalCount()).isGreaterThan(0);
        assertThat(table.rows()).isNotEmpty();
        assertThat(table.tierCounts()).containsKeys("WORKING", "EPISODIC", "SEMANTIC", "PROCEDURAL");

        table.rows().forEach(row -> {
            assertThat(row.id()).isNotBlank();
            assertThat(row.text()).isNotBlank();
            assertThat(row.tier()).isNotBlank();
            assertThat(row.source()).isNotBlank();
        });
    }

    @Test
    @Order(21)
    @DisplayName("getMemoryTable: tier filter returns only matching rows")
    void getMemoryTable_tierFilter() {
        var table = memoryService.getMemoryTable(0, 50, "SEMANTIC", false);

        table.rows().forEach(row ->
                assertThat(row.tier()).isEqualToIgnoringCase("SEMANTIC")
        );
    }

    @Test
    @Order(22)
    @DisplayName("getMemoryTable: pagination returns correct page slice")
    void getMemoryTable_pagination() {
        // Page 0 with size 1
        var page0 = memoryService.getMemoryTable(0, 1, null, false);
        // Page 1 with size 1
        var page1 = memoryService.getMemoryTable(1, 1, null, false);

        assertThat(page0.rows()).hasSize(1);
        if (page0.totalCount() > 1) {
            assertThat(page1.rows()).hasSize(1);
            // Pages should contain different memories
            assertThat(page0.rows().get(0).id())
                    .isNotEqualTo(page1.rows().get(0).id());
        }
    }

    // ═══════════════════════════════════════════════════
    // 4. Status
    // ═══════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("getStatus: returns positive counts after ingestion")
    void getStatus_afterIngestion() {
        var status = memoryService.getStatus();

        assertThat(status.totalMemories()).isGreaterThan(0);
        assertThat(status.tierCounts()).containsKeys("WORKING", "EPISODIC", "SEMANTIC", "PROCEDURAL");
        assertThat(status.tierCounts().values().stream().mapToInt(Integer::intValue).sum())
                .isGreaterThan(0);
    }

    // ═══════════════════════════════════════════════════
    // 5. Reinforce
    // ═══════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("reinforce: positive valence strengthens memory without error")
    void reinforce_positive() {
        assertThatCode(() -> memoryService.reinforce(semanticId, new ReinforceByIdRequest(50).effectiveValence()))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(41)
    @DisplayName("reinforce: negative valence (punishment) works without error")
    void reinforce_negative() {
        assertThatCode(() -> memoryService.reinforce(episodicId, new ReinforceByIdRequest(-30).effectiveValence()))
                .doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════════
    // 6. Suppress / Unsuppress
    // ═══════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("suppress: memory is suppressed without error")
    void suppress_succeeds() {
        assertThatCode(() -> memoryService.suppress(workingId, new SuppressRequest(null, "task completed")))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(51)
    @DisplayName("unsuppress: memory is re-enabled without error")
    void unsuppress_succeeds() {
        assertThatCode(() -> memoryService.suppress(workingId, new SuppressRequest("UNSUPPRESS", null)))
                .doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════════
    // 7. Resolve
    // ═══════════════════════════════════════════════════

    @Test
    @Order(60)
    @DisplayName("markResolved: marks episodic memory resolved without error")
    void resolve_succeeds() {
        assertThatCode(() -> memoryService.resolve(episodicId, new ResolveRequest(true)))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(61)
    @DisplayName("markUnresolved: reverts resolved state without error")
    void unresolve_succeeds() {
        assertThatCode(() -> memoryService.resolve(episodicId, new ResolveRequest(false)))
                .doesNotThrowAnyException();
    }

    // ═══════════════════════════════════════════════════
    // 8. Reflect
    // ═══════════════════════════════════════════════════

    @Test
    @Order(70)
    @DisplayName("reflect: runs consolidation cycle and returns report")
    void reflect_completesSuccessfully() {
        var report = memoryService.reflect();

        assertThat(report.durationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(report.message()).isNotBlank();
        // tombstonedCount may be 0 if nothing decayed yet — that's valid
        assertThat(report.tombstonedCount()).isGreaterThanOrEqualTo(0);
    }

    // ═══════════════════════════════════════════════════
    // 9. Bulk forget
    // ═══════════════════════════════════════════════════

    @Test
    @Order(80)
    @DisplayName("forget: tombstones a memory and reduces visible recall results")
    void forget_reducesResults() throws InterruptedException {
        // Store a very distinctive memory
        var storeResp = memoryService.store(new StoreRequest(
                "ZyxwvutsrqponmlkjihgfedcbaXYZ unique marker for forget test",
                List.of("forget-marker"), null, null
        ));
        String targetId = storeResp.id();
        TimeUnit.SECONDS.sleep(2);

        // Verify it can be recalled
        var beforeForget = memoryService.recall(
                new RecallRequest("Zyxwvutsrqponmlkjihgfedcba unique marker", 10, null));
        assertThat(beforeForget.stream().anyMatch(r -> r.id().equals(targetId))).isTrue();

        // Forget it
        memoryService.forget(targetId);
        TimeUnit.SECONDS.sleep(1);

        // Should no longer appear in non-tombstoned table
        var table = memoryService.getMemoryTable(0, 100, null, false);
        boolean stillVisible = table.rows().stream().anyMatch(r -> r.id().equals(targetId));
        assertThat(stillVisible).isFalse();
    }

    // ═══════════════════════════════════════════════════
    // 10. Vacuum
    // ═══════════════════════════════════════════════════

    @Test
    @Order(90)
    @DisplayName("vacuum: runs compaction on SEMANTIC tier without error")
    void vacuum_semantic() {
        var result = memoryService.vacuum(new VacuumRequest("SEMANTIC"));

        assertThat(result.tier()).isEqualToIgnoringCase("SEMANTIC");
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
    }
}
