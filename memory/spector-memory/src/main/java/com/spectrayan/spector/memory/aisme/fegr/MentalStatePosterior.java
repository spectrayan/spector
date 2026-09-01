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
package com.spectrayan.spector.memory.aisme.fegr;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.FreeEnergyKernel;

import java.util.Arrays;

/**
 * Immutable representation of the agent's approximate posterior belief distribution q(s_t)
 * over continuous latent mental states.
 *
 * <h3>Biological Analog: Cortical Latent Belief Density</h3>
 * <p>Represents a multivariate diagonal Gaussian distribution parameterized by mean expectations
 * and precision (inverse variance) weightings across cognitive dimensions.</p>
 */
public record MentalStatePosterior(
        float[] mean,
        float[] precision,
        long timestampMs,
        int version
) {

    /**
     * Compact constructor enforcing defensive copies, dimensionality alignment, and precision positivity.
     */
    public MentalStatePosterior {
        if (mean == null || precision == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Mean and precision vectors must not be null");
        }
        if (mean.length != precision.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Mean and precision vectors must have equal dimensions");
        }
        if (mean.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Vector dimension must be greater than zero");
        }

        mean = Arrays.copyOf(mean, mean.length);
        precision = Arrays.copyOf(precision, precision.length);

        for (int i = 0; i < mean.length; i++) {
            if (Float.isNaN(mean[i]) || Float.isInfinite(mean[i])) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Mean vector contains NaN or Infinite value at index " + i);
            }
            if (Float.isNaN(precision[i]) || Float.isInfinite(precision[i]) || precision[i] <= 0.0f) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Precision vector must contain strictly positive non-NaN values at index " + i);
            }
        }
    }

    /**
     * @return the dimensionality of the latent mental state distribution
     */
    public int dimensions() {
        return mean.length;
    }

    /**
     * Derives a new posterior conditioned on new sensory or memory evidence via precision-weighted Bayesian fusion.
     *
     * @param evidenceMean mean vector of incoming evidence
     * @param evidencePrecision precision vector of incoming evidence
     * @param timestamp epoch timestamp in milliseconds
     * @param nextVersion incremented state version
     * @return updated posterior distribution
     */
    public MentalStatePosterior withEvidence(float[] evidenceMean, float[] evidencePrecision, long timestamp, int nextVersion) {
        int dim = dimensions();
        float[] fusedMean = new float[dim];
        float[] fusedPrecision = new float[dim];

        FreeEnergyKernel.precisionWeightedFusion(mean, precision, evidenceMean, evidencePrecision, fusedMean, fusedPrecision);
        return new MentalStatePosterior(fusedMean, fusedPrecision, timestamp, nextVersion);
    }

    /**
     * Simulates temporal decay of beliefs back toward a baseline prior.
     *
     * @param priorMean prior mean vector
     * @param priorPrecision prior precision vector
     * @param decayFactor decay rate in [0, 1] (0 = no decay, 1 = immediate snap to prior)
     * @param timestamp timestamp in milliseconds
     * @param nextVersion incremented state version
     * @return decayed posterior distribution
     */
    public MentalStatePosterior decayTowards(float[] priorMean, float[] priorPrecision, float decayFactor, long timestamp, int nextVersion) {
        float factor = Math.max(0.0f, Math.min(1.0f, decayFactor));
        int dim = dimensions();
        float[] decayedMean = new float[dim];
        float[] decayedPrecision = new float[dim];

        for (int i = 0; i < dim; i++) {
            decayedMean[i] = (1.0f - factor) * mean[i] + factor * priorMean[i];
            decayedPrecision[i] = (1.0f - factor) * precision[i] + factor * priorPrecision[i];
            if (decayedPrecision[i] <= 0.0f) {
                decayedPrecision[i] = 1.0f;
            }
        }

        return new MentalStatePosterior(decayedMean, decayedPrecision, timestamp, nextVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MentalStatePosterior that)) return false;
        return timestampMs == that.timestampMs &&
                version == that.version &&
                Arrays.equals(mean, that.mean) &&
                Arrays.equals(precision, that.precision);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(timestampMs);
        result = 31 * result + Integer.hashCode(version);
        result = 31 * result + Arrays.hashCode(mean);
        result = 31 * result + Arrays.hashCode(precision);
        return result;
    }

    @Override
    public String toString() {
        return "MentalStatePosterior{" +
                "dim=" + mean.length +
                ", version=" + version +
                ", timestampMs=" + timestampMs +
                '}';
    }
}
