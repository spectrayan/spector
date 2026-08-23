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
package com.spectrayan.spector.memory.express.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.BlendshapeVector;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.PhenomenologicalContextPack;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;

import java.util.List;

public class PhenomenologicalStreamRelay implements SynapticRelay<ExpressSignal> {

    @Override
    public String relayName() {
        return "phenomenological_stream";
    }

    @Override
    public boolean transmit(ExpressSignal signal) {
        if (signal == null) {
            return true;
        }

        InteroceptiveState state = signal.interoceptiveState() != null
                ? signal.interoceptiveState()
                : InteroceptiveState.NEUTRAL;

        String query = signal.queryText() != null ? signal.queryText() : "";
        String monologue = String.format("Synthesizing response for '%s' under affective state (V=%.2f, A=%.2f, D=%.2f)",
                query, state.valence(), state.arousal(), state.dominance());

        String promptDirectives = (String) signal.attributes().getOrDefault("promptDirectives", "");
        ProsodyParameterVector prosodyVector = (ProsodyParameterVector) signal.attributes().get("prosodyVector");
        BlendshapeVector blendshapeVector = (BlendshapeVector) signal.attributes().get("blendshapeVector");

        List<CognitiveResult> groundedMemories = signal.candidates() != null
                ? signal.candidates()
                : List.of();

        String soulId = signal.soulContext() != null ? signal.soulContext().id() : "default-soul";

        PhenomenologicalContextPack pack = new PhenomenologicalContextPack(
                monologue,
                promptDirectives,
                prosodyVector,
                blendshapeVector,
                groundedMemories,
                state,
                soulId
        );

        signal.attributes().put("contextPack", pack);
        return true;
    }
}
