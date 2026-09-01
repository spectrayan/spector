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
package com.spectrayan.spector.memory.aisme.lifespan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.lifespan.LifespanEvaluationResult.LifespanRetentionDecision;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-decade lifespan simulation and high-throughput benchmark for lifespan-adaptive forgetting.
 *
 * <p>Simulates a 100-year operational horizon (36,500 sleep cycles) under varying capacity
 * pressure conditions, verifying that all autobiographical core invariants survive 100 years
 * while ephemeral noise is pruned efficiently.</p>
 */
class MultiDecadeLifespanSimulationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(MultiDecadeLifespanSimulationBenchmarkTest.class);

    private AismeConfig config;
    private LifespanRetentionController controller;

    record SyntheticMemory(float importance, boolean flashbulb, String[] tags, LifespanTier expectedTier) {}

    @BeforeEach
    void setUp() {
        config = AismeConfig.builder()
                .enableLifespan(true)
                .lifespanTau0(0.30f)
                .lifespanK(0.15f)
                .lifespanT0Epochs(365L)
                .lifespanVTarget(100000L)
                .lifespanGamma(1.2f)
                .lifespanFlashbulbProtect(true)
                .build();
        controller = new LifespanRetentionController(config);
    }

    @Test
    @DisplayName("Simulate 100-Year Multi-Decade Horizon with Autobiographical Tiering Invariants")
    void simulate100YearLifespanHorizon() {
        // Generate test memory population
        List<SyntheticMemory> population = new ArrayList<>();

        // 100 CORE milestones (flashbulbs and covenants)
        for (int i = 0; i < 100; i++) {
            population.add(new SyntheticMemory(
                    0.90f + ThreadLocalRandom.current().nextFloat() * 0.10f,
                    true,
                    new String[]{"milestone:graduation", "soul:invariable"},
                    LifespanTier.CORE
            ));
        }

        // 5,000 FLAVOUR contextual memories
        for (int i = 0; i < 5000; i++) {
            population.add(new SyntheticMemory(
                    0.35f + ThreadLocalRandom.current().nextFloat() * 0.45f, // [0.35, 0.80)
                    false,
                    new String[]{"context:project_update"},
                    LifespanTier.FLAVOUR
            ));
        }

        // 50,000 EPHEMERAL noise memories
        for (int i = 0; i < 50000; i++) {
            population.add(new SyntheticMemory(
                    ThreadLocalRandom.current().nextFloat() * 0.28f, // [0.00, 0.28)
                    false,
                    new String[]{"sensor:heartbeat"},
                    LifespanTier.EPHEMERAL
            ));
        }

        // Verify across milestones
        long[] epochs = new long[]{1, 365, 3650, 18250, 36500}; // Day 1, Year 1, Year 10, Year 50, Year 100
        for (long epoch : epochs) {
            controller.setEpoch(epoch);
            controller.updateVolume(100000L); // Steady-state target volume

            float tau = controller.currentTau();
            log.info("Lifespan Epoch {} (Year {}): tau(t) = {}", epoch, epoch / 365, tau);

            int coreRetained = 0;
            int flavourRetained = 0;
            int flavourConsolidated = 0;
            int ephemeralPruned = 0;

            for (SyntheticMemory mem : population) {
                LifespanEvaluationResult result = controller.evaluate(mem.importance(), mem.flashbulb(), mem.tags());
                if (result.tier() == LifespanTier.CORE) {
                    assertThat(result.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
                    assertThat(result.flashbulbProtected()).isTrue();
                    coreRetained++;
                } else if (result.tier() == LifespanTier.FLAVOUR) {
                    if (result.decision() == LifespanRetentionDecision.RETAIN) {
                        flavourRetained++;
                    } else if (result.decision() == LifespanRetentionDecision.CONSOLIDATE) {
                        flavourConsolidated++;
                    }
                } else if (result.tier() == LifespanTier.EPHEMERAL) {
                    if (result.decision() == LifespanRetentionDecision.PRUNE) {
                        ephemeralPruned++;
                    }
                }
            }

            // 100% of CORE memories must survive after 100 years
            assertThat(coreRetained).isEqualTo(100);

            // 100% of EPHEMERAL noise memories must be pruned
            assertThat(ephemeralPruned).isEqualTo(50000);

            // Flavour retention decreases gracefully as tau rises over 100 years
            assertThat(flavourRetained + flavourConsolidated).isEqualTo(5000);
        }
    }

    @Test
    @DisplayName("High Capacity Pressure Surge triggers aggressive pruning while protecting Core")
    void testCapacityPressureSurge() {
        controller.setEpoch(3650L); // Year 10
        controller.updateVolume(250000L); // 2.5x volume pressure surge

        float highTau = controller.currentTau();
        // tau = 0.30 * (1 + 0.15 * ln(11)) * (2.5)^1.2 ≈ 0.30 * 1.3597 * 3.0039 ≈ 1.22 -> clamped to 1.0
        assertThat(highTau).isGreaterThanOrEqualTo(0.95f);

        // Core milestone is still 100% retained
        LifespanEvaluationResult coreRes = controller.evaluate(0.95f, true, new String[]{"milestone:birth"});
        assertThat(coreRes.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(coreRes.tier()).isEqualTo(LifespanTier.CORE);
        assertThat(coreRes.flashbulbProtected()).isTrue();

        // Moderate flavour memory (0.60) is consolidated under extreme pressure
        LifespanEvaluationResult flavourRes = controller.evaluate(0.60f, false, new String[]{"notes"});
        assertThat(flavourRes.decision()).isEqualTo(LifespanRetentionDecision.CONSOLIDATE);
        assertThat(flavourRes.tier()).isEqualTo(LifespanTier.FLAVOUR);
    }

    @Test
    @DisplayName("Throughput Benchmark: Evaluate 100,000 memory decisions in < 50ms")
    void benchmarkHighThroughputEvaluation() {
        controller.setEpoch(3650L);
        controller.updateVolume(120000L);

        int count = 100_000;
        float[] importances = new float[count];
        boolean[] flashbulbs = new boolean[count];
        for (int i = 0; i < count; i++) {
            importances[i] = ThreadLocalRandom.current().nextFloat();
            flashbulbs[i] = (i % 100 == 0);
        }

        // Warmup
        for (int i = 0; i < 5000; i++) {
            controller.evaluate(importances[i], flashbulbs[i], null);
        }

        long start = System.nanoTime();
        int coreCount = 0;
        int retainCount = 0;
        for (int i = 0; i < count; i++) {
            LifespanEvaluationResult res = controller.evaluate(importances[i], flashbulbs[i], null);
            if (res.tier() == LifespanTier.CORE) {
                coreCount++;
            }
            if (res.decision() == LifespanRetentionDecision.RETAIN) {
                retainCount++;
            }
        }
        long durationNs = System.nanoTime() - start;
        double durationMs = durationNs / 1_000_000.0;
        double opsPerSec = count / (durationNs / 1_000_000_000.0);

        log.info("Lifespan retention benchmark: {} evaluations in {:.2f} ms ({:.0f} ops/sec, core={}, retained={})",
                count, durationMs, opsPerSec, coreCount, retainCount);

        assertThat(durationMs).isLessThan(50.0);
    }
}
