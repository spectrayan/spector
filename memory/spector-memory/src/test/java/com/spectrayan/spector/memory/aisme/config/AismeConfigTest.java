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
        assertThat(config.enableEventDensity()).isFalse();
        assertThat(config.enableBocpd()).isFalse();
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
        assertThat(config.enableEventDensity()).isTrue();
        assertThat(config.eventDensityThreshold()).isEqualTo(0.50f);
        assertThat(config.eventDensityAlphaKl()).isEqualTo(0.40f);
        assertThat(config.eventDensityBetaGradient()).isEqualTo(0.30f);
        assertThat(config.eventDensityGammaSurprise()).isEqualTo(0.30f);
        assertThat(config.eventDensitySamplingMinHz()).isEqualTo(0.10f);
        assertThat(config.eventDensitySamplingMaxHz()).isEqualTo(30.0f);
        assertThat(config.enableBocpd()).isTrue();
        assertThat(config.bocpdHazardLambda()).isEqualTo(100.0f);
        assertThat(config.bocpdChangePointThreshold()).isEqualTo(0.65f);
        assertThat(config.bocpdSurprisalCutThreshold()).isEqualTo(1.50f);
        assertThat(config.bocpdMaxEpisodeFrames()).isEqualTo(200);
        assertThat(config.bocpdMaxRunLength()).isEqualTo(150);
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
                .enableEventDensity(true)
                .eventDensityThreshold(0.60f)
                .eventDensityAlphaKl(0.50f)
                .eventDensityBetaGradient(0.25f)
                .eventDensityGammaSurprise(0.25f)
                .eventDensitySamplingMinHz(0.5f)
                .eventDensitySamplingMaxHz(20.0f)
                .enableBocpd(true)
                .bocpdHazardLambda(80.0f)
                .bocpdChangePointThreshold(0.70f)
                .bocpdSurprisalCutThreshold(1.80f)
                .bocpdMaxEpisodeFrames(180)
                .bocpdMaxRunLength(120)
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
        assertThat(config.enableEventDensity()).isTrue();
        assertThat(config.eventDensityThreshold()).isEqualTo(0.60f);
        assertThat(config.enableBocpd()).isTrue();
        assertThat(config.bocpdHazardLambda()).isEqualTo(80.0f);
        assertThat(config.bocpdChangePointThreshold()).isEqualTo(0.70f);
        assertThat(config.bocpdSurprisalCutThreshold()).isEqualTo(1.80f);
        assertThat(config.bocpdMaxEpisodeFrames()).isEqualTo(180);
        assertThat(config.bocpdMaxRunLength()).isEqualTo(120);
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

        assertThatThrownBy(() -> AismeConfig.builder().eventDensityThreshold(-0.1f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().eventDensitySamplingMinHz(0.0f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().bocpdHazardLambda(0.0f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().bocpdChangePointThreshold(1.5f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().bocpdSurprisalCutThreshold(-0.1f).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().bocpdMaxEpisodeFrames(0).build())
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> AismeConfig.builder().bocpdMaxRunLength(0).build())
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
        props.setEnableEventDensity(true);
        props.setEventDensityThreshold(0.55f);
        props.setEventDensityAlphaKl(0.45f);
        props.setEventDensityBetaGradient(0.35f);
        props.setEventDensityGammaSurprise(0.20f);
        props.setEventDensitySamplingMinHz(0.25f);
        props.setEventDensitySamplingMaxHz(25.0f);
        props.setEnableBocpd(true);
        props.setBocpdHazardLambda(95.0f);
        props.setBocpdChangePointThreshold(0.68f);
        props.setBocpdSurprisalCutThreshold(1.65f);
        props.setBocpdMaxEpisodeFrames(220);
        props.setBocpdMaxRunLength(160);

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
        assertThat(config.enableEventDensity()).isTrue();
        assertThat(config.eventDensityThreshold()).isEqualTo(0.55f);
        assertThat(config.enableBocpd()).isTrue();
        assertThat(config.bocpdHazardLambda()).isEqualTo(95.0f);
        assertThat(config.bocpdChangePointThreshold()).isEqualTo(0.68f);
        assertThat(config.bocpdSurprisalCutThreshold()).isEqualTo(1.65f);
        assertThat(config.bocpdMaxEpisodeFrames()).isEqualTo(220);
        assertThat(config.bocpdMaxRunLength()).isEqualTo(160);

        // Disabled or null props returns disabled config
        props.setEnabled(false);
        assertThat(AismeConfig.fromProperties(props).enabled()).isFalse();
        assertThat(AismeConfig.fromProperties(null).enabled()).isFalse();
    }
}
