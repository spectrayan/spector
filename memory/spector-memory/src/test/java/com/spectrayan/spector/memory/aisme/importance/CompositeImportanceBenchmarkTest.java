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
package com.spectrayan.spector.memory.aisme.importance;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1,000-Signal Multi-Scenario Simulation Benchmark for Multimodal Composite Importance Scoring \(I(o_t)\).
 *
 * <p>Simulates diverse operational workloads across 4 distinct neurocognitive categories:
 * <ul>
 *   <li><b>Category A (250 signals)</b>: High-affect emotional disclosures & critical outages.</li>
 *   <li><b>Category B (250 signals)</b>: Explicit prospective goals & task commitments.</li>
 *   <li><b>Category C (250 signals)</b>: Exploratory & novel scientific discoveries.</li>
 *   <li><b>Category D (250 signals)</b>: Routine low-salience telemetry and background noise.</li>
 * </ul>
 */
class CompositeImportanceBenchmarkTest {

    private AismeConfig config;
    private CompositeImportanceScorer scorer;

    @BeforeEach
    void setUp() {
        config = AismeConfig.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceWeightSurprise(0.20f)
                .importanceWeightAffect(0.20f)
                .importanceWeightGoal(0.20f)
                .importanceWeightSocial(0.20f)
                .importanceWeightNovelty(0.20f)
                .importanceFlashbulbThreshold(0.85f)
                .build();
        scorer = new CompositeImportanceScorer(config);
    }

    @Test
    void run1000SignalMultiScenarioSimulationBenchmark() {
        int scenarioCountPerCategory = 250;
        int totalSignals = scenarioCountPerCategory * 4;

        List<RememberSignal> categoryA = new ArrayList<>();
        List<RememberSignal> categoryB = new ArrayList<>();
        List<RememberSignal> categoryC = new ArrayList<>();
        List<RememberSignal> categoryD = new ArrayList<>();

        // 1. Category A: Emotional Disclosures & Outages
        for (int i = 0; i < scenarioCountPerCategory; i++) {
            RememberSignal s = RememberSignal.forCognitive(
                    "catA-" + i,
                    "CRITICAL outage panic: user:john said catastrophic failure in cluster " + i,
                    new float[]{0.9f, 0.1f, 0.0f, 0.0f},
                    MemoryType.EPISODIC,
                    new String[]{"user:john", "critical"},
                    MemorySource.OBSERVED,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );
            s.eventDensityMetrics(new EventDensityMetrics(2.5f, 0.9f, 0.90f, 0.85f, true, 30.0f));
            s.nearestDist(1.5f);
            categoryA.add(s);
        }

        // 2. Category B: Prospective Goals & Commitments
        for (int i = 0; i < scenarioCountPerCategory; i++) {
            RememberSignal s = RememberSignal.forCognitive(
                    "catB-" + i,
                    "Action Item: I commit to milestone task plan for sprint " + i,
                    new float[]{0.1f, 0.9f, 0.0f, 0.0f},
                    MemoryType.EPISODIC,
                    new String[]{"speaker:alice", "milestone"},
                    MemorySource.OBSERVED,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );
            s.eventDensityMetrics(new EventDensityMetrics(0.8f, 0.4f, 0.40f, 0.30f, false, 10.0f));
            s.nearestDist(0.8f);
            categoryB.add(s);
        }

        // 3. Category C: Exploratory Novel Discoveries
        for (int i = 0; i < scenarioCountPerCategory; i++) {
            RememberSignal s = RememberSignal.forCognitive(
                    "catC-" + i,
                    "Observed novel topological manifold phase transition in experiment " + i,
                    new float[]{0.0f, 0.0f, 0.9f, 0.1f},
                    MemoryType.EPISODIC,
                    new String[]{"research"},
                    MemorySource.OBSERVED,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );
            s.eventDensityMetrics(new EventDensityMetrics(1.8f, 0.7f, 0.75f, 0.80f, true, 20.0f));
            s.nearestDist(1.9f);
            categoryC.add(s);
        }

        // 4. Category D: Routine Background Telemetry
        for (int i = 0; i < scenarioCountPerCategory; i++) {
            RememberSignal s = RememberSignal.forCognitive(
                    "catD-" + i,
                    "heartbeat ping ok status 200 frame " + i,
                    new float[]{0.01f, 0.01f, 0.01f, 0.01f},
                    MemoryType.EPISODIC,
                    new String[]{"telemetry"},
                    MemorySource.OBSERVED,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );
            s.eventDensityMetrics(new EventDensityMetrics(0.1f, 0.05f, 0.05f, 0.05f, false, 0.5f));
            s.nearestDist(0.05f);
            categoryD.add(s);
        }

        long startNs = System.nanoTime();

        // Evaluate Category A
        float sumA = 0.0f;
        int flashbulbCountA = 0;
        for (RememberSignal s : categoryA) {
            float score = scorer.evaluateSignal(s, CognitiveProfile.DEBUGGING);
            sumA += score;
            if (scorer.isFlashbulb(score)) {
                flashbulbCountA++;
            }
        }

        // Evaluate Category B
        float sumB = 0.0f;
        for (RememberSignal s : categoryB) {
            float score = scorer.evaluateSignal(s, CognitiveProfile.THE_EXECUTOR);
            sumB += score;
        }

        // Evaluate Category C
        float sumC = 0.0f;
        for (RememberSignal s : categoryC) {
            float score = scorer.evaluateSignal(s, CognitiveProfile.EXPLORING);
            sumC += score;
        }

        // Evaluate Category D
        float sumD = 0.0f;
        for (RememberSignal s : categoryD) {
            float score = scorer.evaluateSignal(s, CognitiveProfile.BALANCED);
            sumD += score;
        }

        long durationNs = System.nanoTime() - startNs;
        double durationMs = durationNs / 1_000_000.0;
        double opsPerSec = (totalSignals / (durationNs / 1_000_000_000.0));

        float meanA = sumA / scenarioCountPerCategory;
        float meanB = sumB / scenarioCountPerCategory;
        float meanC = sumC / scenarioCountPerCategory;
        float meanD = sumD / scenarioCountPerCategory;

        System.out.printf("=== Composite Importance 1,000-Signal Benchmark ===%n");
        System.out.printf("Total evaluated: %d signals in %.2f ms (%.1f evals/sec)%n", totalSignals, durationMs, opsPerSec);
        System.out.printf("Category A (Emotional/Critical) Mean: %.3f (Flashbulbs: %d/%d)%n", meanA, flashbulbCountA, scenarioCountPerCategory);
        System.out.printf("Category B (Goals/Commitments)  Mean: %.3f%n", meanB);
        System.out.printf("Category C (Novel Discovery)    Mean: %.3f%n", meanC);
        System.out.printf("Category D (Routine Noise)      Mean: %.3f%n", meanD);

        // Quality & Salience Gate Assertions
        assertThat(meanA).isGreaterThanOrEqualTo(0.80f);
        assertThat(flashbulbCountA).isGreaterThan(200);
        assertThat(meanB).isGreaterThanOrEqualTo(0.60f);
        assertThat(meanC).isGreaterThanOrEqualTo(0.55f);
        assertThat(meanD).isLessThan(0.20f);

        // Discrimination factor: salient signals should have >= 3.0x higher mean importance than noise
        float salientMean = (meanA + meanB + meanC) / 3.0f;
        assertThat(salientMean).isGreaterThan(3.0f * meanD);
    }
}
