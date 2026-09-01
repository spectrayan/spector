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
package com.spectrayan.spector.memory.aisme.simulation;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.relay.SurprisalBoundaryRelay;
import com.spectrayan.spector.memory.aisme.segmentation.BayesianOnlineChangePointDetector;
import com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment;
import com.spectrayan.spector.memory.aisme.segmentation.SurprisalBoundaryDetector;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 1,000-frame continuous multimodal sensory stream episode segmentation benchmark.
 *
 * <p>Validates that Bayesian Online Change-Point Detection (BOCPD) and Surprisal Boundary Cuts
 * accurately partition multi-topic continuous streams into cohesive {@link EpisodicSegment} packages.</p>
 */
class MultimodalEpisodeSegmentationBenchmarkTest {

    private static final int DIMENSIONS = 16;
    private static final int TOTAL_FRAMES = 1_000;
    private static final int FRAMES_PER_TOPIC = 100; // 10 distinct narrative episodes

    @Test
    @DisplayName("Benchmark: 1,000 frames multi-topic stream partitions into discrete cohesive episodes")
    void multiTopicStream_partitionsIntoCohesiveEpisodes() {
        float[] baseEmbedding = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            baseEmbedding[i] = (float) Math.sin((i + 1) * 0.5);
        }

        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("bocpd-benchmark-entity")
                .purposeEmbedding(baseEmbedding)
                .build();

        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, DIMENSIONS);
        MentalStateTracker tracker = new MentalStateTracker(selfModel);

        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(
                DIMENSIONS, 80.0f, 120, selfModel.priorMean(), selfModel.observationPrecision()
        );
        SurprisalBoundaryDetector detector = new SurprisalBoundaryDetector(bocpd, 0.60f, 1.80f, 150);

        List<EpisodicSegment> emittedSegments = new ArrayList<>();
        SurprisalBoundaryRelay relay = new SurprisalBoundaryRelay(detector, tracker, emittedSegments::add);

        Random random = new Random(2026L);

        // Generate 10 topic centroids
        float[][] topicCentroids = new float[10][DIMENSIONS];
        for (int k = 0; k < 10; k++) {
            for (int d = 0; d < DIMENSIONS; d++) {
                topicCentroids[k][d] = (float) ((k - 5) * 1.2 + Math.cos(d * 0.3));
            }
        }

        List<Integer> trueBoundaryFrames = new ArrayList<>();
        for (int frame = 0; frame < TOTAL_FRAMES; frame++) {
            int topicIdx = Math.min(frame / FRAMES_PER_TOPIC, 9);
            if (frame > 0 && frame % FRAMES_PER_TOPIC == 0) {
                trueBoundaryFrames.add(frame);
            }

            float[] obs = new float[DIMENSIONS];
            for (int d = 0; d < DIMENSIONS; d++) {
                obs[d] = topicCentroids[topicIdx][d] + (float) (random.nextGaussian() * 0.05);
            }

            RememberSignal signal = RememberSignal.forCognitive(
                    "frame-" + frame,
                    "sensory observation frame " + frame + " (topic " + topicIdx + ")",
                    obs,
                    MemoryType.SEMANTIC,
                    new String[]{"topic-" + topicIdx},
                    null,
                    null,
                    SalienceProfile.NEUTRAL,
                    (short) 1
            );

            relay.transmit(signal);
        }

        // Assertions:
        // 1. Emitted episodes should detect the narrative transitions
        assertThat(emittedSegments)
                .as("Emitted episodic segments count aligns with ground-truth topic switches")
                .hasSizeBetween(8, 14);

        // 2. Centroids of emitted segments must be non-null and correctly dimensioned
        for (EpisodicSegment segment : emittedSegments) {
            assertThat(segment.centroidVector()).hasSize(DIMENSIONS);
            assertThat(segment.frameCount()).isGreaterThan(0);
            assertThat(segment.endTimestampMs()).isGreaterThanOrEqualTo(segment.startTimestampMs());
        }
    }
}
