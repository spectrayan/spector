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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.List;

public record RhetoricalPatterns(
        DirectnessLevel directness,
        HumorArchetype humor,
        float storytellingTendency,
        float socraticQuestioningRate,
        List<String> fillerPhrases,
        List<String> codeSwitchingLanguages
) {
    public enum DirectnessLevel { DIRECT, SOCRATIC, STORYTELLING_PREAMBLE, DIPLOMATIC }
    public enum HumorArchetype { NONE, DRY, WARM_AFFECTIONATE, IRONIC_WITTY, SELF_DEPRECATING, DIDACTIC }

    public static final RhetoricalPatterns NEUTRAL = new RhetoricalPatterns(
            DirectnessLevel.DIRECT, HumorArchetype.NONE, 0.2f, 0.1f, List.of(), List.of()
    );

    public RhetoricalPatterns {
        directness = directness != null ? directness : DirectnessLevel.DIRECT;
        humor = humor != null ? humor : HumorArchetype.NONE;
        fillerPhrases = fillerPhrases != null ? List.copyOf(fillerPhrases) : List.of();
        codeSwitchingLanguages = codeSwitchingLanguages != null ? List.copyOf(codeSwitchingLanguages) : List.of();
    }
}
