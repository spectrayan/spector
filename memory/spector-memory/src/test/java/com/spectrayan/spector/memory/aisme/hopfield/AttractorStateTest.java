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
package com.spectrayan.spector.memory.aisme.hopfield;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AttractorState}.
 */
class AttractorStateTest {

    @Test
    void construction_defensiveCopyAndAccessors() {
        float[] vector = {1.0f, 2.0f, 3.0f};
        float[] weights = {0.8f, 0.2f};

        AttractorState state = new AttractorState(
                vector, weights, AttractorType.FIXED_POINT, -5.5f, 3, 1000L);

        assertThat(state.dimensions()).isEqualTo(3);
        assertThat(state.patternCount()).isEqualTo(2);
        assertThat(state.type()).isEqualTo(AttractorType.FIXED_POINT);
        assertThat(state.energy()).isEqualTo(-5.5f);
        assertThat(state.iterations()).isEqualTo(3);
        assertThat(state.timestampMs()).isEqualTo(1000L);

        vector[0] = 999.0f;
        weights[0] = 999.0f;
        assertThat(state.attractorVector()[0]).isEqualTo(1.0f);
        assertThat(state.attentionWeights()[0]).isEqualTo(0.8f);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[] vector = {1.0f};
        float[] weights = {1.0f};

        assertThatThrownBy(() -> new AttractorState(null, weights, AttractorType.FIXED_POINT, 0f, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new AttractorState(vector, null, AttractorType.FIXED_POINT, 0f, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new AttractorState(vector, weights, null, 0f, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new AttractorState(new float[0], weights, AttractorType.FIXED_POINT, 0f, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new AttractorState(vector, weights, AttractorType.FIXED_POINT, 0f, -1, 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
