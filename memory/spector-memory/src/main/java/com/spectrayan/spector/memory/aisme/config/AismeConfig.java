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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Immutable configuration encapsulating Active Inference Self-Model Engine (AISME) settings.
 *
 * <h3>Biological Analog: Multi-Layered Neurocognitive Architecture Governance</h3>
 * <p>Controls the activation and hyperparameters of homeostatic affective regulation,
 * variational free-energy minimization, continuous Hopfield attractor dynamics,
 * Riemannian cognitive manifolds, predictive coding, consciousness continuity,
 * and the Global Workspace conscious access gateway.</p>
 */
public record AismeConfig(
        boolean enabled,
        boolean enableHomeostasis,
        boolean enableFreeEnergy,
        boolean enableHopfield,
        boolean enableManifold,
        boolean enablePredictiveCoding,
        boolean enableConsciousnessContinuity,
        boolean enableGlobalWorkspace,
        int globalWorkspaceCapacity,
        float hopfieldTemperature,
        float manifoldSigma,
        float phiCohesionThreshold,
        boolean enableDmnSpontaneous,
        int dmnIdleIntervalSeconds,
        boolean enableLongitudinalContinuity,
        int longitudinalSnapshotIntervalMinutes,
        boolean enableExpectedFreeEnergy,
        float efePolicyPrecision,
        float efeEpistemicWeight,
        float efePragmaticWeight,
        float efeSoulWeightAgent,
        float efeSoulWeightUser,
        float efeSoulWeightTenant,
        float efeSoulWeightOrgUnit
) {

    /**
     * Compact constructor with validation.
     */
    public AismeConfig {
        if (globalWorkspaceCapacity < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "globalWorkspaceCapacity must be at least 1");
        }
        if (Float.isNaN(hopfieldTemperature) || hopfieldTemperature <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "hopfieldTemperature must be positive");
        }
        if (Float.isNaN(manifoldSigma) || manifoldSigma <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "manifoldSigma must be positive");
        }
        if (Float.isNaN(phiCohesionThreshold) || phiCohesionThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "phiCohesionThreshold must be non-negative");
        }
        if (dmnIdleIntervalSeconds < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dmnIdleIntervalSeconds must be at least 1");
        }
        if (longitudinalSnapshotIntervalMinutes < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "longitudinalSnapshotIntervalMinutes must be at least 1");
        }
        if (Float.isNaN(efePolicyPrecision) || efePolicyPrecision <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efePolicyPrecision must be positive");
        }
        if (Float.isNaN(efeEpistemicWeight) || efeEpistemicWeight < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efeEpistemicWeight must be non-negative");
        }
        if (Float.isNaN(efePragmaticWeight) || efePragmaticWeight < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efePragmaticWeight must be non-negative");
        }
        if (Float.isNaN(efeSoulWeightAgent) || efeSoulWeightAgent < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efeSoulWeightAgent must be non-negative");
        }
        if (Float.isNaN(efeSoulWeightUser) || efeSoulWeightUser < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efeSoulWeightUser must be non-negative");
        }
        if (Float.isNaN(efeSoulWeightTenant) || efeSoulWeightTenant < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efeSoulWeightTenant must be non-negative");
        }
        if (Float.isNaN(efeSoulWeightOrgUnit) || efeSoulWeightOrgUnit < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "efeSoulWeightOrgUnit must be non-negative");
        }
    }

    /**
     * Returns a disabled AISME configuration with zero runtime overhead (legacy mode).
     *
     * @return disabled AismeConfig
     */
    public static AismeConfig disabled() {
        return new AismeConfig(
                false, false, false, false, false, false, false, false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_DMN_IDLE_SECONDS,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_POLICY_PRECISION,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_EPISTEMIC_WEIGHT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_PRAGMATIC_WEIGHT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_USER,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT
        );
    }

    /**
     * Returns a fully enabled AISME configuration with production default hyperparameters.
     *
     * @return fully enabled default AismeConfig
     */
    public static AismeConfig defaultConfig() {
        return new AismeConfig(
                true,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_HOMEOSTASIS_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_FREE_ENERGY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_HOPFIELD_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_MANIFOLD_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PREDICTIVE_CODING_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSCIOUSNESS_CONTINUITY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_DMN_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_DMN_IDLE_SECONDS,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_LONGITUDINAL_CONTINUITY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_POLICY_PRECISION,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_EPISTEMIC_WEIGHT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_PRAGMATIC_WEIGHT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_USER,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT
        );
    }

    /**
     * Creates an AismeConfig record from a system-level {@link com.spectrayan.spector.config.properties.AismeProperties} POJO.
     *
     * @param props the AismeProperties instance (nullable)
     * @return configured AismeConfig (or disabled if null or disabled)
     */
    public static AismeConfig fromProperties(com.spectrayan.spector.config.properties.AismeProperties props) {
        if (props == null || !props.isEnabled()) {
            return disabled();
        }
        return new AismeConfig(
                props.isEnabled(),
                props.isEnableHomeostasis(),
                props.isEnableFreeEnergy(),
                props.isEnableHopfield(),
                props.isEnableManifold(),
                props.isEnablePredictiveCoding(),
                props.isEnableConsciousnessContinuity(),
                props.isEnableGlobalWorkspace(),
                props.getGlobalWorkspaceCapacity(),
                props.getHopfieldTemperature(),
                props.getManifoldSigma(),
                props.getPhiCohesionThreshold(),
                props.isEnableDmnSpontaneous(),
                props.getDmnIdleIntervalSeconds(),
                props.isEnableLongitudinalContinuity(),
                props.getLongitudinalSnapshotIntervalMinutes(),
                props.enableExpectedFreeEnergy(),
                props.efePolicyPrecision(),
                props.efeEpistemicWeight(),
                props.efePragmaticWeight(),
                props.efeSoulWeightAgent(),
                props.efeSoulWeightUser(),
                props.efeSoulWeightTenant(),
                props.efeSoulWeightOrgUnit()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link AismeConfig}.
     */
    public static final class Builder {
        private boolean enabled = true;
        private boolean enableHomeostasis = true;
        private boolean enableFreeEnergy = true;
        private boolean enableHopfield = true;
        private boolean enableManifold = true;
        private boolean enablePredictiveCoding = true;
        private boolean enableConsciousnessContinuity = true;
        private boolean enableGlobalWorkspace = true;
        private int globalWorkspaceCapacity = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_GLOBAL_WORKSPACE_CAPACITY;
        private float hopfieldTemperature = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_HOPFIELD_TEMPERATURE;
        private float manifoldSigma = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_MANIFOLD_SIGMA;
        private float phiCohesionThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PHI_COHESION_THRESHOLD;
        private boolean enableDmnSpontaneous = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_DMN_ENABLED;
        private int dmnIdleIntervalSeconds = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_DMN_IDLE_SECONDS;
        private boolean enableLongitudinalContinuity = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_LONGITUDINAL_CONTINUITY_ENABLED;
        private int longitudinalSnapshotIntervalMinutes = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_LONGITUDINAL_SNAPSHOT_INTERVAL_MINUTES;
        private boolean enableExpectedFreeEnergy = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_ENABLED;
        private float efePolicyPrecision = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_POLICY_PRECISION;
        private float efeEpistemicWeight = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_EPISTEMIC_WEIGHT;
        private float efePragmaticWeight = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_PRAGMATIC_WEIGHT;
        private float efeSoulWeightAgent = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT;
        private float efeSoulWeightUser = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_USER;
        private float efeSoulWeightTenant = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT;
        private float efeSoulWeightOrgUnit = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder enableHomeostasis(boolean enable) { this.enableHomeostasis = enable; return this; }
        public Builder enableFreeEnergy(boolean enable) { this.enableFreeEnergy = enable; return this; }
        public Builder enableHopfield(boolean enable) { this.enableHopfield = enable; return this; }
        public Builder enableManifold(boolean enable) { this.enableManifold = enable; return this; }
        public Builder enablePredictiveCoding(boolean enable) { this.enablePredictiveCoding = enable; return this; }
        public Builder enableConsciousnessContinuity(boolean enable) { this.enableConsciousnessContinuity = enable; return this; }
        public Builder enableGlobalWorkspace(boolean enable) { this.enableGlobalWorkspace = enable; return this; }
        public Builder globalWorkspaceCapacity(int cap) { this.globalWorkspaceCapacity = cap; return this; }
        public Builder hopfieldTemperature(float temp) { this.hopfieldTemperature = temp; return this; }
        public Builder manifoldSigma(float sigma) { this.manifoldSigma = sigma; return this; }
        public Builder phiCohesionThreshold(float threshold) { this.phiCohesionThreshold = threshold; return this; }
        public Builder enableDmnSpontaneous(boolean enable) { this.enableDmnSpontaneous = enable; return this; }
        public Builder dmnIdleIntervalSeconds(int sec) { this.dmnIdleIntervalSeconds = sec; return this; }
        public Builder enableLongitudinalContinuity(boolean enable) { this.enableLongitudinalContinuity = enable; return this; }
        public Builder longitudinalSnapshotIntervalMinutes(int min) { this.longitudinalSnapshotIntervalMinutes = min; return this; }
        public Builder enableExpectedFreeEnergy(boolean enable) { this.enableExpectedFreeEnergy = enable; return this; }
        public Builder efePolicyPrecision(float precision) { this.efePolicyPrecision = precision; return this; }
        public Builder efeEpistemicWeight(float weight) { this.efeEpistemicWeight = weight; return this; }
        public Builder efePragmaticWeight(float weight) { this.efePragmaticWeight = weight; return this; }
        public Builder efeSoulWeightAgent(float weight) { this.efeSoulWeightAgent = weight; return this; }
        public Builder efeSoulWeightUser(float weight) { this.efeSoulWeightUser = weight; return this; }
        public Builder efeSoulWeightTenant(float weight) { this.efeSoulWeightTenant = weight; return this; }
        public Builder efeSoulWeightOrgUnit(float weight) { this.efeSoulWeightOrgUnit = weight; return this; }

        public AismeConfig build() {
            return new AismeConfig(
                    enabled, enableHomeostasis, enableFreeEnergy, enableHopfield,
                    enableManifold, enablePredictiveCoding, enableConsciousnessContinuity,
                    enableGlobalWorkspace, globalWorkspaceCapacity, hopfieldTemperature,
                    manifoldSigma, phiCohesionThreshold, enableDmnSpontaneous,
                    dmnIdleIntervalSeconds, enableLongitudinalContinuity,
                    longitudinalSnapshotIntervalMinutes, enableExpectedFreeEnergy,
                    efePolicyPrecision, efeEpistemicWeight, efePragmaticWeight,
                    efeSoulWeightAgent, efeSoulWeightUser, efeSoulWeightTenant,
                    efeSoulWeightOrgUnit
            );
        }
    }
}
