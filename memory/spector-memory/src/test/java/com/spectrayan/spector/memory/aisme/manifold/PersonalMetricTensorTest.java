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
package com.spectrayan.spector.memory.aisme.manifold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PersonalMetricTensor}.
 */
class PersonalMetricTensorTest {

    @Test
    void identity_createsDefaultUnitTensor() {
        PersonalMetricTensor identity = PersonalMetricTensor.identity(4);
        assertThat(identity.dimensions()).isEqualTo(4);
        assertThat(identity.rank()).isZero();
        assertThat(identity.version()).isZero();
        assertThat(identity.diagonalScaling()).containsExactly(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Test
    void validConstruction_defensiveCopy() {
        float[] diag = {2.0f, 3.0f};
        float[][] rank = {
                {0.5f, 0.5f}
        };

        PersonalMetricTensor tensor = new PersonalMetricTensor(diag, rank, 1, 1000L);

        assertThat(tensor.dimensions()).isEqualTo(2);
        assertThat(tensor.rank()).isEqualTo(1);
        assertThat(tensor.version()).isEqualTo(1);
        assertThat(tensor.timestampMs()).isEqualTo(1000L);

        diag[0] = 999.0f;
        rank[0][0] = 999.0f;
        assertThat(tensor.diagonalScaling()[0]).isEqualTo(2.0f);
        assertThat(tensor.lowRankComponents()[0][0]).isEqualTo(0.5f);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[] negDiag = {-1.0f};
        float[] nanDiag = {Float.NaN};

        assertThatThrownBy(() -> new PersonalMetricTensor(null, new float[0][], 0, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new PersonalMetricTensor(new float[0], new float[0][], 0, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new PersonalMetricTensor(negDiag, new float[0][], 0, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new PersonalMetricTensor(nanDiag, new float[0][], 0, 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
