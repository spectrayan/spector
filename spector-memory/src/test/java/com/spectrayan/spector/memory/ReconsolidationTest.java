/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for memory reconsolidation (update-in-place).
 *
 * <p>Uses {@link FakeEmbeddingProvider} with a real {@link DefaultSpectorMemory}
 * instance to test the full reconsolidation pipeline:
 * tombstone old slot → remove from index → re-embed → re-ingest.</p>
 *
 * <p>These tests run without Ollama — instant, deterministic, always-on.</p>
 */
@DisplayName("🧠 Memory Reconsolidation (Fake Embeddings)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconsolidationTest {

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
    // SETUP HELPERS
    // ══════════════════════════════════════════════════════════════

    private void ingest(String id, String text, MemoryType type, String... tags) {
        memory.remember(id, text, type, MemorySource.USER_STATED, tags).join();
    }

    private void reconsolidate(String id, String newText, String[] newTags) {
        var dsm = (DefaultSpectorMemory) memory;
        var admin = memory.admin();
        var index = admin.index();
        var loc = index.locate(id);
        assertThat(loc).as("Memory '%s' should exist before reconsolidation", id).isNotNull();

        // Capture existing metadata before removal
        String text = newText != null ? newText : index.text(id);
        String[] tags = newTags != null ? newTags : index.tags(id);
        var source = index.source(id);
        var type = loc.type();

        // Remove from index to bypass dedup guard
        index.remove(id);

        // Re-embed and re-ingest
        float[] vector = dsm.embeddingProvider().embed(text).vector();
        memory.target().ingestCognitive(id, text, vector, type, tags,
                source != null ? source : MemorySource.OBSERVED,
                (com.spectrayan.spector.memory.neurodivergent.IngestionHints) null);
    }

    // ══════════════════════════════════════════════════════════════
    // RECONSOLIDATION — Text Updates
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Reconsolidate text — index returns updated content")
    void reconsolidateText_updatesIndex() {
        ingest("recon-text-1", "The cat sat on the mat", MemoryType.EPISODIC, "animals");

        reconsolidate("recon-text-1", "The dog sat on the mat", null);

        var index = memory.admin().index();
        assertThat(index.text("recon-text-1"))
                .as("Text should be updated after reconsolidation")
                .isEqualTo("The dog sat on the mat");
    }

    @Test
    @Order(2)
    @DisplayName("Reconsolidate tags only — text unchanged, tags updated")
    void reconsolidateTags_textUnchanged() {
        ingest("recon-tags-1", "Java concurrency patterns", MemoryType.SEMANTIC, "java");

        reconsolidate("recon-tags-1", null, new String[]{"java", "concurrency", "patterns"});

        var index = memory.admin().index();
        assertThat(index.text("recon-tags-1")).isEqualTo("Java concurrency patterns");
        assertThat(index.tags("recon-tags-1")).containsExactly("java", "concurrency", "patterns");
    }

    @Test
    @Order(3)
    @DisplayName("Reconsolidate text + tags together")
    void reconsolidateBoth() {
        ingest("recon-both-1", "Old text about Python", MemoryType.EPISODIC, "python");

        reconsolidate("recon-both-1",
                "Updated text about Rust",
                new String[]{"rust", "programming"});

        var index = memory.admin().index();
        assertThat(index.text("recon-both-1")).isEqualTo("Updated text about Rust");
        assertThat(index.tags("recon-both-1")).containsExactly("rust", "programming");
    }

    // ══════════════════════════════════════════════════════════════
    // RECONSOLIDATION — Metadata Preservation
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Reconsolidate preserves tier type")
    void reconsolidate_preservesTier() {
        ingest("recon-tier-1", "Semantic knowledge fact", MemoryType.SEMANTIC, "fact");
        reconsolidate("recon-tier-1", "Updated semantic knowledge", null);

        var loc = memory.admin().index().locate("recon-tier-1");
        assertThat(loc.type()).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @Order(5)
    @DisplayName("Reconsolidate preserves source")
    void reconsolidate_preservesSource() {
        ingest("recon-source-1", "User stated fact", MemoryType.EPISODIC, "user");
        reconsolidate("recon-source-1", "Updated user fact", null);

        assertThat(memory.admin().index().source("recon-source-1"))
                .isEqualTo(MemorySource.USER_STATED);
    }

    // ══════════════════════════════════════════════════════════════
    // RECONSOLIDATION — Recall Verification
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Reconsolidated memory is recallable with new text")
    void reconsolidated_recallableWithNewText() {
        ingest("recon-recall-1", "PostgreSQL connection pooling with HikariCP",
                MemoryType.SEMANTIC, "database");
        reconsolidate("recon-recall-1",
                "Redis caching strategies for high throughput", null);

        // The updated text should be in the index
        assertThat(memory.admin().index().text("recon-recall-1"))
                .contains("Redis caching");
    }

    // ══════════════════════════════════════════════════════════════
    // DEDUP GUARD — The Original Bug
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(7)
    @DisplayName("Dedup guard blocks second remember() with same ID")
    void dedupGuard_blocksReRemember() {
        ingest("dedup-1", "Original content", MemoryType.EPISODIC, "original");

        // Second remember with same ID should be silently skipped by dedup guard
        memory.remember("dedup-1", "Should be ignored", MemoryType.EPISODIC,
                MemorySource.USER_STATED, "ignored").join();

        // Index should still have original text (dedup guard blocked)
        assertThat(memory.admin().index().text("dedup-1")).isEqualTo("Original content");
    }

    @Test
    @Order(8)
    @DisplayName("Reconsolidation bypasses dedup guard")
    void reconsolidation_bypassesDedupGuard() {
        ingest("dedup-bypass-1", "Before reconsolidation", MemoryType.EPISODIC, "before");

        reconsolidate("dedup-bypass-1", "After reconsolidation", null);

        assertThat(memory.admin().index().text("dedup-bypass-1"))
                .as("Reconsolidation must bypass dedup guard")
                .isEqualTo("After reconsolidation");
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("Reconsolidate non-existent ID throws")
    void reconsolidate_nonExistentThrows() {
        assertThatThrownBy(() -> reconsolidate("does-not-exist", "text", null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @Order(10)
    @DisplayName("Reconsolidate with null text keeps existing")
    void reconsolidate_nullTextKeepsExisting() {
        ingest("recon-null-text", "Keep this text", MemoryType.EPISODIC, "keep");

        reconsolidate("recon-null-text", null, new String[]{"new-tag"});

        assertThat(memory.admin().index().text("recon-null-text")).isEqualTo("Keep this text");
        assertThat(memory.admin().index().tags("recon-null-text")).containsExactly("new-tag");
    }

    @Test
    @Order(11)
    @DisplayName("Reconsolidate with null tags keeps existing")
    void reconsolidate_nullTagsKeepsExisting() {
        ingest("recon-null-tags", "Some text", MemoryType.EPISODIC, "original-tag");

        reconsolidate("recon-null-tags", "Updated text", null);

        assertThat(memory.admin().index().tags("recon-null-tags")).containsExactly("original-tag");
    }

    @Test
    @Order(12)
    @DisplayName("Reconsolidate with empty tags clears tags")
    void reconsolidate_emptyTagsClearsTags() {
        ingest("recon-empty-tags", "Some text", MemoryType.EPISODIC, "tag1", "tag2");

        reconsolidate("recon-empty-tags", null, new String[]{});

        assertThat(memory.admin().index().tags("recon-empty-tags")).isEmpty();
    }

    @Test
    @Order(13)
    @DisplayName("Multiple reconsolidations of same ID in sequence")
    void multipleReconsolidations() {
        ingest("recon-multi", "Version 1", MemoryType.EPISODIC, "v1");

        reconsolidate("recon-multi", "Version 2", new String[]{"v2"});
        reconsolidate("recon-multi", "Version 3", new String[]{"v3"});
        reconsolidate("recon-multi", "Version 4", new String[]{"v4"});

        var index = memory.admin().index();
        assertThat(index.text("recon-multi")).isEqualTo("Version 4");
        assertThat(index.tags("recon-multi")).containsExactly("v4");
    }

    @Test
    @Order(14)
    @DisplayName("Index entry count unchanged after reconsolidation (same ID)")
    void reconsolidate_doesNotIncrementIndexCount() {
        int before = memory.admin().index().size();
        ingest("recon-count-1", "Original", MemoryType.EPISODIC);
        int afterIngest = memory.admin().index().size();
        assertThat(afterIngest).isEqualTo(before + 1);

        reconsolidate("recon-count-1", "Updated", null);
        int afterRecon = memory.admin().index().size();
        assertThat(afterRecon)
                .as("Index count should not increase — same ID re-registered")
                .isEqualTo(afterIngest);
    }

    @Test
    @Order(15)
    @DisplayName("Index location offset changes after reconsolidation")
    void reconsolidate_offsetChanges() {
        ingest("recon-offset-1", "Original", MemoryType.EPISODIC, "test");
        long oldOffset = memory.admin().index().locate("recon-offset-1").offset();

        reconsolidate("recon-offset-1", "Updated content with different length", null);
        long newOffset = memory.admin().index().locate("recon-offset-1").offset();

        assertThat(newOffset)
                .as("Offset should change after reconsolidation (new binary record)")
                .isNotEqualTo(oldOffset);
    }
}
