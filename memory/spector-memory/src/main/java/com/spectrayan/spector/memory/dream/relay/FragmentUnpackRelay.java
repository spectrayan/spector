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
import java.util.Random;

/**
 * Stage relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Constructive Episodic Simulation Fragment Decomposition (Schacter & Addis, 2007)</h3>
 * <p>Decomposes seeds into typed SceneFragments (AGENT, ACTION, OBJECT, LOCATION, TEMPORAL, AFFECT).</p>
 *
 * @since 1.4.0
 */
public final class FragmentUnpackRelay implements SynapticRelay<DreamSignal> {

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.seedMemoryIds().isEmpty()) {
            return true;
        }

        List<String> seeds = signal.seedMemoryIds();
        List<float[]> vectors = signal.seedVectors();
        Random random = new Random(signal.startTime().toEpochMilli());

        for (int i = 0; i < seeds.size(); i++) {
            String seedId = seeds.get(i);
            float[] vector = i < vectors.size() ? vectors.get(i) : new float[0];

            int fragmentCount = 2 + random.nextInt(3); // 2 to 4 fragments

            for (int j = 0; j < fragmentCount; j++) {
                FragmentRole role = FragmentRole.values()[random.nextInt(FragmentRole.values().length)];

                SceneFragment fragment = new SceneFragment(
                        seedId,
                        Math.abs(random.nextInt()),
                        role.name() + "_fragment_" + j,
                        role,
                        vector,
                        (byte) (random.nextInt(100) - 50), // valence (-50 to 49)
                        random.nextInt(100) // arousal (0 to 99)
                );
                signal.addFragment(fragment);
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return "fragment_unpack";
    }
}
