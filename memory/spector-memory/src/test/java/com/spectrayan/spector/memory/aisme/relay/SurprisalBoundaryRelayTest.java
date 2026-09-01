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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.segmentation.BayesianOnlineChangePointDetector;
import com.spectrayan.spector.memory.aisme.segmentation.BoundaryReason;
import com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment;
import com.spectrayan.spector.memory.aisme.segmentation.SurprisalBoundaryDetector;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Unit tests for {@link SurprisalBoundaryRelay}.
 */
class SurprisalBoundaryRelayTest {

    private static final int DIMENSIONS = 8;
    private SurprisalBoundaryRelay relay;
    private MentalStateTracker tracker;
    private List<EpisodicSegment> emittedSegments;

    @BeforeEach
    void setUp() {
        float[] base = new float[DIMENSIONS];
        Arrays.fill(base, 0.4f);

        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-soul")
                .purposeEmbedding(base)
                .build();

        GenerativeSelfModel selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, DIMENSIONS);
        tracker = new MentalStateTracker(selfModel);

        BayesianOnlineChangePointDetector bocpd = new BayesianOnlineChangePointDetector(
                DIMENSIONS, 50.0f, 50, selfModel.priorMean(), selfModel.observationPrecision()
        );
        SurprisalBoundaryDetector detector = new SurprisalBoundaryDetector(bocpd, 0.65f, 1.50f, 10);

        emittedSegments = new ArrayList<>();
        relay = new SurprisalBoundaryRelay(detector, tracker, emittedSegments::add);
    }

    @Test
    @DisplayName("transmit: Buffers frames and emits packaged EpisodicSegment on boundary trigger")
    void transmit_buffersAndEmitsSegmentOnBoundary() {
        assertThat(relay.relayName()).isEqualTo(RelayNames.SURPRISAL_BOUNDARY_SEGMENTATION);

        // Frame 1-3: Standard baseline
        for (int i = 0; i < 3; i++) {
            float[] frame = new float[DIMENSIONS];
            Arrays.fill(frame, 0.4f);
            RememberSignal signal = RememberSignal.forCognitive(
                    "f-" + i, "baseline " + i, frame, MemoryType.SEMANTIC,
                    new String[]{"base"}, null, null, SalienceProfile.NEUTRAL, (short) 1
            );
            relay.transmit(signal);
            assertThat(signal.episodicSegment()).isNull();
        }

        assertThat(relay.bufferedFrameCount()).isEqualTo(3);
        assertThat(emittedSegments).isEmpty();

        // Frame 4: Shock frame triggering surprisal boundary cut
        float[] shock = new float[DIMENSIONS];
        Arrays.fill(shock, 3.5f);
        RememberSignal shockSignal = RememberSignal.forCognitive(
                "f-3", "shock event", shock, MemoryType.SEMANTIC,
                new String[]{"shock"}, null, null, SalienceProfile.NEUTRAL, (short) 1
        );

        relay.transmit(shockSignal);

        assertThat(emittedSegments).hasSize(1);
        EpisodicSegment segment = emittedSegments.getFirst();

        assertThat(segment.frameCount()).isEqualTo(3);
        assertThat(segment.boundaryReason()).isEqualTo(BoundaryReason.SURPRISAL_SPIKE);
        assertThat(shockSignal.episodicSegment()).isNotNull();
        assertThat(shockSignal.episodicSegment().segmentId()).isEqualTo(segment.segmentId());

        // Previous buffer emitted, new buffer initialized with current shock frame
        assertThat(relay.bufferedFrameCount()).isEqualTo(1);
    }
}
