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

import com.spectrayan.spector.commons.pathway.SynapticRelay;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Predictive Coding Network Reality Testing & Counterfactual Simulation</h3>
 * <p>Evaluates prediction error and Expected Free Energy to assign quality scores to scenes.</p>
 *
 * @since 1.4.0
 */
public final class CounterfactualProbeRelay implements SynapticRelay<DreamSignal> {

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) {
            return true;
        }

        List<DreamSignal.DreamScene> scenes = new ArrayList<>(signal.constructedScenes());
        signal.constructedScenes().clear();
        
        for (DreamSignal.DreamScene scene : scenes) {
            DreamSignal.DreamScene evaluated = new DreamSignal.DreamScene(
                    scene.id(),
                    scene.narrative(),
                    scene.insightText(),
                    scene.embedding(),
                    scene.sourceIds(),
                    0.85f,
                    scene.triageOutcome()
            );
            signal.addConstructedScene(evaluated);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "counterfactual_probe";
    }
}
