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

/**
 * Biological Analog: Endogenous parameters regulating sleep cycles, plasticity windows,
 * and neurotransmitter thresholds during memory consolidation.
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
        int dreamCycleFrequency
) {
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
        private float dreamNoiseScale = 0.15f;
        private float dreamTemperatureRem = 2.0f;
        private float dreamTemperatureDaydream = 1.0f;
        private float dreamTemperatureThought = 0.5f;
        private int maxDreamsPerCycle = 5;
        private int maxCounterfactualsPerSeed = 3;
        private float persistenceThreshold = 0.50f;
        private float langevinStepSize = 0.01f;
        private int langevinSteps = 100;
        private float noveltyRadius = 1.5f;
        private float hebbianInhibitionDelta = -0.05f;
        private boolean journalEnabled = true;
        private int dreamCycleFrequency = 3;

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

        public DreamConfig build() {
            return new DreamConfig(
                    enabled, dreamNoiseScale, dreamTemperatureRem, dreamTemperatureDaydream,
                    dreamTemperatureThought, maxDreamsPerCycle, maxCounterfactualsPerSeed,
                    persistenceThreshold, langevinStepSize, langevinSteps, noveltyRadius,
                    hebbianInhibitionDelta, journalEnabled, dreamCycleFrequency
            );
        }
    }
}
