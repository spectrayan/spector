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
import com.spectrayan.spector.core.similarity.VectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage 6 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Compositional Episodic Scene Construction</h3>
 * <p>Recombines fragmented memory primitives into synthetic scenario representations,
 * synthesizing narrative scaffolding and blending high-dimensional latent vectors under
 * temperature-modulated regularizing noise.</p>
 *
 * @since 1.4.0
 */
public final class SceneConstructRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(SceneConstructRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.fragments().isEmpty()) {
            return true;
        }

        List<SceneFragment> fragments = signal.fragments();
        int maxScenes = signal.config().maxDreamsPerCycle();
        float temp = signal.temperature();
        float baseNoise = signal.config().dreamNoiseScale();
        float scaledNoise = baseNoise * (temp / 2.0f);

        Random random = new Random(signal.startTime().toEpochMilli() + 42L);

        // Group fragments by role for structured slot filling
        List<SceneFragment> agents = fragments.stream().filter(f -> f.role() == FragmentRole.AGENT).toList();
        List<SceneFragment> actions = fragments.stream().filter(f -> f.role() == FragmentRole.ACTION).toList();
        List<SceneFragment> objects = fragments.stream().filter(f -> f.role() == FragmentRole.OBJECT).toList();
        List<SceneFragment> locations = fragments.stream().filter(f -> f.role() == FragmentRole.LOCATION).toList();

        int count = Math.min(maxScenes, Math.max(1, fragments.size() / 3));

        for (int i = 0; i < count; i++) {
            SceneFragment agent = pickFragment(agents, fragments, random, i);
            SceneFragment action = pickFragment(actions, fragments, random, i + 1);
            SceneFragment obj = pickFragment(objects, fragments, random, i + 2);
            SceneFragment loc = pickFragment(locations, fragments, random, i + 3);

            List<String> sourceIds = new ArrayList<>();
            if (agent != null) sourceIds.add(agent.sourceMemoryId());
            if (action != null && !sourceIds.contains(action.sourceMemoryId())) sourceIds.add(action.sourceMemoryId());
            if (obj != null && !sourceIds.contains(obj.sourceMemoryId())) sourceIds.add(obj.sourceMemoryId());
            if (loc != null && !sourceIds.contains(loc.sourceMemoryId())) sourceIds.add(loc.sourceMemoryId());

            // Build structured narrative text
            String agentLabel = agent != null ? agent.entityLabel() : "Agent";
            String actionLabel = action != null ? action.entityLabel() : "interacts with";
            String objectLabel = obj != null ? obj.entityLabel() : "Object";
            String locLabel = loc != null ? loc.entityLabel() : "Environment";

            String narrative = String.format("[%s Dream] %s -> %s -> %s (Context: %s)",
                    signal.mode(), agentLabel, actionLabel, objectLabel, locLabel);

            String insightDraft = String.format("Cross-domain relation: %s linked with %s through %s",
                    agentLabel, objectLabel, actionLabel);

            // Blend fragment vectors with temperature-scaled Gaussian noise using VectorOps SIMD
            float[] blended = blendVectors(List.of(agent, action, obj, loc), scaledNoise, random);

            DreamSignal.DreamScene scene = new DreamSignal.DreamScene(
                    signal.nextId(),
                    narrative,
                    insightDraft,
                    blended,
                    sourceIds,
                    0.5f, // initial baseline prior to counterfactual probe
                    null
            );

            signal.addConstructedScene(scene);
        }

        if (log.isDebugEnabled()) {
            log.debug("SceneConstructRelay: synthesized {} compositional dream scenes (temperature={}, noise={})",
                    signal.constructedScenes().size(), temp, scaledNoise);
        }

        return true;
    }

    private static SceneFragment pickFragment(List<SceneFragment> roleList, List<SceneFragment> all, Random rng, int idx) {
        if (roleList != null && !roleList.isEmpty()) {
            return roleList.get(idx % roleList.size());
        }
        return !all.isEmpty() ? all.get(rng.nextInt(all.size())) : null;
    }

    private static float[] blendVectors(List<SceneFragment> frags, float noiseScale, Random rng) {
        int dim = 0;
        for (SceneFragment f : frags) {
            if (f != null && f.embedding() != null && f.embedding().length > dim) {
                dim = f.embedding().length;
            }
        }
        if (dim == 0) return new float[0];

        float[] sum = new float[dim];
        int validCount = 0;

        for (SceneFragment f : frags) {
            if (f == null || f.embedding() == null || f.embedding().length == 0) continue;
            sum = VectorOps.add(sum, f.embedding());
            validCount++;
        }

        if (validCount > 0) {
            float[] scaled = VectorOps.scale(sum, 1.0f / validCount);
            float[] noise = new float[dim];
            for (int d = 0; d < dim; d++) {
                noise[d] = (float) (rng.nextGaussian() * noiseScale);
            }
            return VectorOps.add(scaled, noise);
        }

        return sum;
    }

    @Override
    public String relayName() {
        return "scene_construct";
    }
}
