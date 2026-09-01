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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.expression.KinesicBlendshapeKernel;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.BlendshapeVector;
import com.spectrayan.spector.memory.model.EmbodiedKinesicsDNA;

public class EmbodiedKinesicsRelay implements SynapticRelay<ExpressSignal> {

    @Override
    public String relayName() {
        return "embodied_kinesics";
    }

    @Override
    public boolean transmit(ExpressSignal signal) {
        if (signal == null) {
            return true;
        }

        InteroceptiveState affectiveState = signal.interoceptiveState() != null
                ? signal.interoceptiveState()
                : InteroceptiveState.NEUTRAL;

        EmbodiedKinesicsDNA kinesics = (signal.personaContext() != null && signal.personaContext().embodiedKinesics() != null)
                ? signal.personaContext().embodiedKinesics()
                : EmbodiedKinesicsDNA.NEUTRAL;

        float valence = affectiveState.valence();
        float arousal = affectiveState.arousal();
        float dominance = affectiveState.dominance();
        float cognitiveLoad = (signal.candidates() != null && !signal.candidates().isEmpty())
                ? signal.candidates().get(0).score()
                : 0.5f;

        float speechActivity = signal.queryText() != null && !signal.queryText().isBlank() ? 0.8f : 0.0f;
        float asymmetryBias = kinesics.smileAsymmetry();

        float[] blendshapes = KinesicBlendshapeKernel.computeBlendshapes(valence, arousal, dominance, cognitiveLoad, speechActivity, asymmetryBias);
        float[] gazeVector = KinesicBlendshapeKernel.computeGazeVector(valence, arousal, dominance, cognitiveLoad);
        float[] headPose = KinesicBlendshapeKernel.computeHeadPose(valence, arousal, dominance, kinesics.noddingCadence());

        String expression = "NEUTRAL";
        if (valence > 0.3f && arousal > 0.2f) {
            expression = "WARM_ENGAGED";
        } else if (valence < -0.3f) {
            expression = "CONTEMPLATIVE_SOLEMN";
        } else if (cognitiveLoad > 0.7f) {
            expression = "DEEP_RECALL";
        }

        BlendshapeVector vector = new BlendshapeVector(blendshapes, gazeVector, headPose, expression);
        signal.attributes().put("blendshapeVector", vector);

        return true;
    }
}
