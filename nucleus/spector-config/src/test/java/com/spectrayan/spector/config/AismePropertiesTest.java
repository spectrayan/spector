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

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isEnableHomeostasis()).isFalse();
        assertThat(props.getGlobalWorkspaceCapacity()).isEqualTo(12);
        assertThat(props.getHopfieldTemperature()).isEqualTo(8.0f);
        assertThat(props.getManifoldSigma()).isEqualTo(2.5f);
        assertThat(props.getPhiCohesionThreshold()).isEqualTo(0.15f);

        // Invalid values should be ignored
        props.setGlobalWorkspaceCapacity(0);
        props.setHopfieldTemperature(-1.0f);
        props.setManifoldSigma(-0.5f);
        props.setPhiCohesionThreshold(-1.0f);

        assertThat(props.getGlobalWorkspaceCapacity()).isEqualTo(12);
        assertThat(props.getHopfieldTemperature()).isEqualTo(8.0f);
        assertThat(props.getManifoldSigma()).isEqualTo(2.5f);
        assertThat(props.getPhiCohesionThreshold()).isEqualTo(0.15f);
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
                .build();

        AismeProperties aisme = SpectorConfigFactory.aismeProperties(props);

        assertThat(aisme.isEnabled()).isTrue();
        assertThat(aisme.isEnableHomeostasis()).isFalse();
        assertThat(aisme.isEnableFreeEnergy()).isTrue();
        assertThat(aisme.getGlobalWorkspaceCapacity()).isEqualTo(5);
        assertThat(aisme.getHopfieldTemperature()).isEqualTo(2.0f);
        assertThat(aisme.getManifoldSigma()).isEqualTo(0.8f);
        assertThat(aisme.getPhiCohesionThreshold()).isEqualTo(0.1f);

        var memory = SpectorConfigFactory.memoryProperties(props);
        assertThat(memory.getAisme().isEnabled()).isTrue();
        assertThat(memory.getAisme().getGlobalWorkspaceCapacity()).isEqualTo(5);
    }
}
