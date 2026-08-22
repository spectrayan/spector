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
package com.spectrayan.spector.memory.reflect;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;
import com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link ManifoldConsolidationRelay} wiring in the reflect pathway.
 */
class ReflectPathwayAismeWiringTest {

    @Test
    void relayName_isManifoldConsolidation() {
        ManifoldConsolidationRelay relay = new ManifoldConsolidationRelay(null, null);
        assertThat(relay.relayName()).isEqualTo("manifold_consolidation");
    }

    @Test
    void transmit_withManifold_consolidatesMetricTensorVersion() {
        CognitiveManifold manifold = new CognitiveManifold(4);
        int initialVersion = manifold.currentTensor().version();

        List<float[]> samplePairs = List.of(new float[]{0.1f, 0.2f, 0.0f, 0.0f});
        ManifoldConsolidationRelay relay = new ManifoldConsolidationRelay(manifold, null, () -> samplePairs);
        ReflectSignal signal = ReflectSignal.builder().build();

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();

        PersonalMetricTensor updated = manifold.currentTensor();
        assertThat(updated.version()).isEqualTo(initialVersion + 1);
    }
}
