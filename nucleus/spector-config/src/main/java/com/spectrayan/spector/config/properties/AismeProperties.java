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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public static final List<String> DEFAULT_IMPORTANCE_AFFECT_HIGH_KEYWORDS = List.of(
            "critical", "panic", "outage", "catastrophic", "emergency", "love", "hate", "congratulations");
    public static final List<String> DEFAULT_IMPORTANCE_AFFECT_MEDIUM_KEYWORDS = List.of(
            "error", "fail", "bug");
    public static final List<String> DEFAULT_IMPORTANCE_GOAL_HIGH_KEYWORDS = List.of(
            "critical", "panic", "outage", "catastrophic", "emergency");
    public static final List<String> DEFAULT_IMPORTANCE_GOAL_MEDIUM_KEYWORDS = List.of(
            "goal", "milestone", "task", "todo", "plan", "commit");
    public static final List<String> DEFAULT_IMPORTANCE_SOCIAL_KEYWORDS = List.of(
            "you promised", "i commit", "agreement", "meeting with", "conversation");

    private boolean enableImportance = DEFAULT_MEMORY_AISME_IMPORTANCE_ENABLED;
    private float importanceWeightSurprise = DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SURPRISE;
    private float importanceWeightAffect = DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_AFFECT;
    private float importanceWeightGoal = DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_GOAL;
    private float importanceWeightSocial = DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_SOCIAL;
    private float importanceWeightNovelty = DEFAULT_MEMORY_AISME_IMPORTANCE_WEIGHT_NOVELTY;
    private float importanceFlashbulbThreshold = DEFAULT_MEMORY_AISME_IMPORTANCE_FLASHBULB_THRESHOLD;
    private List<String> importanceAffectHighKeywords = new ArrayList<>(DEFAULT_IMPORTANCE_AFFECT_HIGH_KEYWORDS);
    private List<String> importanceAffectMediumKeywords = new ArrayList<>(DEFAULT_IMPORTANCE_AFFECT_MEDIUM_KEYWORDS);
    private List<String> importanceGoalHighKeywords = new ArrayList<>(DEFAULT_IMPORTANCE_GOAL_HIGH_KEYWORDS);
    private List<String> importanceGoalMediumKeywords = new ArrayList<>(DEFAULT_IMPORTANCE_GOAL_MEDIUM_KEYWORDS);
    private List<String> importanceSocialKeywords = new ArrayList<>(DEFAULT_IMPORTANCE_SOCIAL_KEYWORDS);
    private Map<String, List<String>> importanceLocaleAffectHighKeywords = new LinkedHashMap<>();
    private Map<String, List<String>> importanceLocaleAffectMediumKeywords = new LinkedHashMap<>();
    private Map<String, List<String>> importanceLocaleGoalHighKeywords = new LinkedHashMap<>();
    private Map<String, List<String>> importanceLocaleGoalMediumKeywords = new LinkedHashMap<>();
    private Map<String, List<String>> importanceLocaleSocialKeywords = new LinkedHashMap<>();

    private boolean enableLifespan = DEFAULT_MEMORY_AISME_LIFESPAN_ENABLED;
    private float lifespanTau0 = DEFAULT_MEMORY_AISME_LIFESPAN_TAU_0;
    private float lifespanK = DEFAULT_MEMORY_AISME_LIFESPAN_K;
    private long lifespanT0Epochs = DEFAULT_MEMORY_AISME_LIFESPAN_T0_EPOCHS;
    private long lifespanVTarget = DEFAULT_MEMORY_AISME_LIFESPAN_V_TARGET;
    private float lifespanGamma = DEFAULT_MEMORY_AISME_LIFESPAN_GAMMA;
    private boolean lifespanFlashbulbProtect = DEFAULT_MEMORY_AISME_LIFESPAN_FLASHBULB_PROTECT;

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

    public boolean isEnableImportance() {
        return enableImportance;
    }

    public void setEnableImportance(boolean enableImportance) {
        this.enableImportance = enableImportance;
    }

    public float getImportanceWeightSurprise() {
        return importanceWeightSurprise;
    }

    public void setImportanceWeightSurprise(float importanceWeightSurprise) {
        if (!Float.isNaN(importanceWeightSurprise) && importanceWeightSurprise >= 0.0f) {
            this.importanceWeightSurprise = importanceWeightSurprise;
        }
    }

    public float getImportanceWeightAffect() {
        return importanceWeightAffect;
    }

    public void setImportanceWeightAffect(float importanceWeightAffect) {
        if (!Float.isNaN(importanceWeightAffect) && importanceWeightAffect >= 0.0f) {
            this.importanceWeightAffect = importanceWeightAffect;
        }
    }

    public float getImportanceWeightGoal() {
        return importanceWeightGoal;
    }

    public void setImportanceWeightGoal(float importanceWeightGoal) {
        if (!Float.isNaN(importanceWeightGoal) && importanceWeightGoal >= 0.0f) {
            this.importanceWeightGoal = importanceWeightGoal;
        }
    }

    public float getImportanceWeightSocial() {
        return importanceWeightSocial;
    }

    public void setImportanceWeightSocial(float importanceWeightSocial) {
        if (!Float.isNaN(importanceWeightSocial) && importanceWeightSocial >= 0.0f) {
            this.importanceWeightSocial = importanceWeightSocial;
        }
    }

    public float getImportanceWeightNovelty() {
        return importanceWeightNovelty;
    }

    public void setImportanceWeightNovelty(float importanceWeightNovelty) {
        if (!Float.isNaN(importanceWeightNovelty) && importanceWeightNovelty >= 0.0f) {
            this.importanceWeightNovelty = importanceWeightNovelty;
        }
    }

    public float getImportanceFlashbulbThreshold() {
        return importanceFlashbulbThreshold;
    }

    public void setImportanceFlashbulbThreshold(float importanceFlashbulbThreshold) {
        if (!Float.isNaN(importanceFlashbulbThreshold) && importanceFlashbulbThreshold >= 0.0f && importanceFlashbulbThreshold <= 1.0f) {
            this.importanceFlashbulbThreshold = importanceFlashbulbThreshold;
        }
    }

    public List<String> getImportanceAffectHighKeywords() {
        return Collections.unmodifiableList(importanceAffectHighKeywords);
    }

    public void setImportanceAffectHighKeywords(List<String> keywords) {
        if (keywords != null) {
            this.importanceAffectHighKeywords = copyKeywordList(keywords);
        }
    }

    public List<String> getImportanceAffectMediumKeywords() {
        return Collections.unmodifiableList(importanceAffectMediumKeywords);
    }

    public void setImportanceAffectMediumKeywords(List<String> keywords) {
        if (keywords != null) {
            this.importanceAffectMediumKeywords = copyKeywordList(keywords);
        }
    }

    public List<String> getImportanceGoalHighKeywords() {
        return Collections.unmodifiableList(importanceGoalHighKeywords);
    }

    public void setImportanceGoalHighKeywords(List<String> keywords) {
        if (keywords != null) {
            this.importanceGoalHighKeywords = copyKeywordList(keywords);
        }
    }

    public List<String> getImportanceGoalMediumKeywords() {
        return Collections.unmodifiableList(importanceGoalMediumKeywords);
    }

    public void setImportanceGoalMediumKeywords(List<String> keywords) {
        if (keywords != null) {
            this.importanceGoalMediumKeywords = copyKeywordList(keywords);
        }
    }

    public List<String> getImportanceSocialKeywords() {
        return Collections.unmodifiableList(importanceSocialKeywords);
    }

    public void setImportanceSocialKeywords(List<String> keywords) {
        if (keywords != null) {
            this.importanceSocialKeywords = copyKeywordList(keywords);
        }
    }

    public Map<String, List<String>> getImportanceLocaleAffectHighKeywords() {
        return Collections.unmodifiableMap(importanceLocaleAffectHighKeywords);
    }

    public void setImportanceLocaleAffectHighKeywords(Map<String, List<String>> localeKeywords) {
        if (localeKeywords != null) {
            this.importanceLocaleAffectHighKeywords = copyLocaleKeywordMap(localeKeywords);
        }
    }

    public Map<String, List<String>> getImportanceLocaleAffectMediumKeywords() {
        return Collections.unmodifiableMap(importanceLocaleAffectMediumKeywords);
    }

    public void setImportanceLocaleAffectMediumKeywords(Map<String, List<String>> localeKeywords) {
        if (localeKeywords != null) {
            this.importanceLocaleAffectMediumKeywords = copyLocaleKeywordMap(localeKeywords);
        }
    }

    public Map<String, List<String>> getImportanceLocaleGoalHighKeywords() {
        return Collections.unmodifiableMap(importanceLocaleGoalHighKeywords);
    }

    public void setImportanceLocaleGoalHighKeywords(Map<String, List<String>> localeKeywords) {
        if (localeKeywords != null) {
            this.importanceLocaleGoalHighKeywords = copyLocaleKeywordMap(localeKeywords);
        }
    }

    public Map<String, List<String>> getImportanceLocaleGoalMediumKeywords() {
        return Collections.unmodifiableMap(importanceLocaleGoalMediumKeywords);
    }

    public void setImportanceLocaleGoalMediumKeywords(Map<String, List<String>> localeKeywords) {
        if (localeKeywords != null) {
            this.importanceLocaleGoalMediumKeywords = copyLocaleKeywordMap(localeKeywords);
        }
    }

    public Map<String, List<String>> getImportanceLocaleSocialKeywords() {
        return Collections.unmodifiableMap(importanceLocaleSocialKeywords);
    }

    public void setImportanceLocaleSocialKeywords(Map<String, List<String>> localeKeywords) {
        if (localeKeywords != null) {
            this.importanceLocaleSocialKeywords = copyLocaleKeywordMap(localeKeywords);
        }
    }

    private static List<String> copyKeywordList(List<String> keywords) {
        List<String> copy = new ArrayList<>(keywords.size());
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                copy.add(kw.trim());
            }
        }
        return copy;
    }

    private static Map<String, List<String>> copyLocaleKeywordMap(Map<String, List<String>> localeKeywords) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : localeKeywords.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                copy.put(normalizeLocaleKey(entry.getKey()), Collections.unmodifiableList(copyKeywordList(entry.getValue())));
            }
        }
        return copy;
    }

    private static String normalizeLocaleKey(String localeTag) {
        return localeTag.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static List<String> resolveKeywordsForLocale(
            List<String> baseKeywords,
            Map<String, List<String>> localeOverrides,
            Locale locale) {
        if (locale != null && localeOverrides != null && !localeOverrides.isEmpty()) {
            String fullTag = normalizeLocaleKey(locale.toLanguageTag());
            List<String> exact = localeOverrides.get(fullTag);
            if (exact != null) {
                return exact;
            }
            String language = normalizeLocaleKey(locale.getLanguage());
            List<String> langMatch = localeOverrides.get(language);
            if (langMatch != null) {
                return langMatch;
            }
        }
        return Collections.unmodifiableList(baseKeywords);
    }

    public boolean isEnableLifespan() {
        return enableLifespan;
    }

    public void setEnableLifespan(boolean enableLifespan) {
        this.enableLifespan = enableLifespan;
    }

    public float getLifespanTau0() {
        return lifespanTau0;
    }

    public void setLifespanTau0(float lifespanTau0) {
        if (!Float.isNaN(lifespanTau0) && lifespanTau0 >= 0.0f && lifespanTau0 <= 1.0f) {
            this.lifespanTau0 = lifespanTau0;
        }
    }

    public float getLifespanK() {
        return lifespanK;
    }

    public void setLifespanK(float lifespanK) {
        if (!Float.isNaN(lifespanK) && lifespanK >= 0.0f) {
            this.lifespanK = lifespanK;
        }
    }

    public long getLifespanT0Epochs() {
        return lifespanT0Epochs;
    }

    public void setLifespanT0Epochs(long lifespanT0Epochs) {
        if (lifespanT0Epochs > 0L) {
            this.lifespanT0Epochs = lifespanT0Epochs;
        }
    }

    public long getLifespanVTarget() {
        return lifespanVTarget;
    }

    public void setLifespanVTarget(long lifespanVTarget) {
        if (lifespanVTarget > 0L) {
            this.lifespanVTarget = lifespanVTarget;
        }
    }

    public float getLifespanGamma() {
        return lifespanGamma;
    }

    public void setLifespanGamma(float lifespanGamma) {
        if (!Float.isNaN(lifespanGamma) && lifespanGamma >= 0.0f) {
            this.lifespanGamma = lifespanGamma;
        }
    }

    public boolean isLifespanFlashbulbProtect() {
        return lifespanFlashbulbProtect;
    }

    public void setLifespanFlashbulbProtect(boolean lifespanFlashbulbProtect) {
        this.lifespanFlashbulbProtect = lifespanFlashbulbProtect;
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
    public boolean enableImportance() { return isEnableImportance(); }
    public float importanceWeightSurprise() { return getImportanceWeightSurprise(); }
    public float importanceWeightAffect() { return getImportanceWeightAffect(); }
    public float importanceWeightGoal() { return getImportanceWeightGoal(); }
    public float importanceWeightSocial() { return getImportanceWeightSocial(); }
    public float importanceWeightNovelty() { return getImportanceWeightNovelty(); }
    public float importanceFlashbulbThreshold() { return getImportanceFlashbulbThreshold(); }
    public List<String> importanceAffectHighKeywords() { return getImportanceAffectHighKeywords(); }
    public List<String> importanceAffectHighKeywords(Locale locale) {
        return resolveKeywordsForLocale(importanceAffectHighKeywords, importanceLocaleAffectHighKeywords, locale);
    }
    public List<String> importanceAffectMediumKeywords() { return getImportanceAffectMediumKeywords(); }
    public List<String> importanceAffectMediumKeywords(Locale locale) {
        return resolveKeywordsForLocale(importanceAffectMediumKeywords, importanceLocaleAffectMediumKeywords, locale);
    }
    public List<String> importanceGoalHighKeywords() { return getImportanceGoalHighKeywords(); }
    public List<String> importanceGoalHighKeywords(Locale locale) {
        return resolveKeywordsForLocale(importanceGoalHighKeywords, importanceLocaleGoalHighKeywords, locale);
    }
    public List<String> importanceGoalMediumKeywords() { return getImportanceGoalMediumKeywords(); }
    public List<String> importanceGoalMediumKeywords(Locale locale) {
        return resolveKeywordsForLocale(importanceGoalMediumKeywords, importanceLocaleGoalMediumKeywords, locale);
    }
    public List<String> importanceSocialKeywords() { return getImportanceSocialKeywords(); }
    public List<String> importanceSocialKeywords(Locale locale) {
        return resolveKeywordsForLocale(importanceSocialKeywords, importanceLocaleSocialKeywords, locale);
    }
    public Map<String, List<String>> importanceLocaleAffectHighKeywords() { return getImportanceLocaleAffectHighKeywords(); }
    public Map<String, List<String>> importanceLocaleAffectMediumKeywords() { return getImportanceLocaleAffectMediumKeywords(); }
    public Map<String, List<String>> importanceLocaleGoalHighKeywords() { return getImportanceLocaleGoalHighKeywords(); }
    public Map<String, List<String>> importanceLocaleGoalMediumKeywords() { return getImportanceLocaleGoalMediumKeywords(); }
    public Map<String, List<String>> importanceLocaleSocialKeywords() { return getImportanceLocaleSocialKeywords(); }
    public boolean enableLifespan() { return isEnableLifespan(); }
    public float lifespanTau0() { return getLifespanTau0(); }
    public float lifespanK() { return getLifespanK(); }
    public long lifespanT0Epochs() { return getLifespanT0Epochs(); }
    public long lifespanVTarget() { return getLifespanVTarget(); }
    public float lifespanGamma() { return getLifespanGamma(); }
    public boolean lifespanFlashbulbProtect() { return isLifespanFlashbulbProtect(); }

    public AismeProperties copy() {
        AismeProperties cp = new AismeProperties();
        cp.enabled = this.enabled;
        cp.enableHomeostasis = this.enableHomeostasis;
        cp.enableFreeEnergy = this.enableFreeEnergy;
        cp.enableHopfield = this.enableHopfield;
        cp.enableManifold = this.enableManifold;
        cp.enablePredictiveCoding = this.enablePredictiveCoding;
        cp.enableConsciousnessContinuity = this.enableConsciousnessContinuity;
        cp.enableGlobalWorkspace = this.enableGlobalWorkspace;
        cp.globalWorkspaceCapacity = this.globalWorkspaceCapacity;
        cp.hopfieldTemperature = this.hopfieldTemperature;
        cp.manifoldSigma = this.manifoldSigma;
        cp.phiCohesionThreshold = this.phiCohesionThreshold;
        cp.enableDmnSpontaneous = this.enableDmnSpontaneous;
        cp.dmnIdleIntervalSeconds = this.dmnIdleIntervalSeconds;
        cp.enableLongitudinalContinuity = this.enableLongitudinalContinuity;
        cp.longitudinalSnapshotIntervalMinutes = this.longitudinalSnapshotIntervalMinutes;
        cp.enableExpectedFreeEnergy = this.enableExpectedFreeEnergy;
        cp.efePolicyPrecision = this.efePolicyPrecision;
        cp.efeEpistemicWeight = this.efeEpistemicWeight;
        cp.efePragmaticWeight = this.efePragmaticWeight;
        cp.efeSoulWeightAgent = this.efeSoulWeightAgent;
        cp.efeSoulWeightUser = this.efeSoulWeightUser;
        cp.efeSoulWeightTenant = this.efeSoulWeightTenant;
        cp.efeSoulWeightOrgUnit = this.efeSoulWeightOrgUnit;
        cp.constructivePersistenceEnabled = this.constructivePersistenceEnabled;
        cp.constructivePersistenceThreshold = this.constructivePersistenceThreshold;
        cp.backgroundDecayEnabled = this.backgroundDecayEnabled;
        cp.backgroundDecayFactor = this.backgroundDecayFactor;
        cp.backgroundDecayIntervalSeconds = this.backgroundDecayIntervalSeconds;
        cp.enableSoftIdentityAnchor = this.enableSoftIdentityAnchor;
        cp.identityAnchorEta = this.identityAnchorEta;
        cp.identityLyapunovThreshold = this.identityLyapunovThreshold;
        cp.identityCoreSnapshotEpochs = this.identityCoreSnapshotEpochs;
        cp.enableEventDensity = this.enableEventDensity;
        cp.eventDensityThreshold = this.eventDensityThreshold;
        cp.eventDensityAlphaKl = this.eventDensityAlphaKl;
        cp.eventDensityBetaGradient = this.eventDensityBetaGradient;
        cp.eventDensityGammaSurprise = this.eventDensityGammaSurprise;
        cp.eventDensitySamplingMinHz = this.eventDensitySamplingMinHz;
        cp.eventDensitySamplingMaxHz = this.eventDensitySamplingMaxHz;
        cp.enableBocpd = this.enableBocpd;
        cp.bocpdHazardLambda = this.bocpdHazardLambda;
        cp.bocpdChangePointThreshold = this.bocpdChangePointThreshold;
        cp.bocpdSurprisalCutThreshold = this.bocpdSurprisalCutThreshold;
        cp.bocpdMaxEpisodeFrames = this.bocpdMaxEpisodeFrames;
        cp.bocpdMaxRunLength = this.bocpdMaxRunLength;
        cp.enablePrivacy = this.enablePrivacy;
        cp.privacyEpsilon = this.privacyEpsilon;
        cp.privacyDelta = this.privacyDelta;
        cp.privacyClippingNorm = this.privacyClippingNorm;
        cp.privacyAnonymizePii = this.privacyAnonymizePii;
        cp.privacyPseudonymizationSalt = this.privacyPseudonymizationSalt;
        cp.enableImportance = this.enableImportance;
        cp.importanceWeightSurprise = this.importanceWeightSurprise;
        cp.importanceWeightAffect = this.importanceWeightAffect;
        cp.importanceWeightGoal = this.importanceWeightGoal;
        cp.importanceWeightSocial = this.importanceWeightSocial;
        cp.importanceWeightNovelty = this.importanceWeightNovelty;
        cp.importanceFlashbulbThreshold = this.importanceFlashbulbThreshold;
        cp.importanceAffectHighKeywords = new ArrayList<>(this.importanceAffectHighKeywords);
        cp.importanceAffectMediumKeywords = new ArrayList<>(this.importanceAffectMediumKeywords);
        cp.importanceGoalHighKeywords = new ArrayList<>(this.importanceGoalHighKeywords);
        cp.importanceGoalMediumKeywords = new ArrayList<>(this.importanceGoalMediumKeywords);
        cp.importanceSocialKeywords = new ArrayList<>(this.importanceSocialKeywords);
        cp.importanceLocaleAffectHighKeywords = copyLocaleKeywordMap(this.importanceLocaleAffectHighKeywords);
        cp.importanceLocaleAffectMediumKeywords = copyLocaleKeywordMap(this.importanceLocaleAffectMediumKeywords);
        cp.importanceLocaleGoalHighKeywords = copyLocaleKeywordMap(this.importanceLocaleGoalHighKeywords);
        cp.importanceLocaleGoalMediumKeywords = copyLocaleKeywordMap(this.importanceLocaleGoalMediumKeywords);
        cp.importanceLocaleSocialKeywords = copyLocaleKeywordMap(this.importanceLocaleSocialKeywords);
        cp.enableLifespan = this.enableLifespan;
        cp.lifespanTau0 = this.lifespanTau0;
        cp.lifespanK = this.lifespanK;
        cp.lifespanT0Epochs = this.lifespanT0Epochs;
        cp.lifespanVTarget = this.lifespanVTarget;
        cp.lifespanGamma = this.lifespanGamma;
        cp.lifespanFlashbulbProtect = this.lifespanFlashbulbProtect;
        return cp;
    }

    // ── Factory & Builder Methods ──

    public static AismeProperties disabled() {
        AismeProperties props = new AismeProperties();
        props.setEnabled(false);
        return props;
    }

    public static AismeProperties defaultConfig() {
        AismeProperties props = new AismeProperties();
        props.setEnabled(true);
        return props;
    }

    public static AismeProperties defaultProperties() {
        return new AismeProperties();
    }

    public static AismeProperties fromProperties(AismeProperties properties) {
        return properties != null ? properties.copy() : defaultConfig();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AismeProperties properties = defaultConfig();

        public Builder enabled(boolean v) { properties.setEnabled(v); return this; }
        public Builder enableHomeostasis(boolean v) { properties.setEnableHomeostasis(v); return this; }
        public Builder enableFreeEnergy(boolean v) { properties.setEnableFreeEnergy(v); return this; }
        public Builder enableHopfield(boolean v) { properties.setEnableHopfield(v); return this; }
        public Builder enableManifold(boolean v) { properties.setEnableManifold(v); return this; }
        public Builder enablePredictiveCoding(boolean v) { properties.setEnablePredictiveCoding(v); return this; }
        public Builder enableConsciousnessContinuity(boolean v) { properties.setEnableConsciousnessContinuity(v); return this; }
        public Builder enableGlobalWorkspace(boolean v) { properties.setEnableGlobalWorkspace(v); return this; }
        public Builder globalWorkspaceCapacity(int v) { properties.setGlobalWorkspaceCapacity(v); return this; }
        public Builder hopfieldTemperature(float v) { properties.setHopfieldTemperature(v); return this; }
        public Builder manifoldSigma(float v) { properties.setManifoldSigma(v); return this; }
        public Builder phiCohesionThreshold(float v) { properties.setPhiCohesionThreshold(v); return this; }
        public Builder enableDmnSpontaneous(boolean v) { properties.setEnableDmnSpontaneous(v); return this; }
        public Builder dmnIdleIntervalSeconds(int v) { properties.setDmnIdleIntervalSeconds(v); return this; }
        public Builder enableLongitudinalContinuity(boolean v) { properties.setEnableLongitudinalContinuity(v); return this; }
        public Builder longitudinalSnapshotIntervalMinutes(int v) { properties.setLongitudinalSnapshotIntervalMinutes(v); return this; }
        public Builder enableExpectedFreeEnergy(boolean v) { properties.setEnableExpectedFreeEnergy(v); return this; }
        public Builder efePolicyPrecision(float v) { properties.setEfePolicyPrecision(v); return this; }
        public Builder efeEpistemicWeight(float v) { properties.setEfeEpistemicWeight(v); return this; }
        public Builder efePragmaticWeight(float v) { properties.setEfePragmaticWeight(v); return this; }
        public Builder efeSoulWeightAgent(float v) { properties.setEfeSoulWeightAgent(v); return this; }
        public Builder efeSoulWeightUser(float v) { properties.setEfeSoulWeightUser(v); return this; }
        public Builder efeSoulWeightTenant(float v) { properties.setEfeSoulWeightTenant(v); return this; }
        public Builder efeSoulWeightOrgUnit(float v) { properties.setEfeSoulWeightOrgUnit(v); return this; }
        public Builder constructivePersistenceEnabled(boolean v) { properties.setConstructivePersistenceEnabled(v); return this; }
        public Builder constructivePersistenceThreshold(float v) { properties.setConstructivePersistenceThreshold(v); return this; }
        public Builder backgroundDecayEnabled(boolean v) { properties.setBackgroundDecayEnabled(v); return this; }
        public Builder backgroundDecayFactor(float v) { properties.setBackgroundDecayFactor(v); return this; }
        public Builder backgroundDecayIntervalSeconds(int v) { properties.setBackgroundDecayIntervalSeconds(v); return this; }
        public Builder enableSoftIdentityAnchor(boolean v) { properties.setEnableSoftIdentityAnchor(v); return this; }
        public Builder identityAnchorEta(float v) { properties.setIdentityAnchorEta(v); return this; }
        public Builder identityLyapunovThreshold(float v) { properties.setIdentityLyapunovThreshold(v); return this; }
        public Builder identityCoreSnapshotEpochs(int v) { properties.setIdentityCoreSnapshotEpochs(v); return this; }
        public Builder enableEventDensity(boolean v) { properties.setEnableEventDensity(v); return this; }
        public Builder eventDensityThreshold(float v) { properties.setEventDensityThreshold(v); return this; }
        public Builder eventDensityAlphaKl(float v) { properties.setEventDensityAlphaKl(v); return this; }
        public Builder eventDensityBetaGradient(float v) { properties.setEventDensityBetaGradient(v); return this; }
        public Builder eventDensityGammaSurprise(float v) { properties.setEventDensityGammaSurprise(v); return this; }
        public Builder eventDensitySamplingMinHz(float v) { properties.setEventDensitySamplingMinHz(v); return this; }
        public Builder eventDensitySamplingMaxHz(float v) { properties.setEventDensitySamplingMaxHz(v); return this; }
        public Builder enableBocpd(boolean v) { properties.setEnableBocpd(v); return this; }
        public Builder bocpdHazardLambda(float v) { properties.setBocpdHazardLambda(v); return this; }
        public Builder bocpdChangePointThreshold(float v) { properties.setBocpdChangePointThreshold(v); return this; }
        public Builder bocpdSurprisalCutThreshold(float v) { properties.setBocpdSurprisalCutThreshold(v); return this; }
        public Builder bocpdMaxEpisodeFrames(int v) { properties.setBocpdMaxEpisodeFrames(v); return this; }
        public Builder bocpdMaxRunLength(int v) { properties.setBocpdMaxRunLength(v); return this; }
        public Builder enablePrivacy(boolean v) { properties.setEnablePrivacy(v); return this; }
        public Builder privacyEpsilon(float v) { properties.setPrivacyEpsilon(v); return this; }
        public Builder privacyDelta(float v) { properties.setPrivacyDelta(v); return this; }
        public Builder privacyClippingNorm(float v) { properties.setPrivacyClippingNorm(v); return this; }
        public Builder privacyAnonymizePii(boolean v) { properties.setPrivacyAnonymizePii(v); return this; }
        public Builder privacyPseudonymizationSalt(String v) { properties.setPrivacyPseudonymizationSalt(v); return this; }
        public Builder enableImportance(boolean v) { properties.setEnableImportance(v); return this; }
        public Builder importanceWeightSurprise(float v) { properties.setImportanceWeightSurprise(v); return this; }
        public Builder importanceWeightAffect(float v) { properties.setImportanceWeightAffect(v); return this; }
        public Builder importanceWeightGoal(float v) { properties.setImportanceWeightGoal(v); return this; }
        public Builder importanceWeightSocial(float v) { properties.setImportanceWeightSocial(v); return this; }
        public Builder importanceWeightNovelty(float v) { properties.setImportanceWeightNovelty(v); return this; }
        public Builder importanceFlashbulbThreshold(float v) { properties.setImportanceFlashbulbThreshold(v); return this; }
        public Builder importanceAffectHighKeywords(List<String> v) { properties.setImportanceAffectHighKeywords(v); return this; }
        public Builder importanceAffectHighKeywords(String localeTag, List<String> v) {
            if (localeTag != null && v != null) {
                properties.importanceLocaleAffectHighKeywords.put(normalizeLocaleKey(localeTag), Collections.unmodifiableList(copyKeywordList(v)));
            }
            return this;
        }
        public Builder importanceAffectHighKeywords(Locale locale, List<String> v) {
            return locale != null ? importanceAffectHighKeywords(locale.toLanguageTag(), v) : this;
        }
        public Builder importanceAffectMediumKeywords(List<String> v) { properties.setImportanceAffectMediumKeywords(v); return this; }
        public Builder importanceAffectMediumKeywords(String localeTag, List<String> v) {
            if (localeTag != null && v != null) {
                properties.importanceLocaleAffectMediumKeywords.put(normalizeLocaleKey(localeTag), Collections.unmodifiableList(copyKeywordList(v)));
            }
            return this;
        }
        public Builder importanceAffectMediumKeywords(Locale locale, List<String> v) {
            return locale != null ? importanceAffectMediumKeywords(locale.toLanguageTag(), v) : this;
        }
        public Builder importanceGoalHighKeywords(List<String> v) { properties.setImportanceGoalHighKeywords(v); return this; }
        public Builder importanceGoalHighKeywords(String localeTag, List<String> v) {
            if (localeTag != null && v != null) {
                properties.importanceLocaleGoalHighKeywords.put(normalizeLocaleKey(localeTag), Collections.unmodifiableList(copyKeywordList(v)));
            }
            return this;
        }
        public Builder importanceGoalHighKeywords(Locale locale, List<String> v) {
            return locale != null ? importanceGoalHighKeywords(locale.toLanguageTag(), v) : this;
        }
        public Builder importanceGoalMediumKeywords(List<String> v) { properties.setImportanceGoalMediumKeywords(v); return this; }
        public Builder importanceGoalMediumKeywords(String localeTag, List<String> v) {
            if (localeTag != null && v != null) {
                properties.importanceLocaleGoalMediumKeywords.put(normalizeLocaleKey(localeTag), Collections.unmodifiableList(copyKeywordList(v)));
            }
            return this;
        }
        public Builder importanceGoalMediumKeywords(Locale locale, List<String> v) {
            return locale != null ? importanceGoalMediumKeywords(locale.toLanguageTag(), v) : this;
        }
        public Builder importanceSocialKeywords(List<String> v) { properties.setImportanceSocialKeywords(v); return this; }
        public Builder importanceSocialKeywords(String localeTag, List<String> v) {
            if (localeTag != null && v != null) {
                properties.importanceLocaleSocialKeywords.put(normalizeLocaleKey(localeTag), Collections.unmodifiableList(copyKeywordList(v)));
            }
            return this;
        }
        public Builder importanceSocialKeywords(Locale locale, List<String> v) {
            return locale != null ? importanceSocialKeywords(locale.toLanguageTag(), v) : this;
        }
        public Builder importanceLocaleAffectHighKeywords(Map<String, List<String>> v) { properties.setImportanceLocaleAffectHighKeywords(v); return this; }
        public Builder importanceLocaleAffectMediumKeywords(Map<String, List<String>> v) { properties.setImportanceLocaleAffectMediumKeywords(v); return this; }
        public Builder importanceLocaleGoalHighKeywords(Map<String, List<String>> v) { properties.setImportanceLocaleGoalHighKeywords(v); return this; }
        public Builder importanceLocaleGoalMediumKeywords(Map<String, List<String>> v) { properties.setImportanceLocaleGoalMediumKeywords(v); return this; }
        public Builder importanceLocaleSocialKeywords(Map<String, List<String>> v) { properties.setImportanceLocaleSocialKeywords(v); return this; }
        public Builder enableLifespan(boolean v) { properties.setEnableLifespan(v); return this; }
        public Builder lifespanTau0(float v) { properties.setLifespanTau0(v); return this; }
        public Builder lifespanK(float v) { properties.setLifespanK(v); return this; }
        public Builder lifespanT0Epochs(long v) { properties.setLifespanT0Epochs(v); return this; }
        public Builder lifespanVTarget(long v) { properties.setLifespanVTarget(v); return this; }
        public Builder lifespanGamma(float v) { properties.setLifespanGamma(v); return this; }
        public Builder lifespanFlashbulbProtect(boolean v) { properties.setLifespanFlashbulbProtect(v); return this; }

        public AismeProperties build() {
            return properties.copy();
        }
    }
}

