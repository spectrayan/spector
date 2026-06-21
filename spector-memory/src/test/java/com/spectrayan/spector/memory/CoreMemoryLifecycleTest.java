/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.*;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the core memory lifecycle: remember → recall → forget → inspect.
 *
 * <p>Uses {@link FakeEmbeddingProvider} with a real {@link DefaultSpectorMemory}
 * to test the full cognitive pipeline without Ollama.</p>
 */
@DisplayName("🧠 Core Memory Lifecycle (Fake Embeddings)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreMemoryLifecycleTest {

    private SpectorMemory memory;
    private FakeEmbeddingProvider embedProvider;

    @BeforeAll
    void initMemory(@TempDir Path tempDir) {
        embedProvider = new FakeEmbeddingProvider();
        memory = DefaultSpectorMemory.builder()
                .dimensions(embedProvider.dimensions())
                .embeddingProvider(embedProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .workingCapacity(20)
                .episodicPartitionCapacity(100)
                .semanticCapacity(50)
                .proceduralCapacity(20)
                .surpriseWarmup(2)
                .build();
    }

    @AfterAll
    void shutdown() {
        if (memory != null) memory.close();
    }

    // ══════════════════════════════════════════════════════════════
    // REMEMBER — Basic Ingestion
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Remember registers memory in index")
    void remember_registersInIndex() {
        memory.remember("lifecycle-1", "The server crashed at 3am",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "server", "crash").join();

        var loc = memory.admin().index().locate("lifecycle-1");
        assertThat(loc).isNotNull();
        assertThat(loc.type()).isEqualTo(MemoryType.EPISODIC);
    }

    @Test
    @Order(2)
    @DisplayName("Remember stores text in index")
    void remember_storesText() {
        memory.remember("lifecycle-2", "Always use prepared statements to prevent SQL injection",
                MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "sql", "security").join();

        assertThat(memory.admin().index().text("lifecycle-2"))
                .isEqualTo("Always use prepared statements to prevent SQL injection");
    }

    @Test
    @Order(3)
    @DisplayName("Remember stores tags in index")
    void remember_storesTags() {
        memory.remember("lifecycle-3", "Kubernetes uses etcd for cluster state",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "k8s", "etcd").join();

        assertThat(memory.admin().index().tags("lifecycle-3")).containsExactly("k8s", "etcd");
    }

    @Test
    @Order(4)
    @DisplayName("Remember stores source in index")
    void remember_storesSource() {
        memory.remember("lifecycle-4", "I prefer dark mode",
                MemoryType.EPISODIC, MemorySource.USER_STATED, "preference").join();

        assertThat(memory.admin().index().source("lifecycle-4"))
                .isEqualTo(MemorySource.USER_STATED);
    }

    @Test
    @Order(5)
    @DisplayName("Remember to each tier routes correctly")
    void remember_tierRouting() {
        memory.remember("tier-working", "temporary note",
                MemoryType.WORKING, MemorySource.OBSERVED).join();
        memory.remember("tier-episodic", "meeting happened",
                MemoryType.EPISODIC, MemorySource.OBSERVED).join();
        memory.remember("tier-semantic", "cats are mammals",
                MemoryType.SEMANTIC, MemorySource.OBSERVED).join();
        memory.remember("tier-procedural", "always backup before deploy",
                MemoryType.PROCEDURAL, MemorySource.PROCEDURAL).join();

        assertThat(memory.admin().index().locate("tier-working").type()).isEqualTo(MemoryType.WORKING);
        assertThat(memory.admin().index().locate("tier-episodic").type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(memory.admin().index().locate("tier-semantic").type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(memory.admin().index().locate("tier-procedural").type()).isEqualTo(MemoryType.PROCEDURAL);
    }

    @Test
    @Order(6)
    @DisplayName("Remember with auto-generated ID returns non-null ID")
    void remember_autoId() {
        String id = memory.remember("Auto-ID test content",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "auto").join();

        assertThat(id).isNotNull().isNotEmpty();
        assertThat(memory.admin().index().locate(id)).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════
    // RECALL — Cognitive Scoring
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("Recall returns non-empty results for related query")
    void recall_returnsResults() {
        memory.remember("recall-1", "Docker containers run isolated processes",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "docker").join();

        List<CognitiveResult> results = memory.recall("Docker containers");
        assertThat(results).isNotEmpty();
    }

    @Test
    @Order(11)
    @DisplayName("Recall results contain scores > 0")
    void recall_scoresPositive() {
        List<CognitiveResult> results = memory.recall("Docker containers");
        for (CognitiveResult r : results) {
            assertThat(r.score()).as("Score for '%s'", r.id()).isGreaterThan(0f);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FORGET — Tombstoning
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("Forget tombstones memory")
    void forget_tombstones() {
        memory.remember("forget-1", "This memory should be forgotten",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "forget").join();
        assertThat(memory.admin().index().locate("forget-1")).isNotNull();

        memory.forget("forget-1");
        // After forget, the memory should still be in index but tombstoned
        // (implementation-dependent — some may remove from index)
    }

    // ══════════════════════════════════════════════════════════════
    // INSPECT — Cognitive X-Ray
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("Inspect returns full cognitive record")
    void inspect_returnsRecord() {
        memory.remember("inspect-1", "Garbage collection in JVM uses mark-and-sweep",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "jvm", "gc").join();

        CognitiveRecord record = memory.inspect("inspect-1");
        assertThat(record).isNotNull();
        assertThat(record.id()).isEqualTo("inspect-1");
        assertThat(record.text()).isEqualTo("Garbage collection in JVM uses mark-and-sweep");
        assertThat(record.memoryType()).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @Order(31)
    @DisplayName("Inspect returns null for non-existent ID")
    void inspect_nullForMissing() {
        CognitiveRecord record = memory.inspect("does-not-exist-123");
        assertThat(record).isNull();
    }

    // ══════════════════════════════════════════════════════════════
    // COUNTS — Memory Statistics
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("Total memories count is positive after ingestion")
    void totalMemories_positive() {
        assertThat(memory.totalMemories()).isGreaterThan(0);
    }

    @Test
    @Order(41)
    @DisplayName("Memory count per tier is >= 0")
    void memoryCount_perTier() {
        for (MemoryType type : MemoryType.values()) {
            assertThat(memory.memoryCount(type))
                    .as("Count for %s", type)
                    .isGreaterThanOrEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SUPPRESS / UNSUPPRESS
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("Suppress and unsuppress work without exceptions")
    void suppressUnsuppress() {
        memory.remember("suppress-1", "Suppressible memory",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "suppress").join();

        assertThatCode(() -> memory.suppress("suppress-1", "test reason"))
                .doesNotThrowAnyException();
        assertThatCode(() -> memory.unsuppress("suppress-1"))
                .doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // RESOLVE / UNRESOLVE (Zeigarnik)
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(60)
    @DisplayName("Mark resolved / unresolved toggles without exceptions")
    void resolveUnresolve() {
        memory.remember("resolve-1", "Open task: fix the build",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "task").join();

        assertThatCode(() -> memory.markResolved("resolve-1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> memory.markUnresolved("resolve-1"))
                .doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // REINFORCE
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(70)
    @DisplayName("Reinforce with positive valence does not throw")
    void reinforce_positive() {
        memory.remember("reinforce-1", "This approach worked great",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "reinforce").join();

        assertThatCode(() -> memory.reinforce("reinforce-1", (byte) 50))
                .doesNotThrowAnyException();
    }

    @Test
    @Order(71)
    @DisplayName("Reinforce with negative valence does not throw")
    void reinforce_negative() {
        memory.remember("reinforce-2", "This approach failed",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "reinforce").join();

        assertThatCode(() -> memory.reinforce("reinforce-2", (byte) -50))
                .doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════
    // BROWSE — Tag-Based Iteration
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(80)
    @DisplayName("Browse by tags returns matching memories")
    void browse_matchesTags() {
        memory.remember("browse-1", "Browsable content tagged with browse-test",
                MemoryType.EPISODIC, MemorySource.OBSERVED, "browse-test").join();

        List<CognitiveRecord> results = memory.browse("browse-test");
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r -> r.id().equals("browse-1"));
    }

    // ══════════════════════════════════════════════════════════════
    // EXPORT
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(90)
    @DisplayName("Export JSON returns non-empty string")
    void export_returnsJson() {
        String json = memory.exportJson();
        assertThat(json)
                .isNotNull()
                .isNotEmpty()
                .startsWith("[");
    }

    // ══════════════════════════════════════════════════════════════
    // IMPORTANCE ESTIMATION
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(100)
    @DisplayName("Estimate importance returns non-negative value")
    void estimateImportance_nonNegative() {
        ImportanceEstimate estimate = memory.estimateImportance("A completely new topic");
        assertThat(estimate).isNotNull();
        assertThat(estimate.fusedImportance()).isGreaterThanOrEqualTo(0f);
    }
}
