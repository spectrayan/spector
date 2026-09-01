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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Biological Analog: Endogenous parameters regulating sleep cycles, plasticity windows,
 * neurotransmitter thresholds, and soul-conditioned salience during memory consolidation and dream generation.
 *
 * @since 1.4.0
 */
public record DreamConfig(
        boolean enabled,
        float dreamNoiseScale,
        float dreamTemperatureRem,
        float dreamTemperatureDaydream,
        float dreamTemperatureThought,
        int maxDreamsPerCycle,
        int maxCounterfactualsPerSeed,
        float persistenceThreshold,
        float langevinStepSize,
        int langevinSteps,
        float noveltyRadius,
        float hebbianInhibitionDelta,
        boolean journalEnabled,
        int dreamCycleFrequency,
        float seedWeightRecency,
        float seedWeightNovelty,
        float seedWeightSoul,
        float seedWeightSalience,
        float identityResonanceThreshold,
        float ethicalViolationThreshold,
        float langevinSoulAttractorLambda,
        float hartmannOpennessMultiplier,
        float hartmannVigilanceMultiplier
) {
    public DreamConfig {
        if (Float.isNaN(dreamNoiseScale) || dreamNoiseScale < 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dreamNoiseScale must be non-negative");
        }
        if (Float.isNaN(dreamTemperatureRem) || dreamTemperatureRem <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dreamTemperatureRem must be positive");
        }
        if (Float.isNaN(dreamTemperatureDaydream) || dreamTemperatureDaydream <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dreamTemperatureDaydream must be positive");
        }
        if (Float.isNaN(dreamTemperatureThought) || dreamTemperatureThought <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dreamTemperatureThought must be positive");
        }
        if (maxDreamsPerCycle < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxDreamsPerCycle must be at least 1");
        }
        if (maxCounterfactualsPerSeed < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "maxCounterfactualsPerSeed must be at least 1");
        }
        if (Float.isNaN(persistenceThreshold) || persistenceThreshold < 0.0f || persistenceThreshold > 1.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "persistenceThreshold must be in [0, 1]");
        }
        if (Float.isNaN(langevinStepSize) || langevinStepSize <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "langevinStepSize must be positive");
        }
        if (langevinSteps < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "langevinSteps must be non-negative");
        }
        if (Float.isNaN(noveltyRadius) || noveltyRadius <= 0.0f) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "noveltyRadius must be positive");
        }
        if (dreamCycleFrequency < 1) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "dreamCycleFrequency must be at least 1");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DreamConfig defaultConfig() {
        return new Builder().build();
    }

    public static DreamConfig disabled() {
        return new Builder().enabled(false).build();
    }

    public static final class Builder {
        private boolean enabled = true;
        private float dreamNoiseScale = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_NOISE_SCALE;
        private float dreamTemperatureRem = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_TEMPERATURE_REM;
        private float dreamTemperatureDaydream = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_TEMPERATURE_DAYDREAM;
        private float dreamTemperatureThought = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_TEMPERATURE_THOUGHT;
        private int maxDreamsPerCycle = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_MAX_DREAMS_PER_CYCLE;
        private int maxCounterfactualsPerSeed = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_MAX_COUNTERFACTUALS_PER_SEED;
        private float persistenceThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_PERSISTENCE_THRESHOLD;
        private float langevinStepSize = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_LANGEVIN_STEP_SIZE;
        private int langevinSteps = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_LANGEVIN_STEPS;
        private float noveltyRadius = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_NOVELTY_RADIUS;
        private float hebbianInhibitionDelta = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_HEBBIAN_INHIBITION_DELTA;
        private boolean journalEnabled = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_JOURNAL_ENABLED;
        private int dreamCycleFrequency = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_CYCLE_FREQUENCY;
        private float seedWeightRecency = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_SEED_WEIGHT_RECENCY;
        private float seedWeightNovelty = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_SEED_WEIGHT_NOVELTY;
        private float seedWeightSoul = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SOUL;
        private float seedWeightSalience = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_SEED_WEIGHT_SALIENCE;
        private float identityResonanceThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_IDENTITY_RESONANCE_THRESHOLD;
        private float ethicalViolationThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_ETHICAL_VIOLATION_THRESHOLD;
        private float langevinSoulAttractorLambda = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_LANGEVIN_SOUL_ATTRACTOR_LAMBDA;
        private float hartmannOpennessMultiplier = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_HARTMANN_OPENNESS_MULTIPLIER;
        private float hartmannVigilanceMultiplier = SpectorPropertyConstants.DEFAULT_MEMORY_DREAM_HARTMANN_VIGILANCE_MULTIPLIER;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder dreamNoiseScale(float scale) { this.dreamNoiseScale = scale; return this; }
        public Builder dreamTemperatureRem(float temp) { this.dreamTemperatureRem = temp; return this; }
        public Builder dreamTemperatureDaydream(float temp) { this.dreamTemperatureDaydream = temp; return this; }
        public Builder dreamTemperatureThought(float temp) { this.dreamTemperatureThought = temp; return this; }
        public Builder maxDreamsPerCycle(int count) { this.maxDreamsPerCycle = count; return this; }
        public Builder maxCounterfactualsPerSeed(int count) { this.maxCounterfactualsPerSeed = count; return this; }
        public Builder persistenceThreshold(float threshold) { this.persistenceThreshold = threshold; return this; }
        public Builder langevinStepSize(float step) { this.langevinStepSize = step; return this; }
        public Builder langevinSteps(int steps) { this.langevinSteps = steps; return this; }
        public Builder noveltyRadius(float radius) { this.noveltyRadius = radius; return this; }
        public Builder hebbianInhibitionDelta(float delta) { this.hebbianInhibitionDelta = delta; return this; }
        public Builder journalEnabled(boolean enabled) { this.journalEnabled = enabled; return this; }
        public Builder dreamCycleFrequency(int freq) { this.dreamCycleFrequency = freq; return this; }
        public Builder seedWeightRecency(float w) { this.seedWeightRecency = w; return this; }
        public Builder seedWeightNovelty(float w) { this.seedWeightNovelty = w; return this; }
        public Builder seedWeightSoul(float w) { this.seedWeightSoul = w; return this; }
        public Builder seedWeightSalience(float w) { this.seedWeightSalience = w; return this; }
        public Builder identityResonanceThreshold(float t) { this.identityResonanceThreshold = t; return this; }
        public Builder ethicalViolationThreshold(float t) { this.ethicalViolationThreshold = t; return this; }
        public Builder langevinSoulAttractorLambda(float l) { this.langevinSoulAttractorLambda = l; return this; }
        public Builder hartmannOpennessMultiplier(float m) { this.hartmannOpennessMultiplier = m; return this; }
        public Builder hartmannVigilanceMultiplier(float m) { this.hartmannVigilanceMultiplier = m; return this; }

        public DreamConfig build() {
            return new DreamConfig(
                    enabled, dreamNoiseScale, dreamTemperatureRem, dreamTemperatureDaydream,
                    dreamTemperatureThought, maxDreamsPerCycle, maxCounterfactualsPerSeed,
                    persistenceThreshold, langevinStepSize, langevinSteps, noveltyRadius,
                    hebbianInhibitionDelta, journalEnabled, dreamCycleFrequency,
                    seedWeightRecency, seedWeightNovelty, seedWeightSoul, seedWeightSalience,
                    identityResonanceThreshold, ethicalViolationThreshold,
                    langevinSoulAttractorLambda, hartmannOpennessMultiplier, hartmannVigilanceMultiplier
            );
        }
    }
}
