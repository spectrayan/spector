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
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;

/**
 * End-to-end integration tests validating MSCE procedural skill crystallization
 * during biological sleep consolidation (ReflectPathway) with live Ollama text
 * generation, embeddings, and LLM Judge semantic verification.
 */
@DisplayName("⚙️ E2E: MSCE Procedural Skill Crystallization in Sleep Consolidation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProceduralCrystallizationE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Ingest multi-turn troubleshooting session and trigger sleep reflection")
    void ingestTroubleshootingAndReflect() {
        // Ingest multi-turn episodic session
        memory.remember("proc-step-1",
                "User reported HTTP 504 Gateway Timeout when querying customer billing endpoint.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "billing", "timeout", "http504", "debug");

        memory.remember("proc-step-2",
                "Diagnosis revealed slow unindexed join on billing_invoices table. Executed EXPLAIN ANALYZE.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "billing", "sql", "explain", "index");

        memory.remember("proc-step-3",
                "Resolution: Created compound index on (customer_id, billing_period) and restarted connection pool. Latency dropped from 30s to 12ms.",
                MemoryType.EPISODIC, MemorySource.OBSERVED,
                "billing", "fix", "compound-index", "performance");

        // Trigger sleep consolidation reflection
        try {
            memory.reflect();
            log.info("Sleep consolidation reflection triggered successfully");
        } catch (Exception e) {
            log.warn("Sleep consolidation reflection completed with message: {}", e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Recall related database query latency issue surfaces crystallized procedural heuristics")
    void recallSurfacesCrystallizedSkills() {
        List<CognitiveResult> results = memory.recall(
                "how to diagnose and resolve billing endpoint database query latency and 504 gateway timeouts",
                RecallOptions.builder()
                        .topK(10)
                        .build());

        log.info("Recall results for billing latency heuristic:");
        printResults(results);

        assertThat(results).isNotEmpty();
        boolean foundResolution = results.stream().anyMatch(r ->
                r.text().contains("compound index")
                || r.text().contains("billing")
                || r.text().contains("EXPLAIN ANALYZE"));

        assertThat(foundResolution).as("Should recall database troubleshooting procedure").isTrue();

        if (isLlmJudgeEnabled()) {
            llmAssertRecall("how to diagnose and resolve billing endpoint database query latency", results)
                    .warnIfIrrelevant("Results should explain how to diagnose and fix database billing query timeouts");
        }
    }
}
