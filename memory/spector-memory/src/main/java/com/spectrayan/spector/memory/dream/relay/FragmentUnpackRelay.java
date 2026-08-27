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
import com.spectrayan.spector.memory.graph.EntityDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Stage 3 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Constructive Episodic Simulation Fragment Decomposition (Schacter & Addis, 2007)</h3>
 * <p>Decomposes intact episodic traces into typed constituent semantic primitives (AGENT, ACTION,
 * OBJECT, LOCATION, TEMPORAL, AFFECT) enabling flexible, combinatorial offline recombination.</p>
 *
 * @since 1.4.0
 */
public final class FragmentUnpackRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(FragmentUnpackRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.seedMemoryIds().isEmpty()) {
            return true;
        }

        List<String> seedIds = signal.seedMemoryIds();
        List<float[]> seedVectors = signal.seedVectors();
        EntityDirectory entityDir = signal.entityDirectory();

        for (int i = 0; i < seedIds.size(); i++) {
            String seedId = seedIds.get(i);
            float[] vector = i < seedVectors.size() ? seedVectors.get(i) : new float[0];
            int dim = vector.length;

            // Extract affective charge from vector energy
            byte valence = computeValence(vector);
            int arousal = computeArousal(vector);

            // 1. Agent Fragment (Head quadrant)
            float[] agentVec = dim > 0 ? Arrays.copyOfRange(vector, 0, Math.max(1, dim / 4)) : new float[0];
            signal.addFragment(new SceneFragment(
                    seedId,
                    i * 10 + 1,
                    "Agent_" + seedId,
                    FragmentRole.AGENT,
                    agentVec,
                    valence,
                    arousal
            ));

            // 2. Action Fragment (Action quadrant)
            float[] actionVec = dim >= 2 ? Arrays.copyOfRange(vector, dim / 4, Math.max(2, dim / 2)) : new float[0];
            signal.addFragment(new SceneFragment(
                    seedId,
                    i * 10 + 2,
                    "Action_" + seedId,
                    FragmentRole.ACTION,
                    actionVec,
                    valence,
                    arousal
            ));

            // 3. Object Fragment (Object quadrant)
            float[] objectVec = dim >= 3 ? Arrays.copyOfRange(vector, dim / 2, Math.max(3, (3 * dim) / 4)) : new float[0];
            signal.addFragment(new SceneFragment(
                    seedId,
                    i * 10 + 3,
                    "Object_" + seedId,
                    FragmentRole.OBJECT,
                    objectVec,
                    valence,
                    arousal
            ));

            // 4. Context / Location Fragment (Tail quadrant)
            float[] locationVec = dim >= 4 ? Arrays.copyOfRange(vector, (3 * dim) / 4, dim) : new float[0];
            signal.addFragment(new SceneFragment(
                    seedId,
                    i * 10 + 4,
                    "Location_" + seedId,
                    FragmentRole.LOCATION,
                    locationVec,
                    valence,
                    arousal
            ));

            // 5. Affective Tone Fragment
            signal.addFragment(new SceneFragment(
                    seedId,
                    i * 10 + 5,
                    "Affect_" + seedId,
                    FragmentRole.AFFECT,
                    vector,
                    valence,
                    arousal
            ));
        }

        if (log.isDebugEnabled()) {
            log.debug("FragmentUnpackRelay: unpacked {} fragments from {} salient seeds",
                    signal.fragments().size(), seedIds.size());
        }

        return true;
    }

    private static byte computeValence(float[] vec) {
        if (vec == null || vec.length == 0) return 0;
        float sum = 0.0f;
        for (int i = 0; i < Math.min(vec.length, 8); i++) {
            sum += vec[i];
        }
        return (byte) Math.max(-50, Math.min(50, (int) (sum * 25.0f)));
    }

    private static int computeArousal(float[] vec) {
        if (vec == null || vec.length == 0) return 30;
        float normSq = 0.0f;
        for (float v : vec) {
            normSq += v * v;
        }
        return Math.min(100, Math.max(1, (int) (Math.sqrt(normSq) * 30.0)));
    }

    @Override
    public String relayName() {
        return "fragment_unpack";
    }
}
