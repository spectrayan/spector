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
package com.spectrayan.spector.memory.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EdgeImportance} — the neuroscience-informed edge scoring function.
 */
class EdgeImportanceTest {

    private final EdgeImportance scorer = EdgeImportance.DEFAULT;

    // ── Convenience: default "neutral" parameters ──
    private static final int CYCLE = 100;
    private static final byte NEUTRAL_AROUSAL = 0;
    private static final byte NEUTRAL_VALENCE = 0;
    private static final float DEFAULT_STORAGE = 1.0f;
    private static final byte DEFAULT_FLAGS = 0x20; // resolved=true (bit 5 set)

    private float scoreWith(float weight, int lastCycle, int bridgeScore, int shared,
                            float impA, float impB, byte aroA, byte aroB,
                            byte valA, byte valB, float ssA, float ssB,
                            byte flagsA, byte flagsB) {
        return scorer.score(weight, CYCLE, lastCycle, bridgeScore, shared,
                impA, impB, aroA, aroB, valA, valB, ssA, ssB, flagsA, flagsB);
    }

    @Nested
    @DisplayName("Signal 1: Weight (Hebbian LTP)")
    class WeightSignal {

        @Test
        @DisplayName("higher co-recall weight → higher importance")
        void higherWeightScoresHigher() {
            float lowWeight = scoreWith(1.0f, CYCLE, 0, 0, 0, 0,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float highWeight = scoreWith(10.0f, CYCLE, 0, 0, 0, 0,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);

            assertThat(highWeight).isGreaterThan(lowWeight);
        }
    }

    @Nested
    @DisplayName("Signal 2: Recency (STC)")
    class RecencySignal {

        @Test
        @DisplayName("recently strengthened edge → higher importance")
        void recentEdgeScoresHigher() {
            float recent = scoreWith(5.0f, CYCLE, 0, 0, 0, 0,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float old = scoreWith(5.0f, CYCLE - 200, 0, 0, 0, 0,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);

            assertThat(recent).isGreaterThan(old);
        }
    }

    @Nested
    @DisplayName("Signal 3: Bridge Score")
    class BridgeSignal {

        @Test
        @DisplayName("high bridge score → higher importance")
        void bridgeEdgeScoresHigher() {
            float noBridge = scoreWith(3.0f, CYCLE, 0, 0, 5.0f, 5.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float criticalBridge = scoreWith(3.0f, CYCLE, 255, 0, 5.0f, 5.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);

            assertThat(criticalBridge).isGreaterThan(noBridge);
        }

        @Test
        @DisplayName("bridge edge with low weight beats high-weight redundant edge")
        void bridgeSurvivesOverWeightAlone() {
            // The CEO's exact concern: low-weight bridge must survive over high-weight redundant
            float highWeightRedundant = scoreWith(10.0f, CYCLE, 0, 10, 3.0f, 3.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float lowWeightBridge = scoreWith(1.0f, CYCLE, 200, 0, 3.0f, 3.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);

            assertThat(lowWeightBridge).isGreaterThan(highWeightRedundant);
        }
    }

    @Nested
    @DisplayName("Signal 5: Memory Importance (ACT-R)")
    class ImportanceSignal {

        @Test
        @DisplayName("edge between high-importance memories → higher importance")
        void highImportanceMemoriesProtectEdge() {
            float lowImp = scoreWith(3.0f, CYCLE, 0, 0, 1.0f, 1.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float highImp = scoreWith(3.0f, CYCLE, 0, 0, 9.0f, 9.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);

            assertThat(highImp).isGreaterThan(lowImp);
        }
    }

    @Nested
    @DisplayName("Signal 6: Arousal (Amygdala)")
    class ArousalSignal {

        @Test
        @DisplayName("emotionally intense edge resists eviction")
        void highArousalProtectsEdge() {
            float neutral = scoreWith(3.0f, CYCLE, 0, 0, 5.0f, 5.0f,
                    (byte) 0, (byte) 0, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            float intense = scoreWith(3.0f, CYCLE, 0, 0, 5.0f, 5.0f,
                    (byte) -56, (byte) -56, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, DEFAULT_FLAGS, DEFAULT_FLAGS);
            // (byte) -56 = unsigned 200

            assertThat(intense).isGreaterThan(neutral);
        }
    }

    @Nested
    @DisplayName("Signal 9: Zeigarnik Protection")
    class ZeigarnkSignal {

        @Test
        @DisplayName("unresolved memory protects its edges")
        void unresolvedMemoryProtectsEdge() {
            byte resolved = 0x20;   // bit 5 set = resolved
            byte unresolved = 0x00; // bit 5 clear = unresolved

            float resolvedEdge = scoreWith(3.0f, CYCLE, 0, 0, 5.0f, 5.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, resolved, resolved);
            float unresolvedEdge = scoreWith(3.0f, CYCLE, 0, 0, 5.0f, 5.0f,
                    NEUTRAL_AROUSAL, NEUTRAL_AROUSAL, NEUTRAL_VALENCE, NEUTRAL_VALENCE,
                    DEFAULT_STORAGE, DEFAULT_STORAGE, unresolved, resolved);

            assertThat(unresolvedEdge).isGreaterThan(resolvedEdge);
        }
    }

    @Nested
    @DisplayName("Structural-only fallback")
    class StructuralFallback {

        @Test
        @DisplayName("scoreStructural produces valid scores without memory signals")
        void structuralScoreWorks() {
            float score = scorer.scoreStructural(5.0f, CYCLE, CYCLE, 128, 2);
            assertThat(score).isBetween(0.0f, 1.0f);
        }

        @Test
        @DisplayName("structural bridge beats structural weight")
        void structuralBridgeBeatsWeight() {
            float highWeightNoBridge = scorer.scoreStructural(10.0f, CYCLE, CYCLE, 0, 5);
            float lowWeightBridge = scorer.scoreStructural(1.0f, CYCLE, CYCLE, 200, 0);
            assertThat(lowWeightBridge).isGreaterThan(highWeightNoBridge);
        }
    }
}
