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
package com.spectrayan.spector.memory.model;

public record VocalProsodyDNA(
        float baselineF0Hz,
        float f0Variance,
        int baselineWordsPerMinute,
        float vocalTension,
        String accent,
        String voiceId,
        AcousticModulationMap modulationMap
) {
    public static final VocalProsodyDNA NEUTRAL = new VocalProsodyDNA(
            140.0f, 20.0f, 150, 0.5f, "Neutral", null, AcousticModulationMap.DEFAULT
    );

    public VocalProsodyDNA {
        if (modulationMap == null) modulationMap = AcousticModulationMap.DEFAULT;
        if (accent == null) accent = "Neutral";
        if (baselineF0Hz <= 0) baselineF0Hz = 140.0f;
        if (baselineWordsPerMinute <= 0) baselineWordsPerMinute = 150;
    }

    public boolean isPresent() {
        return !this.equals(NEUTRAL);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float baselineF0Hz = 140.0f;
        private float f0Variance = 20.0f;
        private int baselineWordsPerMinute = 150;
        private float vocalTension = 0.5f;
        private String accent = "Neutral";
        private String voiceId;
        private AcousticModulationMap modulationMap = AcousticModulationMap.DEFAULT;

        public Builder baselineF0Hz(float baselineF0Hz) { this.baselineF0Hz = baselineF0Hz; return this; }
        public Builder f0Variance(float f0Variance) { this.f0Variance = f0Variance; return this; }
        public Builder baselineWordsPerMinute(int baselineWordsPerMinute) { this.baselineWordsPerMinute = baselineWordsPerMinute; return this; }
        public Builder vocalTension(float vocalTension) { this.vocalTension = vocalTension; return this; }
        public Builder accent(String accent) { this.accent = accent; return this; }
        public Builder voiceId(String voiceId) { this.voiceId = voiceId; return this; }
        public Builder modulationMap(AcousticModulationMap modulationMap) { this.modulationMap = modulationMap; return this; }

        public VocalProsodyDNA build() {
            return new VocalProsodyDNA(baselineF0Hz, f0Variance, baselineWordsPerMinute, vocalTension, accent, voiceId, modulationMap);
        }
    }
}
