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

import java.io.Serializable;

public class GraphProperties implements Serializable {

    // Graph expansion
    private String expansionMode = "GATED";
    private float expansionThreshold = 0.40f;
    private float causalBoost = 0.3f;
    private float hebbianBoost = 0.3f;
    private float temporalForward = 0.8f;
    private float temporalBackward = 0.7f;
    private float entityAttenuation = 0.25f;
    
    // Sub-domain configs
    private HebbianProperties hebbian = new HebbianProperties();
    private StdpProperties stdp = new StdpProperties();
    private BridgeProperties bridge = new BridgeProperties();
    private EntityGraphProperties entity = new EntityGraphProperties();

    public String getExpansionMode() { return expansionMode; }
    public void setExpansionMode(String expansionMode) { this.expansionMode = expansionMode; }
    public String expansionMode() { return expansionMode; }

    public float getExpansionThreshold() { return expansionThreshold; }
    public void setExpansionThreshold(float expansionThreshold) { this.expansionThreshold = expansionThreshold; }
    public float expansionThreshold() { return expansionThreshold; }

    public float getCausalBoost() { return causalBoost; }
    public void setCausalBoost(float causalBoost) { this.causalBoost = causalBoost; }
    public float causalBoost() { return causalBoost; }

    public float getHebbianBoost() { return hebbianBoost; }
    public void setHebbianBoost(float hebbianBoost) { this.hebbianBoost = hebbianBoost; }
    public float hebbianBoost() { return hebbianBoost; }

    public float getTemporalForward() { return temporalForward; }
    public void setTemporalForward(float temporalForward) { this.temporalForward = temporalForward; }
    public float temporalForward() { return temporalForward; }

    public float getTemporalBackward() { return temporalBackward; }
    public void setTemporalBackward(float temporalBackward) { this.temporalBackward = temporalBackward; }
    public float temporalBackward() { return temporalBackward; }

    public float getEntityAttenuation() { return entityAttenuation; }
    public void setEntityAttenuation(float entityAttenuation) { this.entityAttenuation = entityAttenuation; }
    public float entityAttenuation() { return entityAttenuation; }

    public HebbianProperties getHebbian() { return hebbian; }
    public void setHebbian(HebbianProperties hebbian) { this.hebbian = hebbian; }
    public HebbianProperties hebbian() { return hebbian; }

    public StdpProperties getStdp() { return stdp; }
    public void setStdp(StdpProperties stdp) { this.stdp = stdp; }
    public StdpProperties stdp() { return stdp; }

    public BridgeProperties getBridge() { return bridge; }
    public void setBridge(BridgeProperties bridge) { this.bridge = bridge; }
    public BridgeProperties bridge() { return bridge; }

    public EntityGraphProperties getEntity() { return entity; }
    public void setEntity(EntityGraphProperties entity) { this.entity = entity; }
    public EntityGraphProperties entity() { return entity; }

    public static class HebbianProperties implements Serializable {
        private int maxDegree = 24;
        private long sessionBoundaryMs = 300000L;
        private float promotionMinWeight = 3.0f;
        private float decayFactor = 0.9f;
        private float decayFloor = 0.10f;
        private float activationCutoff = 0.01f;
        private float hopAttenuation = 0.50f;
        private float defaultWeightDelta = 1.0f;
        private int neutralBridgeScore = 128;

        public int getMaxDegree() { return maxDegree; }
        public void setMaxDegree(int maxDegree) { this.maxDegree = maxDegree; }
        public int maxDegree() { return maxDegree; }

        public long getSessionBoundaryMs() { return sessionBoundaryMs; }
        public void setSessionBoundaryMs(long sessionBoundaryMs) { this.sessionBoundaryMs = sessionBoundaryMs; }
        public long sessionBoundaryMs() { return sessionBoundaryMs; }

        public float getPromotionMinWeight() { return promotionMinWeight; }
        public void setPromotionMinWeight(float promotionMinWeight) { this.promotionMinWeight = promotionMinWeight; }
        public float promotionMinWeight() { return promotionMinWeight; }

        public float getDecayFactor() { return decayFactor; }
        public void setDecayFactor(float decayFactor) { this.decayFactor = decayFactor; }
        public float decayFactor() { return decayFactor; }

        public float getDecayFloor() { return decayFloor; }
        public void setDecayFloor(float decayFloor) { this.decayFloor = decayFloor; }
        public float decayFloor() { return decayFloor; }

        public float getActivationCutoff() { return activationCutoff; }
        public void setActivationCutoff(float activationCutoff) { this.activationCutoff = activationCutoff; }
        public float activationCutoff() { return activationCutoff; }

        public float getHopAttenuation() { return hopAttenuation; }
        public void setHopAttenuation(float hopAttenuation) { this.hopAttenuation = hopAttenuation; }
        public float hopAttenuation() { return hopAttenuation; }

        public float getDefaultWeightDelta() { return defaultWeightDelta; }
        public void setDefaultWeightDelta(float defaultWeightDelta) { this.defaultWeightDelta = defaultWeightDelta; }
        public float defaultWeightDelta() { return defaultWeightDelta; }

        public int getNeutralBridgeScore() { return neutralBridgeScore; }
        public void setNeutralBridgeScore(int neutralBridgeScore) { this.neutralBridgeScore = neutralBridgeScore; }
        public int neutralBridgeScore() { return neutralBridgeScore; }
    }

    public static class StdpProperties implements Serializable {
        private float aPlus = 0.1f;
        private float aMinus = 0.05f;
        private float tauPlus = 30000f;
        private float tauMinus = 30000f;

        public float getAPlus() { return aPlus; }
        public void setAPlus(float aPlus) { this.aPlus = aPlus; }
        public float aPlus() { return aPlus; }

        public float getAMinus() { return aMinus; }
        public void setAMinus(float aMinus) { this.aMinus = aMinus; }
        public float aMinus() { return aMinus; }

        public float getTauPlus() { return tauPlus; }
        public void setTauPlus(float tauPlus) { this.tauPlus = tauPlus; }
        public float tauPlus() { return tauPlus; }

        public float getTauMinus() { return tauMinus; }
        public void setTauMinus(float tauMinus) { this.tauMinus = tauMinus; }
        public float tauMinus() { return tauMinus; }
    }

    public static class BridgeProperties implements Serializable {
        private int sampleCount = 15;
        private long budgetMs = 500L;

        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
        public int sampleCount() { return sampleCount; }

        public long getBudgetMs() { return budgetMs; }
        public void setBudgetMs(long budgetMs) { this.budgetMs = budgetMs; }
        public long budgetMs() { return budgetMs; }
    }

    public static class EntityGraphProperties implements Serializable {
        private String extractionMode = "NONE";
        private boolean resolutionEnabled = false;
        private boolean shadowMode = true;
        private int maxDegree = 16;
        private int maxPerMemory = 10;
        private float cosineThreshold = 0.85f;
        private int retentionDays = 7;
        private float decayFactor = 0.95f;
        private float pruneThreshold = 0.5f;
        private float adjDecayFactor = 0.95f;
        private float adjPruneThreshold = 0.2f;
        private int mergeDistance = 2;

        public String getExtractionMode() { return extractionMode; }
        public void setExtractionMode(String extractionMode) { this.extractionMode = extractionMode; }
        public String extractionMode() { return extractionMode; }

        public boolean isResolutionEnabled() { return resolutionEnabled; }
        public void setResolutionEnabled(boolean resolutionEnabled) { this.resolutionEnabled = resolutionEnabled; }
        public boolean resolutionEnabled() { return resolutionEnabled; }

        public boolean isShadowMode() { return shadowMode; }
        public void setShadowMode(boolean shadowMode) { this.shadowMode = shadowMode; }
        public boolean shadowMode() { return shadowMode; }

        public int getMaxDegree() { return maxDegree; }
        public void setMaxDegree(int maxDegree) { this.maxDegree = maxDegree; }
        public int maxDegree() { return maxDegree; }

        public int getMaxPerMemory() { return maxPerMemory; }
        public void setMaxPerMemory(int maxPerMemory) { this.maxPerMemory = maxPerMemory; }
        public int maxPerMemory() { return maxPerMemory; }

        public float getCosineThreshold() { return cosineThreshold; }
        public void setCosineThreshold(float cosineThreshold) { this.cosineThreshold = cosineThreshold; }
        public float cosineThreshold() { return cosineThreshold; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public int retentionDays() { return retentionDays; }

        public float getDecayFactor() { return decayFactor; }
        public void setDecayFactor(float decayFactor) { this.decayFactor = decayFactor; }
        public float decayFactor() { return decayFactor; }

        public float getPruneThreshold() { return pruneThreshold; }
        public void setPruneThreshold(float pruneThreshold) { this.pruneThreshold = pruneThreshold; }
        public float pruneThreshold() { return pruneThreshold; }

        public float getAdjDecayFactor() { return adjDecayFactor; }
        public void setAdjDecayFactor(float adjDecayFactor) { this.adjDecayFactor = adjDecayFactor; }
        public float adjDecayFactor() { return adjDecayFactor; }

        public float getAdjPruneThreshold() { return adjPruneThreshold; }
        public void setAdjPruneThreshold(float adjPruneThreshold) { this.adjPruneThreshold = adjPruneThreshold; }
        public float adjPruneThreshold() { return adjPruneThreshold; }

        public int getMergeDistance() { return mergeDistance; }
        public void setMergeDistance(int mergeDistance) { this.mergeDistance = mergeDistance; }
        public int mergeDistance() { return mergeDistance; }
    }
}
