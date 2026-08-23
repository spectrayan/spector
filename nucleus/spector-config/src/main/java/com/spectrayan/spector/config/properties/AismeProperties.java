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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;

/**
 * Active Inference Self-Model Engine (AISME) configuration properties.
 *
 * <h3>Biological Analog: Multi-Layered Neurocognitive Architecture Governance</h3>
 * <p>Controls the activation and hyperparameters of homeostatic affective regulation,
 * variational free-energy minimization, continuous Hopfield attractor dynamics,
 * Riemannian cognitive manifolds, predictive coding, consciousness continuity,
 * and the Global Workspace conscious access gateway.</p>
 */
public class AismeProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = DEFAULT_MEMORY_AISME_ENABLED;
    private boolean enableHomeostasis = DEFAULT_MEMORY_AISME_HOMEOSTASIS_ENABLED;
    private boolean enableFreeEnergy = DEFAULT_MEMORY_AISME_FREE_ENERGY_ENABLED;
    private boolean enableHopfield = DEFAULT_MEMORY_AISME_HOPFIELD_ENABLED;
    private boolean enableManifold = DEFAULT_MEMORY_AISME_MANIFOLD_ENABLED;
    private boolean enablePredictiveCoding = DEFAULT_MEMORY_AISME_PREDICTIVE_CODING_ENABLED;
    private boolean enableConsciousnessContinuity = DEFAULT_MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED;
    private boolean enableGlobalWorkspace = DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED;

    private int globalWorkspaceCapacity = DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY;
    private float hopfieldTemperature = DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE;
    private float manifoldSigma = DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA;
    private float phiCohesionThreshold = DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD;
    private boolean enableDmnSpontaneous = DEFAULT_MEMORY_AISME_DMN_ENABLED;
    private int dmnIdleIntervalSeconds = DEFAULT_MEMORY_AISME_DMN_IDLE_SECONDS;
    private boolean enableLongitudinalContinuity = DEFAULT_MEMORY_AISME_LONGITUDINAL_CONTINUITY_ENABLED;
    private int longitudinalSnapshotIntervalMinutes = DEFAULT_MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES;

    public AismeProperties() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnableHomeostasis() {
        return enableHomeostasis;
    }

    public void setEnableHomeostasis(boolean enableHomeostasis) {
        this.enableHomeostasis = enableHomeostasis;
    }

    public boolean isEnableFreeEnergy() {
        return enableFreeEnergy;
    }

    public void setEnableFreeEnergy(boolean enableFreeEnergy) {
        this.enableFreeEnergy = enableFreeEnergy;
    }

    public boolean isEnableHopfield() {
        return enableHopfield;
    }

    public void setEnableHopfield(boolean enableHopfield) {
        this.enableHopfield = enableHopfield;
    }

    public boolean isEnableManifold() {
        return enableManifold;
    }

    public void setEnableManifold(boolean enableManifold) {
        this.enableManifold = enableManifold;
    }

    public boolean isEnablePredictiveCoding() {
        return enablePredictiveCoding;
    }

    public void setEnablePredictiveCoding(boolean enablePredictiveCoding) {
        this.enablePredictiveCoding = enablePredictiveCoding;
    }

    public boolean isEnableConsciousnessContinuity() {
        return enableConsciousnessContinuity;
    }

    public void setEnableConsciousnessContinuity(boolean enableConsciousnessContinuity) {
        this.enableConsciousnessContinuity = enableConsciousnessContinuity;
    }

    public boolean isEnableGlobalWorkspace() {
        return enableGlobalWorkspace;
    }

    public void setEnableGlobalWorkspace(boolean enableGlobalWorkspace) {
        this.enableGlobalWorkspace = enableGlobalWorkspace;
    }

    public int getGlobalWorkspaceCapacity() {
        return globalWorkspaceCapacity;
    }

    public void setGlobalWorkspaceCapacity(int globalWorkspaceCapacity) {
        if (globalWorkspaceCapacity >= 1) {
            this.globalWorkspaceCapacity = globalWorkspaceCapacity;
        }
    }

    public float getHopfieldTemperature() {
        return hopfieldTemperature;
    }

    public void setHopfieldTemperature(float hopfieldTemperature) {
        if (!Float.isNaN(hopfieldTemperature) && hopfieldTemperature > 0.0f) {
            this.hopfieldTemperature = hopfieldTemperature;
        }
    }

    public float getManifoldSigma() {
        return manifoldSigma;
    }

    public void setManifoldSigma(float manifoldSigma) {
        if (!Float.isNaN(manifoldSigma) && manifoldSigma > 0.0f) {
            this.manifoldSigma = manifoldSigma;
        }
    }

    public float getPhiCohesionThreshold() {
        return phiCohesionThreshold;
    }

    public void setPhiCohesionThreshold(float phiCohesionThreshold) {
        if (!Float.isNaN(phiCohesionThreshold) && phiCohesionThreshold >= 0.0f) {
            this.phiCohesionThreshold = phiCohesionThreshold;
        }
    }

    public boolean isEnableDmnSpontaneous() {
        return enableDmnSpontaneous;
    }

    public void setEnableDmnSpontaneous(boolean enableDmnSpontaneous) {
        this.enableDmnSpontaneous = enableDmnSpontaneous;
    }

    public int getDmnIdleIntervalSeconds() {
        return dmnIdleIntervalSeconds;
    }

    public void setDmnIdleIntervalSeconds(int dmnIdleIntervalSeconds) {
        if (dmnIdleIntervalSeconds > 0) {
            this.dmnIdleIntervalSeconds = dmnIdleIntervalSeconds;
        }
    }

    public boolean isEnableLongitudinalContinuity() {
        return enableLongitudinalContinuity;
    }

    public void setEnableLongitudinalContinuity(boolean enableLongitudinalContinuity) {
        this.enableLongitudinalContinuity = enableLongitudinalContinuity;
    }

    public int getLongitudinalSnapshotIntervalMinutes() {
        return longitudinalSnapshotIntervalMinutes;
    }

    public void setLongitudinalSnapshotIntervalMinutes(int longitudinalSnapshotIntervalMinutes) {
        if (longitudinalSnapshotIntervalMinutes > 0) {
            this.longitudinalSnapshotIntervalMinutes = longitudinalSnapshotIntervalMinutes;
        }
    }

    // ── Fluent Accessors ──

    public boolean enabled() { return isEnabled(); }
    public boolean enableHomeostasis() { return isEnableHomeostasis(); }
    public boolean enableFreeEnergy() { return isEnableFreeEnergy(); }
    public boolean enableHopfield() { return isEnableHopfield(); }
    public boolean enableManifold() { return isEnableManifold(); }
    public boolean enablePredictiveCoding() { return isEnablePredictiveCoding(); }
    public boolean enableConsciousnessContinuity() { return isEnableConsciousnessContinuity(); }
    public boolean enableGlobalWorkspace() { return isEnableGlobalWorkspace(); }
    public int globalWorkspaceCapacity() { return getGlobalWorkspaceCapacity(); }
    public float hopfieldTemperature() { return getHopfieldTemperature(); }
    public float manifoldSigma() { return getManifoldSigma(); }
    public float phiCohesionThreshold() { return getPhiCohesionThreshold(); }
    public boolean enableDmnSpontaneous() { return isEnableDmnSpontaneous(); }
    public int dmnIdleIntervalSeconds() { return getDmnIdleIntervalSeconds(); }
    public boolean enableLongitudinalContinuity() { return isEnableLongitudinalContinuity(); }
    public int longitudinalSnapshotIntervalMinutes() { return getLongitudinalSnapshotIntervalMinutes(); }
}
