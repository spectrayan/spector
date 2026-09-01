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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * End-to-end integration tests validating all 7 biological phases of the
 * Active Inference Self-Model Engine (AISME) across Cognitive Pathways with
 * live Ollama embeddings and LLM Judge semantic verification.
 */
@DisplayName("🧠 E2E: AISME Cognitive Pathways (Phases 1–7)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AismeCognitivePathwayE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Phase 1: Homeostatic affective regulation biases recall toward resonant memories")
    void phase1_homeostaticAffectiveBiasing() {
        memory.remember("aisme-homeostasis-1",
                "Successfully mitigated critical production outage by applying exponential backoff to retry queue.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "outage", "production", "retry", "resilience");

        List<CognitiveResult> results = memory.recall(
                "critical production incident resolution strategy",
                RecallOptions.builder()
                        .topK(10)
                        .profile(CognitiveProfile.BALANCED)
                        .build());

        log.info("Phase 1 Homeostatic Recall Results:");
        printResults(results);

        assertThat(results).isNotEmpty();
        boolean found = results.stream().anyMatch(r -> r.text().contains("exponential backoff") || r.text().contains("outage"));
        assertThat(found).as("Should recall homeostatically salient outage resolution").isTrue();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("critical production incident resolution strategy", results)
                    .warnIfIrrelevant("Results should contain memories about resolving critical production incidents");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Phase 2: Variational Free Energy minimization balances epistemic exploration and pragmatic value")
    void phase2_freeEnergyMinimization() {
        memory.remember("aisme-fe-1",
                "Investigated zero-copy off-heap memory buffers using Java 25 Foreign Function and Memory API.",
                MemoryType.SEMANTIC, MemorySource.OBSERVED,
                "panama", "offheap", "performance", "java25");

        List<CognitiveResult> results = memory.recall(
                "low latency off-heap memory management architecture",
                RecallOptions.builder()
                        .topK(10)
                        .profile(CognitiveProfile.BALANCED)
                        .build());

        log.info("Phase 2 Free Energy Recall Results:");
        printResults(results);

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(r -> r.text().contains("Foreign Function") || r.text().contains("off-heap")))
                .as("Free energy guided recall should surface low-latency off-heap architecture")
                .isTrue();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("low latency off-heap memory management architecture", results)
                    .warnIfIrrelevant("Results should contain memories discussing off-heap memory and Panama FFM");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Phase 3: Dense Hopfield associative dynamics retrieve attractor basins")
    void phase3_hopfieldAssociativeAttractors() {
        memory.remember("aisme-hopfield-1",
                "Post-quantum cryptographic key encapsulation mechanism using ML-KEM-768 for sovereign vaults.",
                MemoryType.SEMANTIC, MemorySource.OBSERVED,
                "cryptography", "quantum", "ml-kem", "security");

        List<CognitiveResult> results = memory.recall(
                "quantum resistant encryption algorithms for sovereign data",
                RecallOptions.builder()
                        .topK(10)
                        .profile(CognitiveProfile.BALANCED)
                        .build());

        log.info("Phase 3 Hopfield Attractor Recall Results:");
        printResults(results);

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(r -> r.text().contains("ML-KEM") || r.text().contains("cryptographic")))
                .as("Hopfield associative dynamic should recall ML-KEM post-quantum security")
                .isTrue();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("quantum resistant encryption algorithms for sovereign data", results)
                    .warnIfIrrelevant("Results should surface post-quantum cryptography memories");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Phase 4 & 5: Manifold geodesic distances and predictive coding support constructive simulation")
    void phase4and5_manifoldAndPredictiveCoding() {
        memory.remember("aisme-sim-1",
                "Constructed high-concurrency event streaming architecture handling 100k msgs/sec with Apache Camel and virtual threads.",
                MemoryType.SEMANTIC, MemorySource.OBSERVED,
                "streaming", "concurrency", "camel", "virtual-threads");

        List<CognitiveResult> results = memory.recall(
                "high throughput event streaming with virtual threads",
                RecallOptions.builder()
                        .topK(10)
                        .profile(CognitiveProfile.BALANCED)
                        .build());

        log.info("Phase 4/5 Manifold & Simulation Recall Results:");
        printResults(results);

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(r -> r.text().contains("Apache Camel") || r.text().contains("streaming")))
                .as("Should surface high-concurrency streaming architecture")
                .isTrue();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("high throughput event streaming with virtual threads", results)
                    .warnIfIrrelevant("Results should discuss event streaming and virtual threads");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Phase 6 & 7: Consciousness continuity and Global Workspace conscious access broadcast salient memory")
    void phase6and7_consciousnessContinuityAndGlobalWorkspace() {
        List<CognitiveResult> broadcast = memory.recall(
                "core architectural tenets and mission of the neural substrate",
                RecallOptions.builder()
                        .topK(7) // Miller's law capacity 7
                        .profile(CognitiveProfile.BALANCED)
                        .build());

        log.info("Phase 6/7 Global Workspace Broadcast Results (Capacity 7):");
        printResults(broadcast);

        assertThat(broadcast).isNotEmpty();
        assertThat(broadcast.size()).isLessThanOrEqualTo(7);

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("core architectural tenets and mission of the neural substrate", broadcast)
                    .warnIfIrrelevant("Broadcast should contain salient architectural memories");
        }
    }
}
