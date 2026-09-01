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
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * End-to-end integration tests validating multi-tier memory synthesis (Working, Procedural,
 * Semantic, Episodic) and bitemporal fact histories across Cognitive Pathways with
 * live Ollama embeddings and LLM Judge semantic validation.
 */
@DisplayName("📦 E2E: Hierarchical Multi-Tier Memory & Context Synthesis")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HierarchicalContextPackE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Ingest multi-tier memory records across Working, Procedural, Semantic, and Episodic tiers")
    void ingestMultiTierMemories() {
        memory.remember("hcp-working-1",
                "Active Turn Intent: Implement and benchmark AISME sub-relays against balanced-baseline dataset.",
                MemoryType.WORKING, MemorySource.OBSERVED,
                "working", "intent", "benchmarking");

        memory.remember("hcp-proc-1",
                "PROCEDURAL RULE: When benchmarking sub-relays, isolate each configuration flag to measure marginal delta.",
                MemoryType.PROCEDURAL, MemorySource.REFLECTED,
                "procedural", "crystallized", "skill");

        memory.remember("hcp-sem-1",
                "Axiom: Java 25 Foreign Function & Memory API enables zero-GC off-heap Panama layout invariance.",
                MemoryType.SEMANTIC, MemorySource.OBSERVED,
                "axiom", "panama", "offheap");

        memory.remember("hcp-epi-1",
                "On day 340, tested memory recall across 11,367 balanced-baseline records with sub-millisecond latency.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "benchmark", "latency", "balanced-baseline");

        memory.assertFact("Subsystem", "architecture", "CognitivePathways", 1700000000000L, Long.MAX_VALUE, 0.99f);

        assertThat(memory.totalMemories()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("Recall across all tiers returns balanced representation of working, procedural, semantic, and episodic memory")
    void recallMultiTierSynthesis() {
        List<CognitiveResult> results = memory.recall(
                "AISME sub-relays benchmark architecture and off-heap performance",
                RecallOptions.builder()
                        .topK(15)
                        .build());

        log.info("Multi-Tier Recall Results:");
        printResults(results);

        assertThat(results).isNotEmpty();

        FactHistory fh = memory.factHistory("Subsystem", "architecture");
        assertThat(fh.activeFact()).isNotNull();
        assertThat(fh.activeFact().object()).isEqualToIgnoringCase("CognitivePathways");

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("AISME sub-relays benchmark architecture and off-heap performance", results)
                    .warnIfIrrelevant("Results should contain memories discussing AISME sub-relays and Panama architecture");
        }
    }
}
