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
package com.spectrayan.spector.memory.decide.relay;

import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.policy.CognitivePolicy;
import com.spectrayan.spector.memory.aisme.policy.ExpectedFreeEnergyCalculator;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.aisme.policy.PolicyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRelayTest {

    @Test
    void testExperimentRelayWithPolicyInferenceEngine() {
        ExperimentRelay relay = new ExperimentRelay();
        assertThat(relay.relayName()).isEqualTo("experiment_relay");

        int dim = 8;
        ExpectedFreeEnergyCalculator calculator = new ExpectedFreeEnergyCalculator(
                List.of(), 0.4f, 0.35f, 0.15f, 0.10f, 1.0f, 1.0f);
        HomeostaticCore homeostaticCore = new HomeostaticCore();
        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, dim);
        MentalStateTracker tracker = new MentalStateTracker(selfModel);
        PolicyInferenceEngine engine = new PolicyInferenceEngine(calculator, homeostaticCore, tracker, 1.0f);

        CognitivePolicy p1 = CognitivePolicy.of(
                PolicyType.CLARIFYING_INTERACTION,
                new float[]{0.1f, 0.2f, 0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f}
        );

        CognitivePolicy p2 = CognitivePolicy.of(
                PolicyType.EPISTEMIC_EXPLORATION,
                new float[]{0.4f, 0.5f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
                new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f}
        );

        DecideSignal signal = DecideSignal.builder()
                .policyInferenceEngine(engine)
                .candidatePolicies(List.of(p1, p2))
                .build();

        boolean result = relay.transmit(signal);
        assertThat(result).isTrue();
        assertThat(signal.report()).isNotNull();
        assertThat(signal.report().selectedPolicy()).isNotNull();
        assertThat(signal.report().rankedPolicies()).hasSize(2);
    }
}
