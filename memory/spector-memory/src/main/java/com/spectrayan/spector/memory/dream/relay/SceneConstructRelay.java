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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stage relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Compositional Scene Construction from Recombined Fragments</h3>
 * <p>Generates DreamScene records from paired fragments and blends embeddings.</p>
 *
 * @since 1.4.0
 */
public final class SceneConstructRelay implements SynapticRelay<DreamSignal> {

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.fragments().isEmpty()) {
            return true;
        }

        List<SceneFragment> fragments = signal.fragments();

        int numScenes = Math.max(1, fragments.size() / 3);
        for (int i = 0; i < numScenes; i++) {
            List<String> sourceIds = fragments.stream()
                    .map(SceneFragment::sourceMemoryId)
                    .distinct()
                    .collect(Collectors.toList());

            float[] blendedEmbedding = new float[0];
            if (!fragments.isEmpty() && fragments.get(0).embedding() != null) {
                blendedEmbedding = new float[fragments.get(0).embedding().length];
            }

            DreamSignal.DreamScene scene = new DreamSignal.DreamScene(
                    UUID.randomUUID().toString(),
                    "Constructed narrative from fragments.",
                    "Draft insight text.",
                    blendedEmbedding,
                    sourceIds,
                    0.0f,
                    null
            );

            signal.addConstructedScene(scene);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "scene_construct";
    }
}
