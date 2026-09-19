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
package com.spectrayan.spector.config.model;

import com.spectrayan.spector.config.properties.MemoryProperties;

import java.io.Serializable;

/**
 * Immutable snapshot of live-tunable cognitive memory parameters.
 *
 * <p>Used by the runtime configuration control plane (ADR-0085) to apply
 * thread-safe dynamic patches to an open {@code SpectorMemory} instance
 * without restarting the JVM or unmapping off-heap Panama foreign memory slabs.</p>
 */
public record LiveMemoryPatch(
        boolean decayEnabled,
        int surpriseWarmup,
        float flashbulbThreshold,
        float valenceLearningRate,
        float deduplicationRadius,
        long inhibitionTtlMs,
        float habituationDecayRate,
        long ltpCooldownMs,
        int hebbianMaxDegree,
        float hebbianDecayFactor,
        String graphExpansionMode,
        int circadianVolumeTrigger,
        float vacuumThreshold
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final LiveMemoryPatch DEFAULTS = new LiveMemoryPatch(
            true,
            10,
            3.0f,
            0.3f,
            0.05f,
            300000L,
            0.2f,
            300000L,
            24,
            0.9f,
            "GATED",
            100,
            0.20f
    );

    public static LiveMemoryPatch from(MemoryProperties props) {
        if (props == null) {
            return DEFAULTS;
        }
        var rem = props.getRemember();
        var graph = props.getGraph();
        var hebbian = graph != null ? graph.getHebbian() : null;
        var decay = props.getDecay();
        boolean decayEnabled = decay == null || decay.getMinThreshold() > 0.0;
        int surpriseWarmup = rem != null ? rem.getSurpriseWarmup() : DEFAULTS.surpriseWarmup();
        float flashbulbThreshold = rem != null ? rem.getFlashbulbThreshold() : DEFAULTS.flashbulbThreshold();
        float valenceLearningRate = rem != null ? rem.getValenceLearningRate() : DEFAULTS.valenceLearningRate();
        float deduplicationRadius = rem != null ? rem.getDeduplicationRadius() : DEFAULTS.deduplicationRadius();
        long inhibitionTtlMs = rem != null ? rem.getInhibitionTtlMs() : DEFAULTS.inhibitionTtlMs();
        float habituationDecayRate = rem != null ? rem.getHabituationDecayRate() : DEFAULTS.habituationDecayRate();
        long ltpCooldownMs = rem != null ? rem.getLtpCooldownMs() : DEFAULTS.ltpCooldownMs();
        int hebbianMaxDegree = hebbian != null ? hebbian.getMaxDegree() : DEFAULTS.hebbianMaxDegree();
        float hebbianDecayFactor = hebbian != null ? (float) hebbian.getDecayFactor() : DEFAULTS.hebbianDecayFactor();
        String graphExpansionMode = graph != null && graph.getExpansionMode() != null
                ? graph.getExpansionMode()
                : DEFAULTS.graphExpansionMode();
        int circadianVolumeTrigger = props.getCircadian() != null
                ? props.getCircadian().getVolumeTrigger()
                : DEFAULTS.circadianVolumeTrigger();
        float vacuumThreshold = props.getVacuum() != null
                ? (float) props.getVacuum().getThreshold()
                : DEFAULTS.vacuumThreshold();

        return new LiveMemoryPatch(
                decayEnabled,
                surpriseWarmup,
                flashbulbThreshold,
                valenceLearningRate,
                deduplicationRadius,
                inhibitionTtlMs,
                habituationDecayRate,
                ltpCooldownMs,
                hebbianMaxDegree,
                hebbianDecayFactor,
                graphExpansionMode,
                circadianVolumeTrigger,
                vacuumThreshold
        );
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder(DEFAULTS);
    }

    public static final class Builder {
        private boolean decayEnabled;
        private int surpriseWarmup;
        private float flashbulbThreshold;
        private float valenceLearningRate;
        private float deduplicationRadius;
        private long inhibitionTtlMs;
        private float habituationDecayRate;
        private long ltpCooldownMs;
        private int hebbianMaxDegree;
        private float hebbianDecayFactor;
        private String graphExpansionMode;
        private int circadianVolumeTrigger;
        private float vacuumThreshold;

        public Builder(LiveMemoryPatch source) {
            this.decayEnabled = source.decayEnabled();
            this.surpriseWarmup = source.surpriseWarmup();
            this.flashbulbThreshold = source.flashbulbThreshold();
            this.valenceLearningRate = source.valenceLearningRate();
            this.deduplicationRadius = source.deduplicationRadius();
            this.inhibitionTtlMs = source.inhibitionTtlMs();
            this.habituationDecayRate = source.habituationDecayRate();
            this.ltpCooldownMs = source.ltpCooldownMs();
            this.hebbianMaxDegree = source.hebbianMaxDegree();
            this.hebbianDecayFactor = source.hebbianDecayFactor();
            this.graphExpansionMode = source.graphExpansionMode();
            this.circadianVolumeTrigger = source.circadianVolumeTrigger();
            this.vacuumThreshold = source.vacuumThreshold();
        }

        public Builder decayEnabled(boolean decayEnabled) { this.decayEnabled = decayEnabled; return this; }
        public Builder surpriseWarmup(int surpriseWarmup) { this.surpriseWarmup = surpriseWarmup; return this; }
        public Builder flashbulbThreshold(float flashbulbThreshold) { this.flashbulbThreshold = flashbulbThreshold; return this; }
        public Builder valenceLearningRate(float valenceLearningRate) { this.valenceLearningRate = valenceLearningRate; return this; }
        public Builder deduplicationRadius(float deduplicationRadius) { this.deduplicationRadius = deduplicationRadius; return this; }
        public Builder inhibitionTtlMs(long inhibitionTtlMs) { this.inhibitionTtlMs = inhibitionTtlMs; return this; }
        public Builder habituationDecayRate(float habituationDecayRate) { this.habituationDecayRate = habituationDecayRate; return this; }
        public Builder ltpCooldownMs(long ltpCooldownMs) { this.ltpCooldownMs = ltpCooldownMs; return this; }
        public Builder hebbianMaxDegree(int hebbianMaxDegree) { this.hebbianMaxDegree = hebbianMaxDegree; return this; }
        public Builder hebbianDecayFactor(float hebbianDecayFactor) { this.hebbianDecayFactor = hebbianDecayFactor; return this; }
        public Builder graphExpansionMode(String graphExpansionMode) { this.graphExpansionMode = graphExpansionMode; return this; }
        public Builder circadianVolumeTrigger(int circadianVolumeTrigger) { this.circadianVolumeTrigger = circadianVolumeTrigger; return this; }
        public Builder vacuumThreshold(float vacuumThreshold) { this.vacuumThreshold = vacuumThreshold; return this; }

        public LiveMemoryPatch build() {
            return new LiveMemoryPatch(
                    decayEnabled, surpriseWarmup, flashbulbThreshold, valenceLearningRate,
                    deduplicationRadius, inhibitionTtlMs, habituationDecayRate, ltpCooldownMs,
                    hebbianMaxDegree, hebbianDecayFactor, graphExpansionMode,
                    circadianVolumeTrigger, vacuumThreshold
            );
        }
    }
}
