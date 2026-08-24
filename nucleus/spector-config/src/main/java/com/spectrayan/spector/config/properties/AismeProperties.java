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

    private boolean enableExpectedFreeEnergy = DEFAULT_MEMORY_AISME_EFE_ENABLED;
    private float efePolicyPrecision = DEFAULT_MEMORY_AISME_EFE_POLICY_PRECISION;
    private float efeEpistemicWeight = DEFAULT_MEMORY_AISME_EFE_EPISTEMIC_WEIGHT;
    private float efePragmaticWeight = DEFAULT_MEMORY_AISME_EFE_PRAGMATIC_WEIGHT;
    private float efeSoulWeightAgent = DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_AGENT;
    private float efeSoulWeightUser = DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_USER;
    private float efeSoulWeightTenant = DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_TENANT;
    private float efeSoulWeightOrgUnit = DEFAULT_MEMORY_AISME_EFE_SOUL_WEIGHT_ORG_UNIT;

    private boolean constructivePersistenceEnabled = DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_ENABLED;
    private float constructivePersistenceThreshold = DEFAULT_MEMORY_AISME_CONSTRUCTIVE_PERSISTENCE_THRESHOLD;
    private boolean backgroundDecayEnabled = DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_ENABLED;
    private float backgroundDecayFactor = DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_FACTOR;
    private int backgroundDecayIntervalSeconds = DEFAULT_MEMORY_AISME_BACKGROUND_DECAY_INTERVAL_SECONDS;

    private boolean enableSoftIdentityAnchor = DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED;
    private float identityAnchorEta = DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA;
    private float identityLyapunovThreshold = DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD;
    private int identityCoreSnapshotEpochs = DEFAULT_MEMORY_AISME_IDENTITY_CORE_SNAPSHOT_EPOCHS;

    private boolean enableEventDensity = DEFAULT_MEMORY_AISME_EVENT_DENSITY_ENABLED;
    private float eventDensityThreshold = DEFAULT_MEMORY_AISME_EVENT_DENSITY_THRESHOLD;
    private float eventDensityAlphaKl = DEFAULT_MEMORY_AISME_EVENT_DENSITY_ALPHA_KL;
    private float eventDensityBetaGradient = DEFAULT_MEMORY_AISME_EVENT_DENSITY_BETA_GRADIENT;
    private float eventDensityGammaSurprise = DEFAULT_MEMORY_AISME_EVENT_DENSITY_GAMMA_SURPRISE;
    private float eventDensitySamplingMinHz = DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MIN_HZ;
    private float eventDensitySamplingMaxHz = DEFAULT_MEMORY_AISME_EVENT_DENSITY_SAMPLING_MAX_HZ;

    private boolean enableBocpd = DEFAULT_MEMORY_AISME_BOCPD_ENABLED;
    private float bocpdHazardLambda = DEFAULT_MEMORY_AISME_BOCPD_HAZARD_LAMBDA;
    private float bocpdChangePointThreshold = DEFAULT_MEMORY_AISME_BOCPD_CHANGE_POINT_THRESHOLD;
    private float bocpdSurprisalCutThreshold = DEFAULT_MEMORY_AISME_BOCPD_SURPRISAL_CUT_THRESHOLD;
    private int bocpdMaxEpisodeFrames = DEFAULT_MEMORY_AISME_BOCPD_MAX_EPISODE_FRAMES;
    private int bocpdMaxRunLength = DEFAULT_MEMORY_AISME_BOCPD_MAX_RUN_LENGTH;

    private boolean enablePrivacy = DEFAULT_MEMORY_AISME_PRIVACY_ENABLED;
    private float privacyEpsilon = DEFAULT_MEMORY_AISME_PRIVACY_EPSILON;
    private float privacyDelta = DEFAULT_MEMORY_AISME_PRIVACY_DELTA;
    private float privacyClippingNorm = DEFAULT_MEMORY_AISME_PRIVACY_CLIPPING_NORM;
    private boolean privacyAnonymizePii = DEFAULT_MEMORY_AISME_PRIVACY_ANONYMIZE_PII;
    private String privacyPseudonymizationSalt = DEFAULT_MEMORY_AISME_PRIVACY_PSEUDONYMIZATION_SALT;

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

    public boolean isEnableExpectedFreeEnergy() {
        return enableExpectedFreeEnergy;
    }

    public void setEnableExpectedFreeEnergy(boolean enableExpectedFreeEnergy) {
        this.enableExpectedFreeEnergy = enableExpectedFreeEnergy;
    }

    public float getEfePolicyPrecision() {
        return efePolicyPrecision;
    }

    public void setEfePolicyPrecision(float efePolicyPrecision) {
        if (!Float.isNaN(efePolicyPrecision) && efePolicyPrecision >= 0.0f) {
            this.efePolicyPrecision = efePolicyPrecision;
        }
    }

    public float getEfeEpistemicWeight() {
        return efeEpistemicWeight;
    }

    public void setEfeEpistemicWeight(float efeEpistemicWeight) {
        if (!Float.isNaN(efeEpistemicWeight) && efeEpistemicWeight >= 0.0f) {
            this.efeEpistemicWeight = efeEpistemicWeight;
        }
    }

    public float getEfePragmaticWeight() {
        return efePragmaticWeight;
    }

    public void setEfePragmaticWeight(float efePragmaticWeight) {
        if (!Float.isNaN(efePragmaticWeight) && efePragmaticWeight >= 0.0f) {
            this.efePragmaticWeight = efePragmaticWeight;
        }
    }

    public float getEfeSoulWeightAgent() {
        return efeSoulWeightAgent;
    }

    public void setEfeSoulWeightAgent(float efeSoulWeightAgent) {
        if (!Float.isNaN(efeSoulWeightAgent) && efeSoulWeightAgent >= 0.0f) {
            this.efeSoulWeightAgent = efeSoulWeightAgent;
        }
    }

    public float getEfeSoulWeightUser() {
        return efeSoulWeightUser;
    }

    public void setEfeSoulWeightUser(float efeSoulWeightUser) {
        if (!Float.isNaN(efeSoulWeightUser) && efeSoulWeightUser >= 0.0f) {
            this.efeSoulWeightUser = efeSoulWeightUser;
        }
    }

    public float getEfeSoulWeightTenant() {
        return efeSoulWeightTenant;
    }

    public void setEfeSoulWeightTenant(float efeSoulWeightTenant) {
        if (!Float.isNaN(efeSoulWeightTenant) && efeSoulWeightTenant >= 0.0f) {
            this.efeSoulWeightTenant = efeSoulWeightTenant;
        }
    }

    public float getEfeSoulWeightOrgUnit() {
        return efeSoulWeightOrgUnit;
    }

    public void setEfeSoulWeightOrgUnit(float efeSoulWeightOrgUnit) {
        if (!Float.isNaN(efeSoulWeightOrgUnit) && efeSoulWeightOrgUnit >= 0.0f) {
            this.efeSoulWeightOrgUnit = efeSoulWeightOrgUnit;
        }
    }

    public boolean isConstructivePersistenceEnabled() {
        return constructivePersistenceEnabled;
    }

    public void setConstructivePersistenceEnabled(boolean constructivePersistenceEnabled) {
        this.constructivePersistenceEnabled = constructivePersistenceEnabled;
    }

    public float getConstructivePersistenceThreshold() {
        return constructivePersistenceThreshold;
    }

    public void setConstructivePersistenceThreshold(float constructivePersistenceThreshold) {
        if (!Float.isNaN(constructivePersistenceThreshold) && constructivePersistenceThreshold >= 0.0f) {
            this.constructivePersistenceThreshold = constructivePersistenceThreshold;
        }
    }

    public boolean isBackgroundDecayEnabled() {
        return backgroundDecayEnabled;
    }

    public void setBackgroundDecayEnabled(boolean backgroundDecayEnabled) {
        this.backgroundDecayEnabled = backgroundDecayEnabled;
    }

    public float getBackgroundDecayFactor() {
        return backgroundDecayFactor;
    }

    public void setBackgroundDecayFactor(float backgroundDecayFactor) {
        if (!Float.isNaN(backgroundDecayFactor) && backgroundDecayFactor >= 0.0f) {
            this.backgroundDecayFactor = backgroundDecayFactor;
        }
    }

    public int getBackgroundDecayIntervalSeconds() {
        return backgroundDecayIntervalSeconds;
    }

    public void setBackgroundDecayIntervalSeconds(int backgroundDecayIntervalSeconds) {
        if (backgroundDecayIntervalSeconds > 0) {
            this.backgroundDecayIntervalSeconds = backgroundDecayIntervalSeconds;
        }
    }

    public boolean isEnableSoftIdentityAnchor() {
        return enableSoftIdentityAnchor;
    }

    public void setEnableSoftIdentityAnchor(boolean enableSoftIdentityAnchor) {
        this.enableSoftIdentityAnchor = enableSoftIdentityAnchor;
    }

    public float getIdentityAnchorEta() {
        return identityAnchorEta;
    }

    public void setIdentityAnchorEta(float identityAnchorEta) {
        if (!Float.isNaN(identityAnchorEta) && identityAnchorEta >= 0.0f) {
            this.identityAnchorEta = identityAnchorEta;
        }
    }

    public float getIdentityLyapunovThreshold() {
        return identityLyapunovThreshold;
    }

    public void setIdentityLyapunovThreshold(float identityLyapunovThreshold) {
        if (!Float.isNaN(identityLyapunovThreshold) && identityLyapunovThreshold >= 0.0f) {
            this.identityLyapunovThreshold = identityLyapunovThreshold;
        }
    }

    public int getIdentityCoreSnapshotEpochs() {
        return identityCoreSnapshotEpochs;
    }

    public void setIdentityCoreSnapshotEpochs(int identityCoreSnapshotEpochs) {
        if (identityCoreSnapshotEpochs > 0) {
            this.identityCoreSnapshotEpochs = identityCoreSnapshotEpochs;
        }
    }

    public boolean isEnableEventDensity() {
        return enableEventDensity;
    }

    public void setEnableEventDensity(boolean enableEventDensity) {
        this.enableEventDensity = enableEventDensity;
    }

    public float getEventDensityThreshold() {
        return eventDensityThreshold;
    }

    public void setEventDensityThreshold(float eventDensityThreshold) {
        if (!Float.isNaN(eventDensityThreshold) && eventDensityThreshold >= 0.0f) {
            this.eventDensityThreshold = eventDensityThreshold;
        }
    }

    public float getEventDensityAlphaKl() {
        return eventDensityAlphaKl;
    }

    public void setEventDensityAlphaKl(float eventDensityAlphaKl) {
        if (!Float.isNaN(eventDensityAlphaKl) && eventDensityAlphaKl >= 0.0f) {
            this.eventDensityAlphaKl = eventDensityAlphaKl;
        }
    }

    public float getEventDensityBetaGradient() {
        return eventDensityBetaGradient;
    }

    public void setEventDensityBetaGradient(float eventDensityBetaGradient) {
        if (!Float.isNaN(eventDensityBetaGradient) && eventDensityBetaGradient >= 0.0f) {
            this.eventDensityBetaGradient = eventDensityBetaGradient;
        }
    }

    public float getEventDensityGammaSurprise() {
        return eventDensityGammaSurprise;
    }

    public void setEventDensityGammaSurprise(float eventDensityGammaSurprise) {
        if (!Float.isNaN(eventDensityGammaSurprise) && eventDensityGammaSurprise >= 0.0f) {
            this.eventDensityGammaSurprise = eventDensityGammaSurprise;
        }
    }

    public float getEventDensitySamplingMinHz() {
        return eventDensitySamplingMinHz;
    }

    public void setEventDensitySamplingMinHz(float eventDensitySamplingMinHz) {
        if (!Float.isNaN(eventDensitySamplingMinHz) && eventDensitySamplingMinHz > 0.0f) {
            this.eventDensitySamplingMinHz = eventDensitySamplingMinHz;
        }
    }

    public float getEventDensitySamplingMaxHz() {
        return eventDensitySamplingMaxHz;
    }

    public void setEventDensitySamplingMaxHz(float eventDensitySamplingMaxHz) {
        if (!Float.isNaN(eventDensitySamplingMaxHz) && eventDensitySamplingMaxHz >= this.eventDensitySamplingMinHz) {
            this.eventDensitySamplingMaxHz = eventDensitySamplingMaxHz;
        }
    }

    public boolean isEnableBocpd() {
        return enableBocpd;
    }

    public void setEnableBocpd(boolean enableBocpd) {
        this.enableBocpd = enableBocpd;
    }

    public float getBocpdHazardLambda() {
        return bocpdHazardLambda;
    }

    public void setBocpdHazardLambda(float bocpdHazardLambda) {
        if (!Float.isNaN(bocpdHazardLambda) && bocpdHazardLambda > 0.0f) {
            this.bocpdHazardLambda = bocpdHazardLambda;
        }
    }

    public float getBocpdChangePointThreshold() {
        return bocpdChangePointThreshold;
    }

    public void setBocpdChangePointThreshold(float bocpdChangePointThreshold) {
        if (!Float.isNaN(bocpdChangePointThreshold) && bocpdChangePointThreshold >= 0.0f && bocpdChangePointThreshold <= 1.0f) {
            this.bocpdChangePointThreshold = bocpdChangePointThreshold;
        }
    }

    public float getBocpdSurprisalCutThreshold() {
        return bocpdSurprisalCutThreshold;
    }

    public void setBocpdSurprisalCutThreshold(float bocpdSurprisalCutThreshold) {
        if (!Float.isNaN(bocpdSurprisalCutThreshold) && bocpdSurprisalCutThreshold >= 0.0f) {
            this.bocpdSurprisalCutThreshold = bocpdSurprisalCutThreshold;
        }
    }

    public int getBocpdMaxEpisodeFrames() {
        return bocpdMaxEpisodeFrames;
    }

    public void setBocpdMaxEpisodeFrames(int bocpdMaxEpisodeFrames) {
        if (bocpdMaxEpisodeFrames > 0) {
            this.bocpdMaxEpisodeFrames = bocpdMaxEpisodeFrames;
        }
    }

    public int getBocpdMaxRunLength() {
        return bocpdMaxRunLength;
    }

    public void setBocpdMaxRunLength(int bocpdMaxRunLength) {
        if (bocpdMaxRunLength > 0) {
            this.bocpdMaxRunLength = bocpdMaxRunLength;
        }
    }

    public boolean isEnablePrivacy() {
        return enablePrivacy;
    }

    public void setEnablePrivacy(boolean enablePrivacy) {
        this.enablePrivacy = enablePrivacy;
    }

    public float getPrivacyEpsilon() {
        return privacyEpsilon;
    }

    public void setPrivacyEpsilon(float privacyEpsilon) {
        if (!Float.isNaN(privacyEpsilon) && privacyEpsilon > 0.0f) {
            this.privacyEpsilon = privacyEpsilon;
        }
    }

    public float getPrivacyDelta() {
        return privacyDelta;
    }

    public void setPrivacyDelta(float privacyDelta) {
        if (!Float.isNaN(privacyDelta) && privacyDelta > 0.0f && privacyDelta < 1.0f) {
            this.privacyDelta = privacyDelta;
        }
    }

    public float getPrivacyClippingNorm() {
        return privacyClippingNorm;
    }

    public void setPrivacyClippingNorm(float privacyClippingNorm) {
        if (!Float.isNaN(privacyClippingNorm) && privacyClippingNorm > 0.0f) {
            this.privacyClippingNorm = privacyClippingNorm;
        }
    }

    public boolean isPrivacyAnonymizePii() {
        return privacyAnonymizePii;
    }

    public void setPrivacyAnonymizePii(boolean privacyAnonymizePii) {
        this.privacyAnonymizePii = privacyAnonymizePii;
    }

    public String getPrivacyPseudonymizationSalt() {
        return privacyPseudonymizationSalt;
    }

    public void setPrivacyPseudonymizationSalt(String privacyPseudonymizationSalt) {
        if (privacyPseudonymizationSalt != null) {
            this.privacyPseudonymizationSalt = privacyPseudonymizationSalt;
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
    public boolean enableExpectedFreeEnergy() { return isEnableExpectedFreeEnergy(); }
    public float efePolicyPrecision() { return getEfePolicyPrecision(); }
    public float efeEpistemicWeight() { return getEfeEpistemicWeight(); }
    public float efePragmaticWeight() { return getEfePragmaticWeight(); }
    public float efeSoulWeightAgent() { return getEfeSoulWeightAgent(); }
    public float efeSoulWeightUser() { return getEfeSoulWeightUser(); }
    public float efeSoulWeightTenant() { return getEfeSoulWeightTenant(); }
    public float efeSoulWeightOrgUnit() { return getEfeSoulWeightOrgUnit(); }
    public boolean constructivePersistenceEnabled() { return isConstructivePersistenceEnabled(); }
    public float constructivePersistenceThreshold() { return getConstructivePersistenceThreshold(); }
    public boolean backgroundDecayEnabled() { return isBackgroundDecayEnabled(); }
    public float backgroundDecayFactor() { return getBackgroundDecayFactor(); }
    public int backgroundDecayIntervalSeconds() { return getBackgroundDecayIntervalSeconds(); }
    public boolean enableSoftIdentityAnchor() { return isEnableSoftIdentityAnchor(); }
    public float identityAnchorEta() { return getIdentityAnchorEta(); }
    public float identityLyapunovThreshold() { return getIdentityLyapunovThreshold(); }
    public int identityCoreSnapshotEpochs() { return getIdentityCoreSnapshotEpochs(); }
    public boolean enableEventDensity() { return isEnableEventDensity(); }
    public float eventDensityThreshold() { return getEventDensityThreshold(); }
    public float eventDensityAlphaKl() { return getEventDensityAlphaKl(); }
    public float eventDensityBetaGradient() { return getEventDensityBetaGradient(); }
    public float eventDensityGammaSurprise() { return getEventDensityGammaSurprise(); }
    public float eventDensitySamplingMinHz() { return getEventDensitySamplingMinHz(); }
    public float eventDensitySamplingMaxHz() { return getEventDensitySamplingMaxHz(); }
    public boolean enableBocpd() { return isEnableBocpd(); }
    public float bocpdHazardLambda() { return getBocpdHazardLambda(); }
    public float bocpdChangePointThreshold() { return getBocpdChangePointThreshold(); }
    public float bocpdSurprisalCutThreshold() { return getBocpdSurprisalCutThreshold(); }
    public int bocpdMaxEpisodeFrames() { return getBocpdMaxEpisodeFrames(); }
    public int bocpdMaxRunLength() { return getBocpdMaxRunLength(); }
    public boolean enablePrivacy() { return isEnablePrivacy(); }
    public float privacyEpsilon() { return getPrivacyEpsilon(); }
    public float privacyDelta() { return getPrivacyDelta(); }
    public float privacyClippingNorm() { return getPrivacyClippingNorm(); }
    public boolean privacyAnonymizePii() { return isPrivacyAnonymizePii(); }
    public String privacyPseudonymizationSalt() { return getPrivacyPseudonymizationSalt(); }
}
