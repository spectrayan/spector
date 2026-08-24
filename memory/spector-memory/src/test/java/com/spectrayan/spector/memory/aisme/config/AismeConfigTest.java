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
        assertThat(config.enableSoftIdentityAnchor()).isFalse();
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
        assertThat(config.enableSoftIdentityAnchor()).isTrue();
        assertThat(config.identityAnchorEta()).isEqualTo(0.0001f);
        assertThat(config.identityLyapunovThreshold()).isEqualTo(0.15f);
        assertThat(config.identityCoreSnapshotEpochs()).isEqualTo(50);
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
                .enableSoftIdentityAnchor(true)
                .identityAnchorEta(0.0002f)
                .identityLyapunovThreshold(0.25f)
                .identityCoreSnapshotEpochs(100)
                .build();

        assertThat(config.enabled()).isTrue();
        assertThat(config.enableHomeostasis()).isTrue();
        assertThat(config.enableFreeEnergy()).isFalse();
        assertThat(config.globalWorkspaceCapacity()).isEqualTo(5);
        assertThat(config.hopfieldTemperature()).isEqualTo(3.5f);
        assertThat(config.manifoldSigma()).isEqualTo(1.2f);
        assertThat(config.phiCohesionThreshold()).isEqualTo(0.1f);
        assertThat(config.enableSoftIdentityAnchor()).isTrue();
        assertThat(config.identityAnchorEta()).isEqualTo(0.0002f);
        assertThat(config.identityLyapunovThreshold()).isEqualTo(0.25f);
        assertThat(config.identityCoreSnapshotEpochs()).isEqualTo(100);
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

        assertThatThrownBy(() -> AismeConfig.builder().identityAnchorEta(-0.01f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().identityLyapunovThreshold(-0.5f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().identityCoreSnapshotEpochs(0).build())
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    void fromProperties_mapsAllFields() {
        com.spectrayan.spector.config.properties.AismeProperties props = new com.spectrayan.spector.config.properties.AismeProperties();
        props.setEnabled(true);
        props.setEnableHomeostasis(true);
        props.setEnableFreeEnergy(false);
        props.setGlobalWorkspaceCapacity(9);
        props.setHopfieldTemperature(5.5f);
        props.setManifoldSigma(1.8f);
        props.setPhiCohesionThreshold(0.08f);
        props.setEnableSoftIdentityAnchor(true);
        props.setIdentityAnchorEta(0.0003f);
        props.setIdentityLyapunovThreshold(0.18f);
        props.setIdentityCoreSnapshotEpochs(60);

        AismeConfig config = AismeConfig.fromProperties(props);

        assertThat(config.enabled()).isTrue();
        assertThat(config.enableHomeostasis()).isTrue();
        assertThat(config.enableFreeEnergy()).isFalse();
        assertThat(config.globalWorkspaceCapacity()).isEqualTo(9);
        assertThat(config.hopfieldTemperature()).isEqualTo(5.5f);
        assertThat(config.manifoldSigma()).isEqualTo(1.8f);
        assertThat(config.phiCohesionThreshold()).isEqualTo(0.08f);
        assertThat(config.enableSoftIdentityAnchor()).isTrue();
        assertThat(config.identityAnchorEta()).isEqualTo(0.0003f);
        assertThat(config.identityLyapunovThreshold()).isEqualTo(0.18f);
        assertThat(config.identityCoreSnapshotEpochs()).isEqualTo(60);

        // Disabled or null props returns disabled config
        props.setEnabled(false);
        assertThat(AismeConfig.fromProperties(props).enabled()).isFalse();
        assertThat(AismeConfig.fromProperties(null).enabled()).isFalse();
    }
}
