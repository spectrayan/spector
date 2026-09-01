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
package com.spectrayan.spector.memory.aisme.pcmn;

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
 * Unit tests for {@link TierPrediction}.
 */
class TierPredictionTest {

    @Test
    void validConstruction_defensiveCopyAndAccessors() {
        float[] pred = {1.0f, 2.0f};
        float[] prec = {2.0f, 2.0f};

        TierPrediction tp = new TierPrediction(2, pred, prec, 1000L);

        assertThat(tp.tierLevel()).isEqualTo(2);
        assertThat(tp.dimensions()).isEqualTo(2);
        assertThat(tp.timestampMs()).isEqualTo(1000L);
        assertThat(tp.predictionVector()).containsExactly(1.0f, 2.0f);
        assertThat(tp.precisionWeights()).containsExactly(2.0f, 2.0f);

        pred[0] = 999.0f;
        assertThat(tp.predictionVector()[0]).isEqualTo(1.0f);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[] v1 = {1.0f};
        float[] v2 = {1.0f, 2.0f};

        assertThatThrownBy(() -> new TierPrediction(1, null, v1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, v1, null, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, v1, v2, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new TierPrediction(1, new float[0], new float[0], 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
