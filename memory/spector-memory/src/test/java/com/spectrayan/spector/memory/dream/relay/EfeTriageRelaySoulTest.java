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

import com.spectrayan.spector.memory.model.AgentSoul;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EfeTriageRelaySoulTest {

    @Test
    void testSoulResonanceIdentityTriage() {
        int dim = 4;
        float[] soulVec = new float[]{1.0f, 0.0f, 0.0f, 0.0f};

        AgentSoul soul = new AgentSoul(
                "agent-creative",
                "Creative Explorer",
                "Explores novel artistic architectures",
                "Prompt",
                "Artistic creativity",
                "creative and innovative",
                List.of("art", "design"),
                List.of("Aesthetics"),
                List.of(),
                new AgentSoul.EmotionalBaseline((byte) 30, (byte) 180),
                "expressive",
                "gpt-4",
                List.of(),
                soulVec,
                soulVec,
                (short) 1,
                Instant.now(),
                Instant.now()
        );

        EfeTriageRelay relay = new EfeTriageRelay();

        // Scene with embedding directly matching the soul's identity embedding
        float[] matchingSceneVec = new float[]{0.95f, 0.05f, 0.0f, 0.0f};
        DreamSignal.DreamScene sceneA = new DreamSignal.DreamScene(
                "scene-1",
                "Identity scene narrative",
                "Insight text",
                matchingSceneVec,
                List.of("source-1"),
                0.40f, // Normally below epistemic/pragmatic, but matches soul!
                null
        );

        // Scene with unrelated embedding
        float[] unrelatedVec = new float[]{0.0f, 1.0f, 0.0f, 0.0f};
        DreamSignal.DreamScene sceneB = new DreamSignal.DreamScene(
                "scene-2",
                "Low quality unrelated scene",
                "Insight text",
                unrelatedVec,
                List.of("source-2"),
                0.20f,
                null
        );

        DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .primarySoul(soul)
                .build();

        signal.addConstructedScene(sceneA);
        signal.addConstructedScene(sceneB);

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.constructedScenes()).hasSize(2);

        DreamSignal.DreamScene evalA = signal.constructedScenes().get(0);
        DreamSignal.DreamScene evalB = signal.constructedScenes().get(1);

        assertThat(evalA.triageOutcome()).isEqualTo(DreamSignal.TriageOutcome.IDENTITY);
        assertThat(evalB.triageOutcome()).isEqualTo(DreamSignal.TriageOutcome.NOISE);
        assertThat(signal.survivingScenes()).containsExactly(evalA);
        assertThat(signal.failedPairs().get()).isEqualTo(1);
    }

    @Test
    void testSoulLessFallbackTriage() {
        EfeTriageRelay relay = new EfeTriageRelay();

        DreamSignal.DreamScene highQ = new DreamSignal.DreamScene(
                "scene-high",
                "High quality epistemic scene",
                "Insight text",
                new float[]{1.0f, 0.0f, 0.0f, 0.0f},
                List.of("s1"),
                0.85f,
                null
        );

        DreamSignal.DreamScene medQ = new DreamSignal.DreamScene(
                "scene-med",
                "Pragmatic scene",
                "Insight text",
                new float[]{0.0f, 1.0f, 0.0f, 0.0f},
                List.of("s2"),
                0.55f,
                null
        );

        DreamSignal.DreamScene lowQ = new DreamSignal.DreamScene(
                "scene-low",
                "Noise scene",
                "Insight text",
                new float[]{0.0f, 0.0f, 1.0f, 0.0f},
                List.of("s3"),
                0.15f,
                null
        );

        DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .build();

        signal.addConstructedScene(highQ);
        signal.addConstructedScene(medQ);
        signal.addConstructedScene(lowQ);

        relay.transmit(signal);

        assertThat(signal.constructedScenes().get(0).triageOutcome()).isEqualTo(DreamSignal.TriageOutcome.EPISTEMIC);
        assertThat(signal.constructedScenes().get(1).triageOutcome()).isEqualTo(DreamSignal.TriageOutcome.PRAGMATIC);
        assertThat(signal.constructedScenes().get(2).triageOutcome()).isEqualTo(DreamSignal.TriageOutcome.NOISE);
    }
}
