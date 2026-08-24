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
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LifespanRetentionController}.
 */
class LifespanRetentionControllerTest {

    private AismeConfig defaultConfig;
    private LifespanRetentionController controller;

    @BeforeEach
    void setUp() {
        defaultConfig = AismeConfig.defaultConfig();
        controller = new LifespanRetentionController(defaultConfig);
    }

    @Test
    void initialThreshold_atTargetVolumeAndEpoch0_matchesTau0() {
        assertThat(controller.getEpoch()).isEqualTo(0L);
        assertThat(controller.getVolume()).isEqualTo(100000L);

        float tau = controller.currentTau();
        assertThat(tau).isCloseTo(0.30f, within(1e-4f));
    }

    @Test
    void advanceEpoch_and_updateVolume_updatesState() {
        controller.advanceEpoch();
        controller.advanceEpoch();
        assertThat(controller.getEpoch()).isEqualTo(2L);

        controller.setEpoch(365L);
        assertThat(controller.getEpoch()).isEqualTo(365L);

        controller.updateVolume(150000L);
        assertThat(controller.getVolume()).isEqualTo(150000L);

        float tau = controller.currentTau();
        // tau = 0.30 * (1 + 0.15 * ln(2)) * (1.5)^1.2 ≈ 0.30 * 1.10397 * 1.62665 ≈ 0.5387
        assertThat(tau).isGreaterThan(0.50f).isLessThan(0.60f);
    }

    @Test
    void evaluate_coreTier_flashbulbIsPermanentlyRetained() {
        // Flashbulb true
        LifespanEvaluationResult res1 = controller.evaluate(0.40f, true, null);
        assertThat(res1.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(res1.tier()).isEqualTo(LifespanTier.CORE);
        assertThat(res1.flashbulbProtected()).isTrue();
        assertThat(res1.effectiveTau()).isEqualTo(0.0f);

        // Importance >= flashbulb threshold (0.85)
        LifespanEvaluationResult res2 = controller.evaluate(0.90f, false, null);
        assertThat(res2.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(res2.tier()).isEqualTo(LifespanTier.CORE);
        assertThat(res2.flashbulbProtected()).isTrue();

        // Milestone tags
        LifespanEvaluationResult res3 = controller.evaluate(0.45f, false, new String[]{"milestone:user_promotion", "work"});
        assertThat(res3.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(res3.tier()).isEqualTo(LifespanTier.CORE);
        assertThat(res3.flashbulbProtected()).isTrue();
    }

    @Test
    void evaluate_flavourTier_retainsOrConsolidates() {
        controller.setEpoch(0L);
        controller.updateVolume(100000L); // tau = 0.30

        // Importance 0.50 >= tau (0.30) -> RETAIN in FLAVOUR tier
        LifespanEvaluationResult resRetain = controller.evaluate(0.50f, false, new String[]{"context:meeting"});
        assertThat(resRetain.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(resRetain.tier()).isEqualTo(LifespanTier.FLAVOUR);
        assertThat(resRetain.flashbulbProtected()).isFalse();

        // Simulate year 10 under 3x volume pressure -> tau > 0.70
        controller.setEpoch(3650L);
        controller.updateVolume(300000L);
        float highTau = controller.currentTau();
        assertThat(highTau).isGreaterThan(0.70f);

        // Importance 0.50 < highTau -> CONSOLIDATE
        LifespanEvaluationResult resConsolidate = controller.evaluate(0.50f, false, new String[]{"context:meeting"});
        assertThat(resConsolidate.decision()).isEqualTo(LifespanRetentionDecision.CONSOLIDATE);
        assertThat(resConsolidate.tier()).isEqualTo(LifespanTier.FLAVOUR);
        assertThat(resConsolidate.flashbulbProtected()).isFalse();
    }

    @Test
    void evaluate_ephemeralTier_retainsOrPrunes() {
        controller.setEpoch(0L);
        controller.updateVolume(100000L); // tau = 0.30

        // Importance 0.15 < tau (0.30) -> PRUNE
        LifespanEvaluationResult resPrune = controller.evaluate(0.15f, false, new String[]{"sensor:heartbeat"});
        assertThat(resPrune.decision()).isEqualTo(LifespanRetentionDecision.PRUNE);
        assertThat(resPrune.tier()).isEqualTo(LifespanTier.EPHEMERAL);
        assertThat(resPrune.flashbulbProtected()).isFalse();
    }

    @Test
    void evaluate_rememberSignal_handlesNullAndFields() {
        LifespanEvaluationResult nullRes = controller.evaluate((RememberSignal) null);
        assertThat(nullRes.decision()).isEqualTo(LifespanRetentionDecision.PRUNE);
        assertThat(nullRes.tier()).isEqualTo(LifespanTier.EPHEMERAL);

        RememberSignal sig = com.spectrayan.spector.memory.remember.relay.RememberSignal.forCognitive(
                "mem-1",
                "Flashbulb invariant memory",
                new float[]{0.1f, 0.2f},
                com.spectrayan.spector.memory.model.MemoryType.EPISODIC,
                new String[]{"soul:covenant"},
                com.spectrayan.spector.memory.cortex.MemorySource.OBSERVED,
                null,
                com.spectrayan.spector.memory.model.SalienceProfile.NEUTRAL,
                (short) 1
        );
        sig.importance(0.95f);
        sig.flashbulb(true);

        LifespanEvaluationResult sigRes = controller.evaluate(sig);
        assertThat(sigRes.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(sigRes.tier()).isEqualTo(LifespanTier.CORE);
        assertThat(sigRes.flashbulbProtected()).isTrue();
    }

    @Test
    void disabledLifespan_returnsDefaultRetention() {
        AismeConfig disabled = AismeConfig.builder().enableLifespan(false).build();
        LifespanRetentionController disabledController = new LifespanRetentionController(disabled);

        LifespanEvaluationResult res = disabledController.evaluate(0.20f, false, null);
        assertThat(res.decision()).isEqualTo(LifespanRetentionDecision.RETAIN);
        assertThat(res.tier()).isEqualTo(LifespanTier.FLAVOUR);
    }
}
