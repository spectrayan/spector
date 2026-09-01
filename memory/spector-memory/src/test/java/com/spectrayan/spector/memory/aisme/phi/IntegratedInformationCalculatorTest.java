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
package com.spectrayan.spector.memory.aisme.phi;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link IntegratedInformationCalculator}.
 */
class IntegratedInformationCalculatorTest {

    private IntegratedInformationCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IntegratedInformationCalculator();
    }

    @Test
    void calculatePhi_emptyOrSingleVector_returnsZero() {
        assertThat(calculator.calculatePhi(List.of())).isZero();
        assertThat(calculator.calculatePhi(List.of(new float[]{1.0f, 0.0f}))).isZero();
    }

    @Test
    void calculatePhi_multiVectorCluster_returnsNonNegativePhi() {
        List<float[]> cluster = List.of(
                new float[]{1.0f, 0.1f},
                new float[]{0.9f, 0.2f},
                new float[]{0.8f, 0.3f}
        );

        float phi = calculator.calculatePhi(cluster);
        assertThat(phi).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    void invalidRegularization_throwsValidationException() {
        assertThatThrownBy(() -> new IntegratedInformationCalculator(-0.01f))
                .isInstanceOf(SpectorValidationException.class);
    }
}
