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
package com.spectrayan.spector.memory.aisme.homeostasis;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link InteroceptiveState} record.
 */
class InteroceptiveStateTest {

    @Test
    void neutral_hasZeroValues() {
        InteroceptiveState neutral = InteroceptiveState.NEUTRAL;
        assertThat(neutral.valence()).isZero();
        assertThat(neutral.arousal()).isZero();
        assertThat(neutral.dominance()).isZero();
        assertThat(neutral.channels()).isEmpty();
        assertThat(neutral.dimensions()).isEqualTo(3);
    }

    @Test
    void toVector_containsVadPlusChannels() {
        float[] channels = {0.1f, 0.2f, 0.3f};
        InteroceptiveState state = new InteroceptiveState(0.5f, -0.3f, 0.8f, channels, 1000L, 1);
        float[] vec = state.toVector();
        assertThat(vec).hasSize(6);
        assertThat(vec[0]).isEqualTo(0.5f);
        assertThat(vec[1]).isEqualTo(-0.3f);
        assertThat(vec[2]).isEqualTo(0.8f);
        assertThat(vec[3]).isEqualTo(0.1f);
        assertThat(vec[4]).isEqualTo(0.2f);
        assertThat(vec[5]).isEqualTo(0.3f);
    }

    @Test
    void fromVector_roundTrips() {
        float[] channels = {-0.5f, 0.7f};
        InteroceptiveState original = new InteroceptiveState(0.2f, -0.8f, 0.4f, channels, 2000L, 5);
        float[] vec = original.toVector();
        InteroceptiveState restored = InteroceptiveState.fromVector(vec, 2000L, 5);
        assertThat(restored.valence()).isEqualTo(original.valence());
        assertThat(restored.arousal()).isEqualTo(original.arousal());
        assertThat(restored.dominance()).isEqualTo(original.dominance());
        assertThat(restored.channels()).containsExactly(original.channels());
    }

    @Test
    void fromVector_tooShort_throwsException() {
        assertThatThrownBy(() -> InteroceptiveState.fromVector(new float[]{1.0f, 2.0f}, 0L, 0))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    void clamp_constrainsValuesToRange() {
        float[] channels = {-1.5f, 2.0f};
        InteroceptiveState unclamped = new InteroceptiveState(1.5f, -1.5f, 0.5f, channels, 0L, 0);
        InteroceptiveState clamped = unclamped.clamp();
        assertThat(clamped.valence()).isEqualTo(1.0f);
        assertThat(clamped.arousal()).isEqualTo(-1.0f);
        assertThat(clamped.dominance()).isEqualTo(0.5f);
        assertThat(clamped.channels()[0]).isEqualTo(-1.0f);
        assertThat(clamped.channels()[1]).isEqualTo(1.0f);
    }

    @Test
    void dimensions_includesVadAndChannels() {
        float[] channels = {0.0f, 0.0f, 0.0f, 0.0f};
        InteroceptiveState state = new InteroceptiveState(0f, 0f, 0f, channels, 0L, 0);
        assertThat(state.dimensions()).isEqualTo(7); // 3 VAD + 4 channels
    }

    @Test
    void defensiveCopy_mutatingOriginalDoesNotAffectState() {
        float[] channels = {0.5f};
        InteroceptiveState state = new InteroceptiveState(0f, 0f, 0f, channels, 0L, 0);
        channels[0] = 999.0f;
        assertThat(state.channels()[0]).isEqualTo(0.5f);
    }

    @Test
    void nanValues_throwException() {
        assertThatThrownBy(() ->
                new InteroceptiveState(Float.NaN, 0f, 0f, new float[0], 0L, 0))
                .isInstanceOf(SpectorValidationException.class);
    }
}
