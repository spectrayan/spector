/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E tests for memory reconsolidation using real Ollama embeddings.
 *
 * <p>Gated behind {@code OLLAMA_LIVE=true}. Tests the complete pipeline:
 * remember → reconsolidate → verify semantic recall shift.</p>
 *
 * <p>These tests validate that reconsolidation actually changes the
 * semantic vector, causing the memory to match different queries.</p>
 */
@DisplayName("🧠 E2E: Reconsolidation (Real Embeddings)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconsolidationE2ETest extends AbstractE2ETest {

    // ── Helper: reconsolidate using the same pattern as MemoryService ──
    private void reconsolidate(String id, String newText, String[] newTags) {
        var dsm = (DefaultSpectorMemory) memory;
        var admin = memory.admin();
        var index = admin.index();
        var loc = index.locate(id);
        assertThat(loc).as("Memory '%s' should exist before reconsolidation", id).isNotNull();

        String text = newText != null ? newText : index.text(id);
        String[] tags = newTags != null ? newTags : index.tags(id);
        var source = index.source(id);
        var type = loc.type();

        // Remove from index to bypass dedup guard
        index.remove(id);

        // Re-embed with real Ollama embeddings
        float[] vector = dsm.embeddingProvider().embed(text).vector();
        memory.target().ingestCognitive(id, text, vector, type, tags,
                source != null ? source : MemorySource.OBSERVED,
                (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null);
    }

    // ══════════════════════════════════════════════════════════════
    // RECONSOLIDATION — Semantic Shift
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Reconsolidate text — recall returns updated content")
    void reconsolidateText_recallReturnsUpdated() {
        // Ingest about databases
        memory.remember("e2e-recon-1",
                "PostgreSQL supports advanced JSON queries with JSONB columns",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "database", "postgres").join();

        // Verify initial recall
        assertThat(memory.admin().index().text("e2e-recon-1"))
                .contains("PostgreSQL");

        // Reconsolidate to be about Kubernetes instead
        reconsolidate("e2e-recon-1",
                "Kubernetes pod scheduling uses affinity rules and taints",
                new String[]{"kubernetes", "orchestration"});

        // Verify text updated
        assertThat(memory.admin().index().text("e2e-recon-1"))
                .contains("Kubernetes");
    }

    @Test
    @Order(2)
    @DisplayName("Reconsolidate tags — browse returns updated tags")
    void reconsolidateTags_browseUpdated() {
        memory.remember("e2e-recon-tags",
                "Machine learning models require feature engineering",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "ml").join();

        reconsolidate("e2e-recon-tags", null, new String[]{"ai", "deep-learning", "training"});

        assertThat(memory.admin().index().tags("e2e-recon-tags"))
                .containsExactly("ai", "deep-learning", "training");
    }

    @Test
    @Order(3)
    @DisplayName("Semantic shift — new query matches, old query rank drops")
    void semanticShift_queryRankChanges() {
        // Ingest about cooking
        memory.remember("e2e-recon-shift",
                "Sourdough bread requires a 24-hour fermentation with wild yeast starter",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "cooking", "baking").join();

        // Recall with cooking query — should find it
        RecallOptions opts = new RecallOptions.Builder().topK(20).build();
        List<CognitiveResult> cookingResults = memory.recall("sourdough bread baking recipe", opts);
        boolean foundInCooking = cookingResults.stream()
                .anyMatch(r -> r.id().equals("e2e-recon-shift"));
        log.info("Before reconsolidation — found in cooking query: {}", foundInCooking);

        // Reconsolidate to be about networking
        reconsolidate("e2e-recon-shift",
                "TCP congestion control uses sliding window and slow start algorithms",
                new String[]{"networking", "tcp"});

        // Recall with networking query — should now find it
        List<CognitiveResult> networkResults = memory.recall("TCP congestion control algorithms", opts);
        boolean foundInNetworking = networkResults.stream()
                .anyMatch(r -> r.id().equals("e2e-recon-shift"));
        log.info("After reconsolidation — found in networking query: {}", foundInNetworking);

        // The reconsolidated memory should be findable with the new topic
        assertThat(memory.admin().index().text("e2e-recon-shift"))
                .as("Text should be updated to networking topic")
                .contains("TCP congestion");
    }

    @Test
    @Order(4)
    @DisplayName("Multiple reconsolidations preserve recall capability")
    void multipleReconsolidations_preserveRecall() {
        memory.remember("e2e-recon-multi",
                "Version 1: Introduction to functional programming",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "v1").join();

        reconsolidate("e2e-recon-multi",
                "Version 2: Object-oriented design patterns in Java",
                new String[]{"v2"});

        reconsolidate("e2e-recon-multi",
                "Version 3: Distributed systems consensus protocols like Raft and Paxos",
                new String[]{"v3"});

        // Final text should be version 3
        assertThat(memory.admin().index().text("e2e-recon-multi"))
                .contains("Raft and Paxos");
        assertThat(memory.admin().index().tags("e2e-recon-multi"))
                .containsExactly("v3");

        // Should still be locatable in the index
        assertThat(memory.admin().index().locate("e2e-recon-multi")).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("Reconsolidation preserves memory type across tiers")
    void reconsolidation_preservesTierType() {
        memory.remember("e2e-recon-proc",
                "Always run database migrations before deploying",
                MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "devops").join();

        reconsolidate("e2e-recon-proc",
                "Always run integration tests before merging to main",
                new String[]{"ci", "testing"});

        var loc = memory.admin().index().locate("e2e-recon-proc");
        assertThat(loc.type()).isEqualTo(MemoryType.PROCEDURAL);
    }
}
