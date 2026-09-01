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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Arrays;

/**
 * Immutable record capturing the converged attractor state of a Continuous Hopfield Network.
 *
 * <h3>Biological Analog: Settled Attractor in Hippocampal-Cortical Circuit</h3>
 * <p>Represents the synthesized memory gestalt after relaxation through the energy landscape,
 * including its energetic depth and the attention weights over constituent memory traces.</p>
 */
public record AttractorState(
        float[] attractorVector,
        float[] attentionWeights,
        AttractorType type,
        float energy,
        int iterations,
        long timestampMs
) {

    /**
     * Compact constructor with defensive copies and validation.
     */
    public AttractorState {
        if (attractorVector == null || attentionWeights == null || type == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Arguments must not be null");
        }
        if (attractorVector.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Attractor vector dimension must be greater than zero");
        }
        if (iterations < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Iteration count cannot be negative");
        }

        attractorVector = Arrays.copyOf(attractorVector, attractorVector.length);
        attentionWeights = Arrays.copyOf(attentionWeights, attentionWeights.length);
    }

    /**
     * @return the dimensionality of the attractor state
     */
    public int dimensions() {
        return attractorVector.length;
    }

    /**
     * @return the number of pattern memories participating in the attractor
     */
    public int patternCount() {
        return attentionWeights.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttractorState that)) return false;
        return Float.compare(that.energy, energy) == 0 &&
                iterations == that.iterations &&
                timestampMs == that.timestampMs &&
                type == that.type &&
                Arrays.equals(attractorVector, that.attractorVector) &&
                Arrays.equals(attentionWeights, that.attentionWeights);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(energy);
        result = 31 * result + iterations;
        result = 31 * result + Long.hashCode(timestampMs);
        result = 31 * result + type.hashCode();
        result = 31 * result + Arrays.hashCode(attractorVector);
        result = 31 * result + Arrays.hashCode(attentionWeights);
        return result;
    }

    @Override
    public String toString() {
        return "AttractorState{" +
                "dim=" + attractorVector.length +
                ", patterns=" + attentionWeights.length +
                ", type=" + type +
                ", energy=" + energy +
                ", iterations=" + iterations +
                '}';
    }
}
