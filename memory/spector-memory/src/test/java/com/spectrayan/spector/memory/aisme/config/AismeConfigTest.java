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
package com.spectrayan.spector.memory.aisme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AismeConfig}.
 */
class AismeConfigTest {

    @Test
    void disabledConfig_hasAllFlagsDisabled() {
        AismeConfig config = AismeConfig.disabled();
        assertThat(config.enabled()).isFalse();
        assertThat(config.enableHomeostasis()).isFalse();
        assertThat(config.enableFreeEnergy()).isFalse();
        assertThat(config.enableHopfield()).isFalse();
        assertThat(config.enableManifold()).isFalse();
        assertThat(config.enablePredictiveCoding()).isFalse();
        assertThat(config.enableConsciousnessContinuity()).isFalse();
        assertThat(config.enableGlobalWorkspace()).isFalse();
    }

    @Test
    void defaultConfig_hasAllFlagsEnabled() {
        AismeConfig config = AismeConfig.defaultConfig();
        assertThat(config.enabled()).isTrue();
        assertThat(config.enableHomeostasis()).isTrue();
        assertThat(config.enableFreeEnergy()).isTrue();
        assertThat(config.enableHopfield()).isTrue();
        assertThat(config.enableManifold()).isTrue();
        assertThat(config.enablePredictiveCoding()).isTrue();
        assertThat(config.enableConsciousnessContinuity()).isTrue();
        assertThat(config.enableGlobalWorkspace()).isTrue();
        assertThat(config.globalWorkspaceCapacity()).isEqualTo(7);
    }

    @Test
    void builder_customizesParameters() {
        AismeConfig config = AismeConfig.builder()
                .enabled(true)
                .enableHomeostasis(true)
                .enableFreeEnergy(false)
                .globalWorkspaceCapacity(5)
                .hopfieldTemperature(3.5f)
                .manifoldSigma(1.2f)
                .phiCohesionThreshold(0.1f)
                .build();

        assertThat(config.enabled()).isTrue();
        assertThat(config.enableHomeostasis()).isTrue();
        assertThat(config.enableFreeEnergy()).isFalse();
        assertThat(config.globalWorkspaceCapacity()).isEqualTo(5);
        assertThat(config.hopfieldTemperature()).isEqualTo(3.5f);
        assertThat(config.manifoldSigma()).isEqualTo(1.2f);
        assertThat(config.phiCohesionThreshold()).isEqualTo(0.1f);
    }

    @Test
    void invalidParameters_throwsValidationException() {
        assertThatThrownBy(() -> AismeConfig.builder().globalWorkspaceCapacity(0).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().hopfieldTemperature(-1.0f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().manifoldSigma(0.0f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().phiCohesionThreshold(-0.5f).build())
                .isInstanceOf(SpectorValidationException.class);
    }
}
