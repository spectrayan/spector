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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Arrays;

/**
 * Immutable record capturing the hierarchical multi-tier prediction error state.
 *
 * <h3>Biological Analog: Ascending Precision-Weighted Prediction Error Signals</h3>
 * <p>Represents the ascending prediction error signals that drive synaptic plasticity,
 * active inference belief updates, and memory retrieval prioritisation across cortical tiers.</p>
 */
public record HierarchicalPredictionError(
        float[][] weightedErrorVectors,
        float[] tierEnergies,
        float totalEnergy,
        long timestampMs
) {

    /**
     * Compact constructor with validation and defensive copies.
     */
    public HierarchicalPredictionError {
        if (weightedErrorVectors == null || tierEnergies == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Error vectors and energies must not be null");
        }
        if (weightedErrorVectors.length != tierEnergies.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Tier count mismatch between errors and energies");
        }

        float[][] copiedErrors = new float[weightedErrorVectors.length][];
        for (int i = 0; i < weightedErrorVectors.length; i++) {
            copiedErrors[i] = (weightedErrorVectors[i] != null)
                    ? Arrays.copyOf(weightedErrorVectors[i], weightedErrorVectors[i].length)
                    : new float[0];
        }
        weightedErrorVectors = copiedErrors;
        tierEnergies = Arrays.copyOf(tierEnergies, tierEnergies.length);
    }

    public int tierCount() {
        return tierEnergies.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HierarchicalPredictionError that)) return false;
        return Float.compare(that.totalEnergy, totalEnergy) == 0 &&
                timestampMs == that.timestampMs &&
                Arrays.deepEquals(weightedErrorVectors, that.weightedErrorVectors) &&
                Arrays.equals(tierEnergies, that.tierEnergies);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(totalEnergy);
        result = 31 * result + Arrays.deepHashCode(weightedErrorVectors);
        result = 31 * result + Arrays.hashCode(tierEnergies);
        result = 31 * result + Long.hashCode(timestampMs);
        return result;
    }

    @Override
    public String toString() {
        return "HierarchicalPredictionError{" +
                "tiers=" + tierEnergies.length +
                ", totalEnergy=" + totalEnergy +
                ", timestampMs=" + timestampMs +
                '}';
    }
}
