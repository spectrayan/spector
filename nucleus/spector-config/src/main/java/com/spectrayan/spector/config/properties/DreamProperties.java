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
import java.util.Objects;

/**
 * Configuration properties POJO for DreamPathway &amp; Generative Cognition (#679, #681).
 */
public class DreamProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = DEFAULT_MEMORY_DREAM_ENABLED;
    private float noiseScale = DEFAULT_MEMORY_DREAM_NOISE_SCALE;
    private float temperatureRem = DEFAULT_MEMORY_DREAM_TEMPERATURE_REM;
    private float temperatureDaydream = DEFAULT_MEMORY_DREAM_TEMPERATURE_DAYDREAM;
    private float temperatureThought = DEFAULT_MEMORY_DREAM_TEMPERATURE_THOUGHT;
    private int maxDreamsPerCycle = DEFAULT_MEMORY_DREAM_MAX_DREAMS_PER_CYCLE;
    private int maxCounterfactualsPerSeed = DEFAULT_MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED;
    private float persistenceThreshold = DEFAULT_MEMORY_DREAM_PERSISTENCE_THRESHOLD;
    private float langevinStepSize = DEFAULT_MEMORY_DREAM_LANGEVIN_STEP_SIZE;
    private int langevinSteps = DEFAULT_MEMORY_DREAM_LANGEVIN_STEPS;
    private float noveltyRadius = DEFAULT_MEMORY_DREAM_NOVELTY_RADIUS;
    private float hebbianInhibitionDelta = DEFAULT_MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA;
    private boolean journalEnabled = DEFAULT_MEMORY_DREAM_JOURNAL_ENABLED;
    private int cycleFrequency = DEFAULT_MEMORY_DREAM_CYCLE_FREQUENCY;

    private float seedWeightRecency = DEFAULT_MEMORY_DREAM_SEED_WEIGHT_RECENCY;
    private float seedWeightNovelty = DEFAULT_MEMORY_DREAM_SEED_WEIGHT_NOVELTY;
    private float seedWeightSoul = DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SOUL;
    private float seedWeightSalience = DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SALIENCE;
    private float identityResonanceThreshold = DEFAULT_MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD;
    private float ethicalViolationThreshold = DEFAULT_MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD;
    private float langevinSoulAttractorLambda = DEFAULT_MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA;
    private float hartmannOpennessMultiplier = DEFAULT_MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER;
    private float hartmannVigilanceMultiplier = DEFAULT_MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER;

    public DreamProperties() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public float getNoiseScale() { return noiseScale; }
    public void setNoiseScale(float noiseScale) { this.noiseScale = noiseScale; }

    public float getTemperatureRem() { return temperatureRem; }
    public void setTemperatureRem(float temperatureRem) { this.temperatureRem = temperatureRem; }

    public float getTemperatureDaydream() { return temperatureDaydream; }
    public void setTemperatureDaydream(float temperatureDaydream) { this.temperatureDaydream = temperatureDaydream; }

    public float getTemperatureThought() { return temperatureThought; }
    public void setTemperatureThought(float temperatureThought) { this.temperatureThought = temperatureThought; }

    public int getMaxDreamsPerCycle() { return maxDreamsPerCycle; }
    public void setMaxDreamsPerCycle(int maxDreamsPerCycle) { this.maxDreamsPerCycle = maxDreamsPerCycle; }

    public int getMaxCounterfactualsPerSeed() { return maxCounterfactualsPerSeed; }
    public void setMaxCounterfactualsPerSeed(int maxCounterfactualsPerSeed) { this.maxCounterfactualsPerSeed = maxCounterfactualsPerSeed; }

    public float getPersistenceThreshold() { return persistenceThreshold; }
    public void setPersistenceThreshold(float persistenceThreshold) { this.persistenceThreshold = persistenceThreshold; }

    public float getLangevinStepSize() { return langevinStepSize; }
    public void setLangevinStepSize(float langevinStepSize) { this.langevinStepSize = langevinStepSize; }

    public int getLangevinSteps() { return langevinSteps; }
    public void setLangevinSteps(int langevinSteps) { this.langevinSteps = langevinSteps; }

    public float getNoveltyRadius() { return noveltyRadius; }
    public void setNoveltyRadius(float noveltyRadius) { this.noveltyRadius = noveltyRadius; }

    public float getHebbianInhibitionDelta() { return hebbianInhibitionDelta; }
    public void setHebbianInhibitionDelta(float hebbianInhibitionDelta) { this.hebbianInhibitionDelta = hebbianInhibitionDelta; }

    public boolean isJournalEnabled() { return journalEnabled; }
    public void setJournalEnabled(boolean journalEnabled) { this.journalEnabled = journalEnabled; }

    public int getCycleFrequency() { return cycleFrequency; }
    public void setCycleFrequency(int cycleFrequency) { this.cycleFrequency = cycleFrequency; }

    public float getSeedWeightRecency() { return seedWeightRecency; }
    public void setSeedWeightRecency(float seedWeightRecency) { this.seedWeightRecency = seedWeightRecency; }

    public float getSeedWeightNovelty() { return seedWeightNovelty; }
    public void setSeedWeightNovelty(float seedWeightNovelty) { this.seedWeightNovelty = seedWeightNovelty; }

    public float getSeedWeightSoul() { return seedWeightSoul; }
    public void setSeedWeightSoul(float seedWeightSoul) { this.seedWeightSoul = seedWeightSoul; }

    public float getSeedWeightSalience() { return seedWeightSalience; }
    public void setSeedWeightSalience(float seedWeightSalience) { this.seedWeightSalience = seedWeightSalience; }

    public float getIdentityResonanceThreshold() { return identityResonanceThreshold; }
    public void setIdentityResonanceThreshold(float identityResonanceThreshold) { this.identityResonanceThreshold = identityResonanceThreshold; }

    public float getEthicalViolationThreshold() { return ethicalViolationThreshold; }
    public void setEthicalViolationThreshold(float ethicalViolationThreshold) { this.ethicalViolationThreshold = ethicalViolationThreshold; }

    public float getLangevinSoulAttractorLambda() { return langevinSoulAttractorLambda; }
    public void setLangevinSoulAttractorLambda(float langevinSoulAttractorLambda) { this.langevinSoulAttractorLambda = langevinSoulAttractorLambda; }

    public float getHartmannOpennessMultiplier() { return hartmannOpennessMultiplier; }
    public void setHartmannOpennessMultiplier(float hartmannOpennessMultiplier) { this.hartmannOpennessMultiplier = hartmannOpennessMultiplier; }

    public float getHartmannVigilanceMultiplier() { return hartmannVigilanceMultiplier; }
    public void setHartmannVigilanceMultiplier(float hartmannVigilanceMultiplier) { this.hartmannVigilanceMultiplier = hartmannVigilanceMultiplier; }

    // ─────────────── Record-Style Accessors & Runtime Helpers ───────────────

    public boolean enabled() { return isEnabled(); }
    public float noiseScale() { return getNoiseScale(); }
    public float dreamNoiseScale() { return getNoiseScale(); }
    public float temperatureRem() { return getTemperatureRem(); }
    public float dreamTemperatureRem() { return getTemperatureRem(); }
    public float temperatureDaydream() { return getTemperatureDaydream(); }
    public float dreamTemperatureDaydream() { return getTemperatureDaydream(); }
    public float temperatureThought() { return getTemperatureThought(); }
    public float dreamTemperatureThought() { return getTemperatureThought(); }
    public int maxDreamsPerCycle() { return getMaxDreamsPerCycle(); }
    public int maxCounterfactualsPerSeed() { return getMaxCounterfactualsPerSeed(); }
    public float persistenceThreshold() { return getPersistenceThreshold(); }
    public float langevinStepSize() { return getLangevinStepSize(); }
    public int langevinSteps() { return getLangevinSteps(); }
    public float noveltyRadius() { return getNoveltyRadius(); }
    public float hebbianInhibitionDelta() { return getHebbianInhibitionDelta(); }
    public boolean journalEnabled() { return isJournalEnabled(); }
    public int cycleFrequency() { return getCycleFrequency(); }
    public int dreamCycleFrequency() { return getCycleFrequency(); }
    public float seedWeightRecency() { return getSeedWeightRecency(); }
    public float seedWeightNovelty() { return getSeedWeightNovelty(); }
    public float seedWeightSoul() { return getSeedWeightSoul(); }
    public float seedWeightSalience() { return getSeedWeightSalience(); }
    public float identityResonanceThreshold() { return getIdentityResonanceThreshold(); }
    public float ethicalViolationThreshold() { return getEthicalViolationThreshold(); }
    public float langevinSoulAttractorLambda() { return getLangevinSoulAttractorLambda(); }
    public float hartmannOpennessMultiplier() { return getHartmannOpennessMultiplier(); }
    public float hartmannVigilanceMultiplier() { return getHartmannVigilanceMultiplier(); }

    public static DreamProperties defaultConfig() { return new DreamProperties(); }
    public static DreamProperties disabled() {
        DreamProperties props = new DreamProperties();
        props.setEnabled(false);
        return props;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DreamProperties that = (DreamProperties) o;
        return enabled == that.enabled &&
                Float.compare(that.noiseScale, noiseScale) == 0 &&
                Float.compare(that.temperatureRem, temperatureRem) == 0 &&
                Float.compare(that.temperatureDaydream, temperatureDaydream) == 0 &&
                Float.compare(that.temperatureThought, temperatureThought) == 0 &&
                maxDreamsPerCycle == that.maxDreamsPerCycle &&
                maxCounterfactualsPerSeed == that.maxCounterfactualsPerSeed &&
                Float.compare(that.persistenceThreshold, persistenceThreshold) == 0 &&
                Float.compare(that.langevinStepSize, langevinStepSize) == 0 &&
                langevinSteps == that.langevinSteps &&
                Float.compare(that.noveltyRadius, noveltyRadius) == 0 &&
                Float.compare(that.hebbianInhibitionDelta, hebbianInhibitionDelta) == 0 &&
                journalEnabled == that.journalEnabled &&
                cycleFrequency == that.cycleFrequency &&
                Float.compare(that.seedWeightRecency, seedWeightRecency) == 0 &&
                Float.compare(that.seedWeightNovelty, seedWeightNovelty) == 0 &&
                Float.compare(that.seedWeightSoul, seedWeightSoul) == 0 &&
                Float.compare(that.seedWeightSalience, seedWeightSalience) == 0 &&
                Float.compare(that.identityResonanceThreshold, identityResonanceThreshold) == 0 &&
                Float.compare(that.ethicalViolationThreshold, ethicalViolationThreshold) == 0 &&
                Float.compare(that.langevinSoulAttractorLambda, langevinSoulAttractorLambda) == 0 &&
                Float.compare(that.hartmannOpennessMultiplier, hartmannOpennessMultiplier) == 0 &&
                Float.compare(that.hartmannVigilanceMultiplier, hartmannVigilanceMultiplier) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, noiseScale, temperatureRem, temperatureDaydream, temperatureThought,
                maxDreamsPerCycle, maxCounterfactualsPerSeed, persistenceThreshold, langevinStepSize,
                langevinSteps, noveltyRadius, hebbianInhibitionDelta, journalEnabled, cycleFrequency,
                seedWeightRecency, seedWeightNovelty, seedWeightSoul, seedWeightSalience,
                identityResonanceThreshold, ethicalViolationThreshold, langevinSoulAttractorLambda,
                hartmannOpennessMultiplier, hartmannVigilanceMultiplier);
    }
}
