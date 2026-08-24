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
 * the Global Workspace conscious access gateway, Soft Identity Anchor, Event Density Gating,
 * and Bayesian Online Change-Point & Surprisal Episode Boundary Segmentation.</p>
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
        float efeSoulWeightOrgUnit,
        boolean constructivePersistenceEnabled,
        float constructivePersistenceThreshold,
        boolean backgroundDecayEnabled,
        float backgroundDecayFactor,
        int backgroundDecayIntervalSeconds,
        boolean enableSoftIdentityAnchor,
        float identityAnchorEta,
        float identityLyapunovThreshold,
        int identityCoreSnapshotEpochs,
        boolean enableEventDensity,
        float eventDensityThreshold,
        float eventDensityAlphaKl,
        float eventDensityBetaGradient,
        float eventDensityGammaSurprise,
        float eventDensitySamplingMinHz,
        float eventDensitySamplingMaxHz,
        boolean enableBocpd,
        float bocpdHazardLambda,
        float bocpdChangePointThreshold,
        float bocpdSurprisalCutThreshold,
        int bocpdMaxEpisodeFrames,
        int bocpdMaxRunLength,
        boolean enablePrivacy,
        float privacyEpsilon,
        float privacyDelta,
        float privacyClippingNorm,
        boolean privacyAnonymizePii,
        String privacyPseudonymizationSalt,
        boolean enableImportance,
        float importanceWeightSurprise,
        float importanceWeightAffect,
        float importanceWeightGoal,
        float importanceWeightSocial,
        float importanceWeightNovelty,
        float importanceFlashbulbThreshold
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
        if (Float.isNaN(constructivePersistenceThreshold) || constructivePersistenceThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "constructivePersistenceThreshold must be non-negative");
        }
        if (Float.isNaN(backgroundDecayFactor) || backgroundDecayFactor < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "backgroundDecayFactor must be non-negative");
        }
        if (backgroundDecayIntervalSeconds < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "backgroundDecayIntervalSeconds must be at least 1");
        }
        if (Float.isNaN(identityAnchorEta) || identityAnchorEta < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "identityAnchorEta must be non-negative");
        }
        if (Float.isNaN(identityLyapunovThreshold) || identityLyapunovThreshold <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "identityLyapunovThreshold must be positive");
        }
        if (identityCoreSnapshotEpochs < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "identityCoreSnapshotEpochs must be at least 1");
        }
        if (Float.isNaN(eventDensityThreshold) || eventDensityThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensityThreshold must be non-negative");
        }
        if (Float.isNaN(eventDensityAlphaKl) || eventDensityAlphaKl < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensityAlphaKl must be non-negative");
        }
        if (Float.isNaN(eventDensityBetaGradient) || eventDensityBetaGradient < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensityBetaGradient must be non-negative");
        }
        if (Float.isNaN(eventDensityGammaSurprise) || eventDensityGammaSurprise < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensityGammaSurprise must be non-negative");
        }
        if (Float.isNaN(eventDensitySamplingMinHz) || eventDensitySamplingMinHz <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensitySamplingMinHz must be positive");
        }
        if (Float.isNaN(eventDensitySamplingMaxHz) || eventDensitySamplingMaxHz < eventDensitySamplingMinHz) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "eventDensitySamplingMaxHz must be >= eventDensitySamplingMinHz");
        }
        if (Float.isNaN(bocpdHazardLambda) || bocpdHazardLambda <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "bocpdHazardLambda must be positive");
        }
        if (Float.isNaN(bocpdChangePointThreshold) || bocpdChangePointThreshold < 0.0f || bocpdChangePointThreshold > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "bocpdChangePointThreshold must be in [0, 1]");
        }
        if (Float.isNaN(bocpdSurprisalCutThreshold) || bocpdSurprisalCutThreshold < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "bocpdSurprisalCutThreshold must be non-negative");
        }
        if (bocpdMaxEpisodeFrames < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "bocpdMaxEpisodeFrames must be at least 1");
        }
        if (bocpdMaxRunLength < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "bocpdMaxRunLength must be at least 1");
        }
        if (Float.isNaN(privacyEpsilon) || privacyEpsilon <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "privacyEpsilon must be positive");
        }
        if (Float.isNaN(privacyDelta) || privacyDelta <= 0.0f || privacyDelta >= 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "privacyDelta must be in (0, 1)");
        }
        if (Float.isNaN(privacyClippingNorm) || privacyClippingNorm <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "privacyClippingNorm must be positive");
        }
        if (privacyPseudonymizationSalt == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "privacyPseudonymizationSalt must not be null");
        }
        if (Float.isNaN(importanceWeightSurprise) || importanceWeightSurprise < 0.0f
                || Float.isNaN(importanceWeightAffect) || importanceWeightAffect < 0.0f
                || Float.isNaN(importanceWeightGoal) || importanceWeightGoal < 0.0f
                || Float.isNaN(importanceWeightSocial) || importanceWeightSocial < 0.0f
                || Float.isNaN(importanceWeightNovelty) || importanceWeightNovelty < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "importance weights must be non-negative");
        }
        if (importanceWeightSurprise + importanceWeightAffect + importanceWeightGoal + importanceWeightSocial + importanceWeightNovelty <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "sum of importance weights must be positive");
        }
        if (Float.isNaN(importanceFlashbulbThreshold) || importanceFlashbulbThreshold < 0.0f || importanceFlashbulbThreshold > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "importanceFlashbulbThreshold must be in [0, 1]");
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
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_FACTOR,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_EPSILON,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_DELTA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT,
                false,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD
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
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_FACTOR,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_EPSILON,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_DELTA,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_ENABLED,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY,
                SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD
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
                props.efeSoulWeightOrgUnit(),
                props.constructivePersistenceEnabled(),
                props.constructivePersistenceThreshold(),
                props.backgroundDecayEnabled(),
                props.backgroundDecayFactor(),
                props.backgroundDecayIntervalSeconds(),
                props.isEnableSoftIdentityAnchor(),
                props.getIdentityAnchorEta(),
                props.getIdentityLyapunovThreshold(),
                props.getIdentityCoreSnapshotEpochs(),
                props.isEnableEventDensity(),
                props.getEventDensityThreshold(),
                props.getEventDensityAlphaKl(),
                props.getEventDensityBetaGradient(),
                props.getEventDensityGammaSurprise(),
                props.getEventDensitySamplingMinHz(),
                props.getEventDensitySamplingMaxHz(),
                props.isEnableBocpd(),
                props.getBocpdHazardLambda(),
                props.getBocpdChangePointThreshold(),
                props.getBocpdSurprisalCutThreshold(),
                props.getBocpdMaxEpisodeFrames(),
                props.getBocpdMaxRunLength(),
                props.isEnablePrivacy(),
                props.getPrivacyEpsilon(),
                props.getPrivacyDelta(),
                props.getPrivacyClippingNorm(),
                props.isPrivacyAnonymizePii(),
                props.getPrivacyPseudonymizationSalt(),
                props.isEnableImportance(),
                props.getImportanceWeightSurprise(),
                props.getImportanceWeightAffect(),
                props.getImportanceWeightGoal(),
                props.getImportanceWeightSocial(),
                props.getImportanceWeightNovelty(),
                props.getImportanceFlashbulbThreshold()
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
        private boolean constructivePersistenceEnabled = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_ENABLED;
        private float constructivePersistenceThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD;
        private boolean backgroundDecayEnabled = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_ENABLED;
        private float backgroundDecayFactor = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_FACTOR;
        private int backgroundDecayIntervalSeconds = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS;
        private boolean enableSoftIdentityAnchor = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED;
        private float identityAnchorEta = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA;
        private float identityLyapunovThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD;
        private int identityCoreSnapshotEpochs = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS;
        private boolean enableEventDensity = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_ENABLED;
        private float eventDensityThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD;
        private float eventDensityAlphaKl = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL;
        private float eventDensityBetaGradient = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT;
        private float eventDensityGammaSurprise = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE;
        private float eventDensitySamplingMinHz = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ;
        private float eventDensitySamplingMaxHz = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ;
        private boolean enableBocpd = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_ENABLED;
        private float bocpdHazardLambda = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA;
        private float bocpdChangePointThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD;
        private float bocpdSurprisalCutThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD;
        private int bocpdMaxEpisodeFrames = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES;
        private int bocpdMaxRunLength = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH;
        private boolean enablePrivacy = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_ENABLED;
        private float privacyEpsilon = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_EPSILON;
        private float privacyDelta = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_DELTA;
        private float privacyClippingNorm = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM;
        private boolean privacyAnonymizePii = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII;
        private String privacyPseudonymizationSalt = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT;
        private boolean enableImportance = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_ENABLED;
        private float importanceWeightSurprise = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE;
        private float importanceWeightAffect = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT;
        private float importanceWeightGoal = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL;
        private float importanceWeightSocial = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL;
        private float importanceWeightNovelty = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY;
        private float importanceFlashbulbThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD;

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
        public Builder constructivePersistenceEnabled(boolean enable) { this.constructivePersistenceEnabled = enable; return this; }
        public Builder constructivePersistenceThreshold(float threshold) { this.constructivePersistenceThreshold = threshold; return this; }
        public Builder backgroundDecayEnabled(boolean enable) { this.backgroundDecayEnabled = enable; return this; }
        public Builder backgroundDecayFactor(float factor) { this.backgroundDecayFactor = factor; return this; }
        public Builder backgroundDecayIntervalSeconds(int sec) { this.backgroundDecayIntervalSeconds = sec; return this; }
        public Builder enableSoftIdentityAnchor(boolean enable) { this.enableSoftIdentityAnchor = enable; return this; }
        public Builder identityAnchorEta(float eta) { this.identityAnchorEta = eta; return this; }
        public Builder identityLyapunovThreshold(float threshold) { this.identityLyapunovThreshold = threshold; return this; }
        public Builder identityCoreSnapshotEpochs(int epochs) { this.identityCoreSnapshotEpochs = epochs; return this; }
        public Builder enableEventDensity(boolean enable) { this.enableEventDensity = enable; return this; }
        public Builder eventDensityThreshold(float threshold) { this.eventDensityThreshold = threshold; return this; }
        public Builder eventDensityAlphaKl(float alpha) { this.eventDensityAlphaKl = alpha; return this; }
        public Builder eventDensityBetaGradient(float beta) { this.eventDensityBetaGradient = beta; return this; }
        public Builder eventDensityGammaSurprise(float gamma) { this.eventDensityGammaSurprise = gamma; return this; }
        public Builder eventDensitySamplingMinHz(float minHz) { this.eventDensitySamplingMinHz = minHz; return this; }
        public Builder eventDensitySamplingMaxHz(float maxHz) { this.eventDensitySamplingMaxHz = maxHz; return this; }
        public Builder enableBocpd(boolean enable) { this.enableBocpd = enable; return this; }
        public Builder bocpdHazardLambda(float lambda) { this.bocpdHazardLambda = lambda; return this; }
        public Builder bocpdChangePointThreshold(float threshold) { this.bocpdChangePointThreshold = threshold; return this; }
        public Builder bocpdSurprisalCutThreshold(float threshold) { this.bocpdSurprisalCutThreshold = threshold; return this; }
        public Builder bocpdMaxEpisodeFrames(int frames) { this.bocpdMaxEpisodeFrames = frames; return this; }
        public Builder bocpdMaxRunLength(int runLength) { this.bocpdMaxRunLength = runLength; return this; }
        public Builder enablePrivacy(boolean enable) { this.enablePrivacy = enable; return this; }
        public Builder privacyEpsilon(float epsilon) { this.privacyEpsilon = epsilon; return this; }
        public Builder privacyDelta(float delta) { this.privacyDelta = delta; return this; }
        public Builder privacyClippingNorm(float norm) { this.privacyClippingNorm = norm; return this; }
        public Builder privacyAnonymizePii(boolean anonymize) { this.privacyAnonymizePii = anonymize; return this; }
        public Builder privacyPseudonymizationSalt(String salt) { this.privacyPseudonymizationSalt = salt; return this; }
        public Builder enableImportance(boolean enable) { this.enableImportance = enable; return this; }
        public Builder importanceWeightSurprise(float w) { this.importanceWeightSurprise = w; return this; }
        public Builder importanceWeightAffect(float w) { this.importanceWeightAffect = w; return this; }
        public Builder importanceWeightGoal(float w) { this.importanceWeightGoal = w; return this; }
        public Builder importanceWeightSocial(float w) { this.importanceWeightSocial = w; return this; }
        public Builder importanceWeightNovelty(float w) { this.importanceWeightNovelty = w; return this; }
        public Builder importanceFlashbulbThreshold(float t) { this.importanceFlashbulbThreshold = t; return this; }

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
                    efeSoulWeightOrgUnit, constructivePersistenceEnabled,
                    constructivePersistenceThreshold, backgroundDecayEnabled,
                    backgroundDecayFactor, backgroundDecayIntervalSeconds,
                    enableSoftIdentityAnchor, identityAnchorEta, identityLyapunovThreshold,
                    identityCoreSnapshotEpochs, enableEventDensity, eventDensityThreshold,
                    eventDensityAlphaKl, eventDensityBetaGradient, eventDensityGammaSurprise,
                    eventDensitySamplingMinHz, eventDensitySamplingMaxHz,
                    enableBocpd, bocpdHazardLambda, bocpdChangePointThreshold,
                    bocpdSurprisalCutThreshold, bocpdMaxEpisodeFrames, bocpdMaxRunLength,
                    enablePrivacy, privacyEpsilon, privacyDelta, privacyClippingNorm,
                    privacyAnonymizePii, privacyPseudonymizationSalt,
                    enableImportance, importanceWeightSurprise, importanceWeightAffect,
                    importanceWeightGoal, importanceWeightSocial, importanceWeightNovelty,
                    importanceFlashbulbThreshold
            );
        }
    }
}
