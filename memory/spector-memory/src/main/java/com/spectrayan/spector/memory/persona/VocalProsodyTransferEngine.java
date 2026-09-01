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
package com.spectrayan.spector.memory.persona;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.VocalProsodyDNA;

import java.util.HashMap;
import java.util.Map;

public class VocalProsodyTransferEngine {
    public static ProsodyParameterVector compute(VocalProsodyDNA dna, InteroceptiveState state) {
        if (dna == null) return null;
        if (state == null) {
            return new ProsodyParameterVector(
                dna.baselineF0Hz(), 0f, dna.baselineWordsPerMinute(), 1f, dna.f0Variance(),
                dna.vocalTension(), 0.5f, "NEUTRAL", "", Map.of()
            );
        }

        var map = dna.modulationMap();
        float arousal = state.arousal();
        float valence = state.valence();
        float dominance = state.dominance();

        float pitchDelta = map.pitchArousalSensitivity() * arousal + map.pitchValenceSensitivity() * valence;
        float targetF0 = Math.max(50.0f, dna.baselineF0Hz() + pitchDelta);
        float tempoMultiplier = Math.max(0.5f, Math.min(2.0f, 1.0f + map.tempoArousalSensitivity() * arousal + map.tempoValenceSensitivity() * valence));
        int targetWpm = Math.round(dna.baselineWordsPerMinute() * tempoMultiplier);
        float pitchVariance = Math.max(5.0f, dna.f0Variance() * (1.0f + map.varianceArousalSensitivity() * arousal));
        float breathiness = Math.max(0.0f, Math.min(1.0f, dna.vocalTension() * (1.0f - map.breathinessDominanceSensitivity() * dominance)));
        float assertiveness = Math.max(0.0f, Math.min(1.0f, 0.5f + map.assertivenessDominanceSensitivity() * dominance));

        String emotionalTone = "NEUTRAL";
        if (arousal > 0.3 && valence > 0.3) emotionalTone = "HIGH_AROUSAL_POSITIVE";
        else if (arousal > 0.3 && valence < -0.3) emotionalTone = "HIGH_AROUSAL_NEGATIVE";
        else if (arousal < -0.3 && valence > 0.3) emotionalTone = "LOW_AROUSAL_POSITIVE";
        else if (arousal < -0.3 && valence < -0.3) emotionalTone = "LOW_AROUSAL_NEGATIVE";

        String sign = pitchDelta >= 0 ? "+" : "";
        String ssmlTags = String.format("<prosody pitch=\"%s%.1fHz\" rate=\"%d%%\">", sign, pitchDelta, Math.round(tempoMultiplier * 100));

        Map<String, Object> vendorParams = new HashMap<>();
        vendorParams.put("stability", Math.clamp(1.0f - pitchVariance / 100.0f, 0.0f, 1.0f));
        vendorParams.put("similarity_boost", 0.75f);
        vendorParams.put("style_weight", Math.clamp(assertiveness, 0.0f, 1.0f));

        return new ProsodyParameterVector(
                targetF0, pitchDelta, targetWpm, tempoMultiplier, pitchVariance,
                breathiness, assertiveness, emotionalTone, ssmlTags, vendorParams
        );
    }
}
