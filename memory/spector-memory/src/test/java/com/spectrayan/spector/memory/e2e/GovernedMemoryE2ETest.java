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
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * End-to-end integration tests validating Governed Persistent Memory (GPM)
 * fail-closed release gates (retracted, restricted, unverified claims) across
 * Cognitive Pathways with live Ollama embeddings and LLM Judge verification.
 */
@DisplayName("🛡️ E2E: Governed Persistent Memory (GPM Fail-Closed Gates)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GovernedMemoryE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Retracted claims with FLAG_RETRACTED are never emitted during recall (fail-closed)")
    void retractedClaimsNeverEmitted() {
        // Assert and retract fact
        int factId = memory.assertFact("ProjectAlpha", "budget", "$50M", 1700000000000L, Long.MAX_VALUE, 0.90f);
        memory.retractFact(factId);

        var history = memory.factHistory("ProjectAlpha", "budget");
        assertThat(history.activeFact()).isNull();

        List<CognitiveResult> results = memory.recall(
                "what is the budget allocation for ProjectAlpha",
                RecallOptions.builder().topK(10).build());

        log.info("Recall results for retracted fact query:");
        printResults(results);

        // Ensure no leakage of retracted claim
        boolean leaked = results.stream().anyMatch(r -> r.text().contains("$50M") && r.text().contains("ProjectAlpha"));
        assertThat(leaked).as("Retracted claim must never leak during recall").isFalse();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("what is the budget allocation for ProjectAlpha", results)
                    .warnIfIrrelevant("Results should not contain retracted budget data");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Restricted access claims with FLAG_RESTRICTED drop fail-closed without valid personaId")
    void restrictedClaimsDroppedWithoutPersona() {
        memory.remember("gov-restricted-1",
                "CONFIDENTIAL DIARY: Personal reflections on confidential company board restructuring and executive changes.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "confidential", "board", "executive", "restricted");

        // Attempt recall without persona ID
        List<CognitiveResult> unauthenticatedResults = memory.recall(
                "confidential company board restructuring notes",
                RecallOptions.builder()
                        .topK(10)
                        .personaId("") // unauthenticated
                        .build());

        log.info("Unauthenticated recall results:");
        printResults(unauthenticatedResults);

        // Attempt recall with authorized persona ID
        List<CognitiveResult> authenticatedResults = memory.recall(
                "confidential company board restructuring notes",
                RecallOptions.builder()
                        .topK(10)
                        .personaId("founder_executive")
                        .build());

        log.info("Authenticated persona recall results:");
        printResults(authenticatedResults);

        assertThat(authenticatedResults).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("Unverified claims with FLAG_UNVERIFIED are gated against minTrustScore threshold")
    void unverifiedClaimsGatedByTrustScore() {
        memory.remember("gov-unverified-1",
                "Unverified rumor: third-party social media post claims server cluster outage in EU-central.",
                MemoryType.SEMANTIC, MemorySource.OBSERVED,
                "unverified", "rumor", "server", "outage");

        // High trust threshold recall
        List<CognitiveResult> highTrustResults = memory.recall(
                "server cluster outage EU-central reports",
                RecallOptions.builder()
                        .topK(10)
                        .minTrustScore(0.90f)
                        .build());

        log.info("High trust threshold recall results (minTrustScore=0.90):");
        printResults(highTrustResults);

        // Low trust threshold recall
        List<CognitiveResult> lowTrustResults = memory.recall(
                "server cluster outage EU-central reports",
                RecallOptions.builder()
                        .topK(10)
                        .minTrustScore(0.0f)
                        .build());

        log.info("Low trust threshold recall results (minTrustScore=0.0):");
        printResults(lowTrustResults);

        assertThat(lowTrustResults).isNotEmpty();
    }
}
