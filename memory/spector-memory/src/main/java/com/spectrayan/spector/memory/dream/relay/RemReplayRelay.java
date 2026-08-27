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

import java.util.List;
import java.util.Random;

/**
 * Stage 3 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Sharp-Wave Ripple Compressed Replay with Hoel Overfitted Brain Noise Injection</h3>
 * <p>Injects noise into seed vectors to prevent overfitting.</p>
 *
 * @since 1.4.0
 */
public final class RemReplayRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(RemReplayRelay.class);

    public static final float DEFAULT_GAMMA_NOISE = 1.5f;
    public static final float DEFAULT_IMPORTANCE_RATIO = 0.5f;
    public static final float REFERENCE_TEMPERATURE = 2.0f;
    public static final double MIN_RESIDUAL_IMPORTANCE_FLOOR = 0.01;
    public static final float INITIAL_REPLAY_QUALITY = 0.0f;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.seedVectors().isEmpty()) return true;

        List<float[]> seeds = signal.seedVectors();
        List<String> seedIds = signal.seedMemoryIds();

        long randomSeed = 0;
        if (!seeds.isEmpty() && seeds.get(0).length > 0) {
            randomSeed = Float.floatToIntBits(seeds.get(0)[0]);
        }
        Random random = new Random(randomSeed);

        float sigmaMax = signal.config().dreamNoiseScale() * (signal.temperature() / REFERENCE_TEMPERATURE);

        for (int i = 0; i < seeds.size(); i++) {
            float[] vector = seeds.get(i);
            String id = seedIds.get(i);

            float importanceRatio = DEFAULT_IMPORTANCE_RATIO;
            float sigmaDream = sigmaMax * (float) Math.pow(Math.max(MIN_RESIDUAL_IMPORTANCE_FLOOR, 1.0 - importanceRatio), DEFAULT_GAMMA_NOISE);

            float[] noise = new float[vector.length];
            for (int j = 0; j < vector.length; j++) {
                noise[j] = (float) (random.nextGaussian() * sigmaDream);
            }
            float[] noisyVector = VectorOps.add(vector, noise);

            String narrative = String.format("[REM Replay: %s] Noisy compressed replay with \u03C3=%.3f", id, sigmaDream);
            String sceneId = signal.nextId();
            DreamSignal.DreamScene scene = new DreamSignal.DreamScene(
                sceneId,
                narrative,
                "",
                noisyVector,
                List.of(id),
                INITIAL_REPLAY_QUALITY,
                null
            );

            signal.constructedScenes().add(scene);
        }

        if (log.isDebugEnabled()) {
            log.debug("RemReplayRelay: constructed {} scenes with noise", signal.constructedScenes().size());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "rem_replay";
    }
}
