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

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;

public record PhenomenologicalContextPack(
        String internalMonologue,
        String systemPromptDirectives,
        ProsodyParameterVector prosodyVector,
        BlendshapeVector blendshapeVector,
        List<CognitiveResult> groundedMemories,
        InteroceptiveState affectiveState,
        String soulIdentity) {

    public PhenomenologicalContextPack {
        groundedMemories = groundedMemories != null ? Collections.unmodifiableList(new ArrayList<>(groundedMemories)) : Collections.emptyList();
    }

    public String toSummary() {
        return String.format("Monologue: %s, Directives: %s", internalMonologue, systemPromptDirectives);
    }
}
