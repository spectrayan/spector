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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Arrays;

/**
 * Immutable representation of the personal Riemannian metric tensor M_person = diag(d) + U U^T.
 *
 * <h3>Biological Analog: Experiential Metric Warping</h3>
 * <p>Encodes the idiosyncratic topological curvature of an individual mind's cognitive space,
 * warping geometric distances according to personal semantic couplings and associative histories.</p>
 */
public record PersonalMetricTensor(
        float[] diagonalScaling,
        float[][] lowRankComponents,
        int version,
        long timestampMs
) {

    /**
     * Compact constructor enforcing defensive copies and validation.
     */
    public PersonalMetricTensor {
        if (diagonalScaling == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Diagonal scaling must not be null");
        }
        if (diagonalScaling.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimension must be positive");
        }
        if (version < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Version cannot be negative");
        }

        diagonalScaling = Arrays.copyOf(diagonalScaling, diagonalScaling.length);
        for (int i = 0; i < diagonalScaling.length; i++) {
            if (Float.isNaN(diagonalScaling[i]) || diagonalScaling[i] <= 0.0f) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Diagonal scaling must be positive and non-NaN at index " + i);
            }
        }

        if (lowRankComponents != null) {
            float[][] copied = new float[lowRankComponents.length][];
            for (int r = 0; r < lowRankComponents.length; r++) {
                if (lowRankComponents[r] == null || lowRankComponents[r].length != diagonalScaling.length) {
                    throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Low-rank component dimension mismatch at index " + r);
                }
                copied[r] = Arrays.copyOf(lowRankComponents[r], lowRankComponents[r].length);
            }
            lowRankComponents = copied;
        } else {
            lowRankComponents = new float[0][];
        }
    }

    /**
     * @return the dimensionality of the metric tensor
     */
    public int dimensions() {
        return diagonalScaling.length;
    }

    /**
     * @return the number of low-rank component factors
     */
    public int rank() {
        return lowRankComponents.length;
    }

    /**
     * Creates a default Euclidean identity metric tensor of the specified dimension.
     *
     * @param dimensions target dimension
     * @return identity metric tensor (d = [1..1], U = empty)
     */
    public static PersonalMetricTensor identity(int dimensions) {
        if (dimensions <= 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Dimension must be positive");
        }
        float[] diag = new float[dimensions];
        Arrays.fill(diag, 1.0f);
        return new PersonalMetricTensor(diag, new float[0][], 0, System.currentTimeMillis());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalMetricTensor that)) return false;
        return version == that.version &&
                timestampMs == that.timestampMs &&
                Arrays.equals(diagonalScaling, that.diagonalScaling) &&
                Arrays.deepEquals(lowRankComponents, that.lowRankComponents);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(diagonalScaling);
        result = 31 * result + Arrays.deepHashCode(lowRankComponents);
        result = 31 * result + version;
        result = 31 * result + Long.hashCode(timestampMs);
        return result;
    }

    @Override
    public String toString() {
        return "PersonalMetricTensor{" +
                "dim=" + diagonalScaling.length +
                ", rank=" + lowRankComponents.length +
                ", version=" + version +
                ", timestampMs=" + timestampMs +
                '}';
    }
}
