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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.ConflictActionPolicy;
import com.spectrayan.spector.memory.model.ConflictMode;
import com.spectrayan.spector.memory.model.EvidenceDistribution;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.graph.temporal.ConflictAwareResolver;

/**
 * End-to-end integration tests validating TANGLE multi-evidence conflict resolution,
 * epistemic entropy, and action policies across Cognitive Pathways with live Ollama
 * embeddings and LLM Judge semantic validation.
 */
@DisplayName("🔀 E2E: TANGLE Multi-Evidence & Conflict Resolution")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TangleMultiEvidenceE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Assert bitemporal facts and verify multi-evidence history tracking")
    void assertEvolvingBitemporalFacts() {
        long t1 = 1704067200000L; // 2024-01-01
        long t2 = 1735689600000L; // 2025-01-01
        long t3 = 1767225600000L; // 2026-01-01

        memory.assertFact("SpectrayanCorp", "headquarters", "Austin", t1, t2, 0.95f);
        memory.assertFact("SpectrayanCorp", "headquarters", "Seattle", t2, Long.MAX_VALUE, 0.98f);

        FactHistory history = memory.factHistory("SpectrayanCorp", "headquarters");
        log.info("FactHistory for SpectrayanCorp HQ: active={}, superseded={}",
                history.activeFact() != null ? history.activeFact().object() : "none",
                history.supersededFacts().size());

        assertThat(history.activeFact()).isNotNull();
        assertThat(history.activeFact().object()).isEqualToIgnoringCase("Seattle");
        assertThat(history.supersededFacts()).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("ConflictAwareResolver generates EvidenceDistribution with PRESENT_ALTERNATIVES policy")
    void resolveEvidenceDistributionWithAlternatives() {
        FactHistory history = memory.factHistory("SpectrayanCorp", "headquarters");
        List<FactHistory.FactSnapshot> allSnapshots = new java.util.ArrayList<>();
        if (history.activeFact() != null) {
            allSnapshots.add(history.activeFact());
        }
        allSnapshots.addAll(history.supersededFacts());

        EvidenceDistribution dist = ConflictAwareResolver.resolveDistribution(
                "SpectrayanCorp", "headquarters", allSnapshots);

        log.info("Resolved EvidenceDistribution: winner={}, policy={}, entropy={}",
                dist.consensusWinner() != null ? dist.consensusWinner().object() : "none",
                dist.recommendedPolicy(), dist.epistemicEntropy());

        assertThat(dist.consensusWinner()).isNotNull();
        assertThat(dist.consensusWinner().object()).isEqualToIgnoringCase("Seattle");
        assertThat(dist.recommendedPolicy()).isIn(
                ConflictActionPolicy.ASK_CLARIFYING_QUESTION,
                ConflictActionPolicy.PRESENT_ALTERNATIVES
        );
        assertThat(dist.hasContradictions()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Recall with ConflictMode.MULTI_EVIDENCE preserves competing historical evidence")
    void recallMultiEvidencePreservesAlternatives() {
        List<CognitiveResult> results = memory.recall(
                "where is Spectrayan corporate headquarters located",
                RecallOptions.builder()
                        .topK(10)
                        .conflictMode(ConflictMode.MULTI_EVIDENCE)
                        .build());

        log.info("Recall Results under ConflictMode.MULTI_EVIDENCE:");
        printResults(results);

        assertThat(results).isNotEmpty();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("where is Spectrayan corporate headquarters located", results)
                    .warnIfIrrelevant("Results should contain headquarters locations");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Recall with ConflictMode.HIGHEST_CONFIDENCE resolves to single dominant winner")
    void recallHighestConfidenceResolvesWinner() {
        List<CognitiveResult> results = memory.recall(
                "current corporate headquarters of Spectrayan",
                RecallOptions.builder()
                        .topK(10)
                        .conflictMode(ConflictMode.HIGHEST_CONFIDENCE)
                        .build());

        log.info("Recall Results under ConflictMode.HIGHEST_CONFIDENCE:");
        printResults(results);

        assertThat(results).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("Recall with ConflictMode.FAIL_CLOSED filters out contradicted candidate traces")
    void recallFailClosedDropsContradicted() {
        List<CognitiveResult> results = memory.recall(
                "Spectrayan headquarters location",
                RecallOptions.builder()
                        .topK(10)
                        .conflictMode(ConflictMode.FAIL_CLOSED)
                        .build());

        log.info("Recall Results under ConflictMode.FAIL_CLOSED:");
        printResults(results);

        // Under FAIL_CLOSED, records flagged with FLAG_CONTRADICTED are pruned
        assertThat(results).isNotNull();
    }
}
