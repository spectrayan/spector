/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.config.properties.AismeProperties;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AismeProperties} — verifies default values, mutators, and fluent accessors.
 */
class AismePropertiesTest {

    @Test
    void aismeProperties_defaultValues() {
        AismeProperties props = new AismeProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.isEnableHomeostasis()).isTrue();
        assertThat(props.isEnableFreeEnergy()).isTrue();
        assertThat(props.isEnableHopfield()).isTrue();
        assertThat(props.isEnableManifold()).isTrue();
        assertThat(props.isEnablePredictiveCoding()).isTrue();
        assertThat(props.isEnableConsciousnessContinuity()).isTrue();
        assertThat(props.isEnableGlobalWorkspace()).isTrue();

        assertThat(props.getGlobalWorkspaceCapacity()).isEqualTo(7);
        assertThat(props.getHopfieldTemperature()).isEqualTo(4.0f);
        assertThat(props.getManifoldSigma()).isEqualTo(1.0f);
        assertThat(props.getPhiCohesionThreshold()).isEqualTo(0.05f);

        assertThat(props.isEnableSoftIdentityAnchor()).isTrue();
        assertThat(props.getIdentityAnchorEta()).isEqualTo(0.0001f);
        assertThat(props.getIdentityLyapunovThreshold()).isEqualTo(0.15f);
        assertThat(props.getIdentityCoreSnapshotEpochs()).isEqualTo(50);

        assertThat(props.isEnableEventDensity()).isTrue();
        assertThat(props.getEventDensityThreshold()).isEqualTo(0.50f);
        assertThat(props.getEventDensityAlphaKl()).isEqualTo(0.40f);
        assertThat(props.getEventDensityBetaGradient()).isEqualTo(0.30f);
        assertThat(props.getEventDensityGammaSurprise()).isEqualTo(0.30f);
        assertThat(props.getEventDensitySamplingMinHz()).isEqualTo(0.10f);
        assertThat(props.getEventDensitySamplingMaxHz()).isEqualTo(30.0f);

        assertThat(props.isEnableBocpd()).isTrue();
        assertThat(props.getBocpdHazardLambda()).isEqualTo(100.0f);
        assertThat(props.getBocpdChangePointThreshold()).isEqualTo(0.65f);
        assertThat(props.getBocpdSurprisalCutThreshold()).isEqualTo(1.50f);
        assertThat(props.getBocpdMaxEpisodeFrames()).isEqualTo(200);
        assertThat(props.getBocpdMaxRunLength()).isEqualTo(150);

        assertThat(props.isEnablePrivacy()).isFalse();
        assertThat(props.getPrivacyEpsilon()).isEqualTo(2.0f);
        assertThat(props.getPrivacyDelta()).isEqualTo(1e-5f);
        assertThat(props.getPrivacyClippingNorm()).isEqualTo(1.0f);
        assertThat(props.isPrivacyAnonymizePii()).isTrue();
        assertThat(props.getPrivacyPseudonymizationSalt()).isEqualTo("spector-privacy-salt");

        assertThat(props.isEnableImportance()).isTrue();
        assertThat(props.getImportanceWeightSurprise()).isEqualTo(0.20f);
        assertThat(props.getImportanceWeightAffect()).isEqualTo(0.20f);
        assertThat(props.getImportanceWeightGoal()).isEqualTo(0.20f);
        assertThat(props.getImportanceWeightSocial()).isEqualTo(0.20f);
        assertThat(props.getImportanceWeightNovelty()).isEqualTo(0.20f);
        assertThat(props.getImportanceFlashbulbThreshold()).isEqualTo(0.85f);
        assertThat(props.isEnableLifespan()).isTrue();
        assertThat(props.getLifespanTau0()).isEqualTo(0.30f);
        assertThat(props.getLifespanK()).isEqualTo(0.15f);
        assertThat(props.getLifespanT0Epochs()).isEqualTo(365L);
        assertThat(props.getLifespanVTarget()).isEqualTo(100000L);
        assertThat(props.getLifespanGamma()).isEqualTo(1.2f);
        assertThat(props.isLifespanFlashbulbProtect()).isTrue();

        // Fluent accessors
        assertThat(props.enabled()).isFalse();
        assertThat(props.enableHomeostasis()).isTrue();
        assertThat(props.enableFreeEnergy()).isTrue();
        assertThat(props.enableHopfield()).isTrue();
        assertThat(props.enableManifold()).isTrue();
        assertThat(props.enablePredictiveCoding()).isTrue();
        assertThat(props.enableConsciousnessContinuity()).isTrue();
        assertThat(props.enableGlobalWorkspace()).isTrue();
        assertThat(props.globalWorkspaceCapacity()).isEqualTo(7);
        assertThat(props.hopfieldTemperature()).isEqualTo(4.0f);
        assertThat(props.manifoldSigma()).isEqualTo(1.0f);
        assertThat(props.phiCohesionThreshold()).isEqualTo(0.05f);
        assertThat(props.enableSoftIdentityAnchor()).isTrue();
        assertThat(props.identityAnchorEta()).isEqualTo(0.0001f);
        assertThat(props.identityLyapunovThreshold()).isEqualTo(0.15f);
        assertThat(props.identityCoreSnapshotEpochs()).isEqualTo(50);

        assertThat(props.enableEventDensity()).isTrue();
        assertThat(props.eventDensityThreshold()).isEqualTo(0.50f);
        assertThat(props.eventDensityAlphaKl()).isEqualTo(0.40f);
        assertThat(props.eventDensityBetaGradient()).isEqualTo(0.30f);
        assertThat(props.eventDensityGammaSurprise()).isEqualTo(0.30f);
        assertThat(props.eventDensitySamplingMinHz()).isEqualTo(0.10f);
        assertThat(props.eventDensitySamplingMaxHz()).isEqualTo(30.0f);

        assertThat(props.enableBocpd()).isTrue();
        assertThat(props.bocpdHazardLambda()).isEqualTo(100.0f);
        assertThat(props.bocpdChangePointThreshold()).isEqualTo(0.65f);
        assertThat(props.bocpdSurprisalCutThreshold()).isEqualTo(1.50f);
        assertThat(props.bocpdMaxEpisodeFrames()).isEqualTo(200);
        assertThat(props.bocpdMaxRunLength()).isEqualTo(150);

        assertThat(props.enablePrivacy()).isFalse();
        assertThat(props.privacyEpsilon()).isEqualTo(2.0f);
        assertThat(props.privacyDelta()).isEqualTo(1e-5f);
        assertThat(props.privacyClippingNorm()).isEqualTo(1.0f);
        assertThat(props.privacyAnonymizePii()).isTrue();
        assertThat(props.privacyPseudonymizationSalt()).isEqualTo("spector-privacy-salt");

        assertThat(props.enableImportance()).isTrue();
        assertThat(props.importanceWeightSurprise()).isEqualTo(0.20f);
        assertThat(props.importanceWeightAffect()).isEqualTo(0.20f);
        assertThat(props.importanceWeightGoal()).isEqualTo(0.20f);
        assertThat(props.importanceWeightSocial()).isEqualTo(0.20f);
        assertThat(props.importanceWeightNovelty()).isEqualTo(0.20f);
        assertThat(props.importanceFlashbulbThreshold()).isEqualTo(0.85f);

        assertThat(props.enableLifespan()).isTrue();
        assertThat(props.lifespanTau0()).isEqualTo(0.30f);
        assertThat(props.lifespanK()).isEqualTo(0.15f);
        assertThat(props.lifespanT0Epochs()).isEqualTo(365L);
        assertThat(props.lifespanVTarget()).isEqualTo(100000L);
        assertThat(props.lifespanGamma()).isEqualTo(1.2f);
        assertThat(props.lifespanFlashbulbProtect()).isTrue();
    }

    @Test
    void aismeProperties_mutatorsAndValidation() {
        AismeProperties props = new AismeProperties();
        props.setEnabled(true);
        props.setEnableHomeostasis(false);
        props.setGlobalWorkspaceCapacity(12);
        props.setHopfieldTemperature(8.0f);
        props.setManifoldSigma(2.5f);
        props.setPhiCohesionThreshold(0.15f);
        props.setEnableSoftIdentityAnchor(false);
        props.setIdentityAnchorEta(0.0005f);
        props.setIdentityLyapunovThreshold(0.20f);
        props.setIdentityCoreSnapshotEpochs(100);

        props.setEnableEventDensity(false);
        props.setEventDensityThreshold(0.65f);
        props.setEventDensityAlphaKl(0.50f);
        props.setEventDensityBetaGradient(0.25f);
        props.setEventDensityGammaSurprise(0.25f);
        props.setEventDensitySamplingMinHz(0.5f);
        props.setEventDensitySamplingMaxHz(60.0f);

        props.setEnableBocpd(false);
        props.setBocpdHazardLambda(80.0f);
        props.setBocpdChangePointThreshold(0.75f);
        props.setBocpdSurprisalCutThreshold(2.0f);
        props.setBocpdMaxEpisodeFrames(300);
        props.setBocpdMaxRunLength(200);

        props.setEnablePrivacy(true);
        props.setPrivacyEpsilon(1.5f);
        props.setPrivacyDelta(1e-4f);
        props.setPrivacyClippingNorm(2.0f);
        props.setPrivacyAnonymizePii(false);
        props.setPrivacyPseudonymizationSalt("custom-salt");

        props.setEnableImportance(false);
        props.setImportanceWeightSurprise(0.35f);
        props.setImportanceWeightAffect(0.05f);
        props.setImportanceWeightGoal(0.35f);
        props.setImportanceWeightSocial(0.10f);
        props.setImportanceWeightNovelty(0.15f);
        props.setImportanceFlashbulbThreshold(0.90f);

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isEnableHomeostasis()).isFalse();
        assertThat(props.getGlobalWorkspaceCapacity()).isEqualTo(12);
        assertThat(props.getHopfieldTemperature()).isEqualTo(8.0f);
        assertThat(props.getManifoldSigma()).isEqualTo(2.5f);
        assertThat(props.getPhiCohesionThreshold()).isEqualTo(0.15f);
        assertThat(props.isEnableSoftIdentityAnchor()).isFalse();
        assertThat(props.getIdentityAnchorEta()).isEqualTo(0.0005f);
        assertThat(props.getIdentityLyapunovThreshold()).isEqualTo(0.20f);
        assertThat(props.getIdentityCoreSnapshotEpochs()).isEqualTo(100);

        assertThat(props.isEnableEventDensity()).isFalse();
        assertThat(props.getEventDensityThreshold()).isEqualTo(0.65f);
        assertThat(props.getEventDensityAlphaKl()).isEqualTo(0.50f);
        assertThat(props.getEventDensityBetaGradient()).isEqualTo(0.25f);
        assertThat(props.getEventDensityGammaSurprise()).isEqualTo(0.25f);
        assertThat(props.getEventDensitySamplingMinHz()).isEqualTo(0.5f);
        assertThat(props.getEventDensitySamplingMaxHz()).isEqualTo(60.0f);

        assertThat(props.isEnableBocpd()).isFalse();
        assertThat(props.getBocpdHazardLambda()).isEqualTo(80.0f);
        assertThat(props.getBocpdChangePointThreshold()).isEqualTo(0.75f);
        assertThat(props.getBocpdSurprisalCutThreshold()).isEqualTo(2.0f);
        assertThat(props.getBocpdMaxEpisodeFrames()).isEqualTo(300);
        assertThat(props.getBocpdMaxRunLength()).isEqualTo(200);

        assertThat(props.isEnablePrivacy()).isTrue();
        assertThat(props.getPrivacyEpsilon()).isEqualTo(1.5f);
        assertThat(props.getPrivacyDelta()).isEqualTo(1e-4f);
        assertThat(props.getPrivacyClippingNorm()).isEqualTo(2.0f);
        assertThat(props.isPrivacyAnonymizePii()).isFalse();
        assertThat(props.getPrivacyPseudonymizationSalt()).isEqualTo("custom-salt");

        assertThat(props.isEnableImportance()).isFalse();
        assertThat(props.getImportanceWeightSurprise()).isEqualTo(0.35f);
        assertThat(props.getImportanceWeightAffect()).isEqualTo(0.05f);
        assertThat(props.getImportanceWeightGoal()).isEqualTo(0.35f);
        assertThat(props.getImportanceWeightSocial()).isEqualTo(0.10f);
        assertThat(props.getImportanceWeightNovelty()).isEqualTo(0.15f);
        assertThat(props.getImportanceFlashbulbThreshold()).isEqualTo(0.90f);

        props.setEnableLifespan(false);
        props.setLifespanTau0(0.40f);
        props.setLifespanK(0.20f);
        props.setLifespanT0Epochs(730L);
        props.setLifespanVTarget(200000L);
        props.setLifespanGamma(1.5f);
        props.setLifespanFlashbulbProtect(false);

        assertThat(props.isEnableLifespan()).isFalse();
        assertThat(props.getLifespanTau0()).isEqualTo(0.40f);
        assertThat(props.getLifespanK()).isEqualTo(0.20f);
        assertThat(props.getLifespanT0Epochs()).isEqualTo(730L);
        assertThat(props.getLifespanVTarget()).isEqualTo(200000L);
        assertThat(props.getLifespanGamma()).isEqualTo(1.5f);
        assertThat(props.isLifespanFlashbulbProtect()).isFalse();

        // Invalid values should be ignored
        props.setGlobalWorkspaceCapacity(0);
        props.setHopfieldTemperature(-1.0f);
        props.setManifoldSigma(-0.5f);
        props.setPhiCohesionThreshold(-1.0f);
        props.setIdentityAnchorEta(-0.01f);
        props.setIdentityLyapunovThreshold(-0.5f);
        props.setIdentityCoreSnapshotEpochs(0);
        props.setEventDensityThreshold(-0.1f);
        props.setEventDensitySamplingMinHz(-1.0f);
        props.setBocpdHazardLambda(0.0f);
        props.setBocpdChangePointThreshold(1.5f);
        props.setBocpdMaxEpisodeFrames(0);
        props.setPrivacyEpsilon(-1.0f);
        props.setPrivacyDelta(1.5f);
        props.setPrivacyClippingNorm(-0.5f);
        props.setImportanceWeightSurprise(-0.1f);
        props.setImportanceFlashbulbThreshold(1.5f);
        props.setLifespanTau0(-0.1f);
        props.setLifespanK(-0.5f);
        props.setLifespanT0Epochs(0L);
        props.setLifespanVTarget(0L);
        props.setLifespanGamma(-1.0f);

        assertThat(props.getGlobalWorkspaceCapacity()).isEqualTo(12);
        assertThat(props.getHopfieldTemperature()).isEqualTo(8.0f);
        assertThat(props.getManifoldSigma()).isEqualTo(2.5f);
        assertThat(props.getPhiCohesionThreshold()).isEqualTo(0.15f);
        assertThat(props.getIdentityAnchorEta()).isEqualTo(0.0005f);
        assertThat(props.getIdentityLyapunovThreshold()).isEqualTo(0.20f);
        assertThat(props.getIdentityCoreSnapshotEpochs()).isEqualTo(100);
        assertThat(props.getEventDensityThreshold()).isEqualTo(0.65f);
        assertThat(props.getEventDensitySamplingMinHz()).isEqualTo(0.5f);
        assertThat(props.getBocpdHazardLambda()).isEqualTo(80.0f);
        assertThat(props.getBocpdChangePointThreshold()).isEqualTo(0.75f);
        assertThat(props.getBocpdMaxEpisodeFrames()).isEqualTo(300);
        assertThat(props.getPrivacyEpsilon()).isEqualTo(1.5f);
        assertThat(props.getPrivacyDelta()).isEqualTo(1e-4f);
        assertThat(props.getPrivacyClippingNorm()).isEqualTo(2.0f);
        assertThat(props.getImportanceWeightSurprise()).isEqualTo(0.35f);
        assertThat(props.getImportanceFlashbulbThreshold()).isEqualTo(0.90f);
        assertThat(props.getLifespanTau0()).isEqualTo(0.40f);
        assertThat(props.getLifespanK()).isEqualTo(0.20f);
        assertThat(props.getLifespanT0Epochs()).isEqualTo(730L);
        assertThat(props.getLifespanVTarget()).isEqualTo(200000L);
        assertThat(props.getLifespanGamma()).isEqualTo(1.5f);
    }

    @Test
    void aismeProperties_fromSpectorProperties() {
        SpectorProperties props = SpectorProperties.builder()
                .override("spector.memory.aisme.enabled", "true")
                .override("spector.memory.aisme.homeostasis.enabled", "false")
                .override("spector.memory.aisme.global-workspace.capacity", "5")
                .override("spector.memory.aisme.hopfield.temperature", "2.0")
                .override("spector.memory.aisme.manifold.sigma", "0.8")
                .override("spector.memory.aisme.phi.cohesion-threshold", "0.1")
                .override("spector.memory.aisme.soft-identity-anchor.enabled", "false")
                .override("spector.memory.aisme.identity-anchor.eta", "0.0002")
                .override("spector.memory.aisme.identity-lyapunov.threshold", "0.25")
                .override("spector.memory.aisme.identity-core-snapshot.epochs", "75")
                .override("spector.memory.aisme.event-density.enabled", "false")
                .override("spector.memory.aisme.event-density.threshold", "0.70")
                .override("spector.memory.aisme.event-density.alpha-kl", "0.35")
                .override("spector.memory.aisme.event-density.beta-gradient", "0.35")
                .override("spector.memory.aisme.event-density.gamma-surprise", "0.30")
                .override("spector.memory.aisme.event-density.sampling-min-hz", "0.2")
                .override("spector.memory.aisme.event-density.sampling-max-hz", "20.0")
                .override("spector.memory.aisme.bocpd.enabled", "false")
                .override("spector.memory.aisme.bocpd.hazard-lambda", "120.0")
                .override("spector.memory.aisme.bocpd.change-point-threshold", "0.70")
                .override("spector.memory.aisme.bocpd.surprisal-cut-threshold", "1.80")
                .override("spector.memory.aisme.bocpd.max-episode-frames", "250")
                .override("spector.memory.aisme.bocpd.max-run-length", "180")
                .override("spector.memory.aisme.privacy.enabled", "true")
                .override("spector.memory.aisme.privacy.epsilon", "3.0")
                .override("spector.memory.aisme.privacy.delta", "0.00002")
                .override("spector.memory.aisme.privacy.clipping-norm", "1.5")
                .override("spector.memory.aisme.privacy.anonymize-pii", "false")
                .override("spector.memory.aisme.privacy.pseudonymization-salt", "test-salt")
                .override("spector.memory.aisme.importance.enabled", "false")
                .override("spector.memory.aisme.importance.weight-surprise", "0.30")
                .override("spector.memory.aisme.importance.weight-affect", "0.10")
                .override("spector.memory.aisme.importance.weight-goal", "0.30")
                .override("spector.memory.aisme.importance.weight-social", "0.15")
                .override("spector.memory.aisme.importance.weight-novelty", "0.15")
                .override("spector.memory.aisme.importance.flashbulb-threshold", "0.92")
                .override("spector.memory.aisme.lifespan.enabled", "false")
                .override("spector.memory.aisme.lifespan.tau-0", "0.35")
                .override("spector.memory.aisme.lifespan.k", "0.18")
                .override("spector.memory.aisme.lifespan.t0-epochs", "500")
                .override("spector.memory.aisme.lifespan.v-target", "150000")
                .override("spector.memory.aisme.lifespan.gamma", "1.3")
                .override("spector.memory.aisme.lifespan.flashbulb-protect", "false")
                .build();

        AismeProperties aisme = SpectorConfigFactory.aismeProperties(props);

        assertThat(aisme.isEnabled()).isTrue();
        assertThat(aisme.isEnableHomeostasis()).isFalse();
        assertThat(aisme.isEnableFreeEnergy()).isTrue();
        assertThat(aisme.getGlobalWorkspaceCapacity()).isEqualTo(5);
        assertThat(aisme.getHopfieldTemperature()).isEqualTo(2.0f);
        assertThat(aisme.getManifoldSigma()).isEqualTo(0.8f);
        assertThat(aisme.getPhiCohesionThreshold()).isEqualTo(0.1f);
        assertThat(aisme.isEnableSoftIdentityAnchor()).isFalse();
        assertThat(aisme.getIdentityAnchorEta()).isEqualTo(0.0002f);
        assertThat(aisme.getIdentityLyapunovThreshold()).isEqualTo(0.25f);
        assertThat(aisme.getIdentityCoreSnapshotEpochs()).isEqualTo(75);

        assertThat(aisme.isEnableEventDensity()).isFalse();
        assertThat(aisme.getEventDensityThreshold()).isEqualTo(0.70f);
        assertThat(aisme.getEventDensityAlphaKl()).isEqualTo(0.35f);
        assertThat(aisme.getEventDensityBetaGradient()).isEqualTo(0.35f);
        assertThat(aisme.getEventDensityGammaSurprise()).isEqualTo(0.30f);
        assertThat(aisme.getEventDensitySamplingMinHz()).isEqualTo(0.2f);
        assertThat(aisme.getEventDensitySamplingMaxHz()).isEqualTo(20.0f);

        assertThat(aisme.isEnableBocpd()).isFalse();
        assertThat(aisme.getBocpdHazardLambda()).isEqualTo(120.0f);
        assertThat(aisme.getBocpdChangePointThreshold()).isEqualTo(0.70f);
        assertThat(aisme.getBocpdSurprisalCutThreshold()).isEqualTo(1.80f);
        assertThat(aisme.getBocpdMaxEpisodeFrames()).isEqualTo(250);
        assertThat(aisme.getBocpdMaxRunLength()).isEqualTo(180);

        assertThat(aisme.isEnablePrivacy()).isTrue();
        assertThat(aisme.getPrivacyEpsilon()).isEqualTo(3.0f);
        assertThat(aisme.getPrivacyDelta()).isEqualTo(0.00002f);
        assertThat(aisme.getPrivacyClippingNorm()).isEqualTo(1.5f);
        assertThat(aisme.isPrivacyAnonymizePii()).isFalse();
        assertThat(aisme.getPrivacyPseudonymizationSalt()).isEqualTo("test-salt");

        assertThat(aisme.isEnableImportance()).isFalse();
        assertThat(aisme.getImportanceWeightSurprise()).isEqualTo(0.30f);
        assertThat(aisme.getImportanceWeightAffect()).isEqualTo(0.10f);
        assertThat(aisme.getImportanceWeightGoal()).isEqualTo(0.30f);
        assertThat(aisme.getImportanceWeightSocial()).isEqualTo(0.15f);
        assertThat(aisme.getImportanceWeightNovelty()).isEqualTo(0.15f);
        assertThat(aisme.getImportanceFlashbulbThreshold()).isEqualTo(0.92f);

        assertThat(aisme.isEnableLifespan()).isFalse();
        assertThat(aisme.getLifespanTau0()).isEqualTo(0.35f);
        assertThat(aisme.getLifespanK()).isEqualTo(0.18f);
        assertThat(aisme.getLifespanT0Epochs()).isEqualTo(500L);
        assertThat(aisme.getLifespanVTarget()).isEqualTo(150000L);
        assertThat(aisme.getLifespanGamma()).isEqualTo(1.3f);
        assertThat(aisme.isLifespanFlashbulbProtect()).isFalse();

        var memory = SpectorConfigFactory.memoryProperties(props);
        assertThat(memory.getAisme().isEnabled()).isTrue();
        assertThat(memory.getAisme().getGlobalWorkspaceCapacity()).isEqualTo(5);
        assertThat(memory.getAisme().getGlobalWorkspaceCapacity()).isEqualTo(5);
    }
}
