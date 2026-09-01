/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;

@DisplayName("NamespaceBias tagWeights and domainFocus Salience Overlay Specifications")
class NamespaceBiasTagWeightsTest {

    @Test
    @DisplayName("Empty or null bias returns base profile unchanged")
    void testEmptyBiasReturnsBase() {
        SalienceProfile base = SalienceProfile.NEUTRAL;
        assertThat(NamespaceBiasApplier.apply(base, null)).isSameAs(base);
        assertThat(NamespaceBiasApplier.apply(base, NamespaceBias.EMPTY)).isSameAs(base);
        assertThat(NamespaceBiasApplier.apply(base, new NamespaceBias(List.of(), Map.of()))).isSameAs(base);
    }

    @Test
    @DisplayName("domainFocus adds interest domains at MEDIUM level")
    void testDomainFocusOverlay() {
        NamespaceBias bias = new NamespaceBias(List.of("quantum-computing", "neuroscience"), Map.of());
        SalienceProfile result = NamespaceBiasApplier.apply(SalienceProfile.NEUTRAL, bias);

        assertThat(result.interests())
                .contains(
                        new InterestDomain("quantum-computing", InterestLevel.MEDIUM),
                        new InterestDomain("neuroscience", InterestLevel.MEDIUM)
                );
    }

    @Test
    @DisplayName("tagWeights maps weights to appropriate interest and disinterest levels")
    void testTagWeightsOverlay() {
        NamespaceBias bias = new NamespaceBias(
                List.of(),
                Map.of(
                        "critical-tag", 2.0f,
                        "standard-tag", 1.0f,
                        "low-tag", 0.5f,
                        "ignored-tag", -1.0f
                )
        );

        SalienceProfile result = NamespaceBiasApplier.apply(SalienceProfile.NEUTRAL, bias);

        assertThat(result.interests())
                .contains(
                        new InterestDomain("critical-tag", InterestLevel.HIGH),
                        new InterestDomain("standard-tag", InterestLevel.MEDIUM),
                        new InterestDomain("low-tag", InterestLevel.LOW)
                );

        assertThat(result.disinterests())
                .contains(
                        new InterestDomain("ignored-tag", InterestLevel.HIGH)
                );
    }
}
