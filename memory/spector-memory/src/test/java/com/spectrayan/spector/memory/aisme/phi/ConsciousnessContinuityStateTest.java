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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsciousnessContinuityState}.
 */
class ConsciousnessContinuityStateTest {

    @Test
    void empty_createsDefaultState() {
        ConsciousnessContinuityState state = ConsciousnessContinuityState.empty();
        assertThat(state.rawPhi()).isZero();
        assertThat(state.soulAlignment()).isEqualTo(1.0f);
        assertThat(state.compositePhiCC()).isZero();
        assertThat(state.isCohesive()).isTrue();
        assertThat(state.candidateCount()).isZero();
    }

    @Test
    void validConstruction_accessors() {
        ConsciousnessContinuityState state = new ConsciousnessContinuityState(
                0.8f, 0.9f, 0.72f, true, 5, 1000L
        );
        assertThat(state.rawPhi()).isEqualTo(0.8f);
        assertThat(state.soulAlignment()).isEqualTo(0.9f);
        assertThat(state.compositePhiCC()).isEqualTo(0.72f);
        assertThat(state.isCohesive()).isTrue();
        assertThat(state.candidateCount()).isEqualTo(5);
        assertThat(state.timestampMs()).isEqualTo(1000L);
    }

    @Test
    void invalidArguments_throwValidationException() {
        assertThatThrownBy(() -> new ConsciousnessContinuityState(-0.1f, 0.5f, 0f, true, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new ConsciousnessContinuityState(0.1f, 1.5f, 0f, true, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new ConsciousnessContinuityState(0.1f, 0.5f, -0.1f, true, 1, 0L))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new ConsciousnessContinuityState(0.1f, 0.5f, 0.05f, true, -1, 0L))
                .isInstanceOf(SpectorValidationException.class);
    }
}
