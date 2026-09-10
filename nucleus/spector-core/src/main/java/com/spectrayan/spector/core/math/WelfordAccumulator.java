/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.core.math;

/**
 * Numerically stable online one-pass variance and mean accumulator (Welford, 1962).
 *
 * <p>Contract: ADR-0033 Purity Tier T2 (Immutable Accumulator Record). All instances are
 * immutable and safe for concurrent reads. State updates return a fresh record instance.</p>
 *
 * <h3>Mathematical Formulation</h3>
 * <pre>
 *   M_1 = x_1,   S_1 = 0
 *   M_k = M_{k-1} + (x_k - M_{k-1}) / k
 *   S_k = S_{k-1} + (x_k - M_{k-1}) * (x_k - M_k)
 *   variance = S_k / k
 * </pre>
 *
 * @param count total number of samples incorporated
 * @param mean  running sample mean
 * @param m2    sum of squared differences from the mean (S_k)
 */
public record WelfordAccumulator(long count, double mean, double m2) {

    /** Empty accumulator with zero observations. */
    public static final WelfordAccumulator EMPTY = new WelfordAccumulator(0L, 0.0, 0.0);

    /**
     * Returns a new accumulator incorporating the given sample value.
     *
     * @param sample the observation value
     * @return new immutable accumulator reflecting the observation
     */
    public WelfordAccumulator update(double sample) {
        long n = count + 1;
        double delta = sample - mean;
        double newMean = mean + delta / n;
        double delta2 = sample - newMean;
        double newM2 = m2 + delta * delta2;
        return new WelfordAccumulator(n, newMean, newM2);
    }

    /**
     * Computes the population variance of observed samples.
     *
     * @return variance, or 0.0 if fewer than 2 samples observed
     */
    public double variance() {
        return count < 2 ? 0.0 : m2 / count;
    }

    /**
     * Computes the population standard deviation of observed samples.
     *
     * @return standard deviation, or 0.0 if fewer than 2 samples observed
     */
    public double stdDev() {
        return Math.sqrt(variance());
    }

    /**
     * Computes the standard score (z-score) of a value against the accumulated distribution.
     *
     * @param sample the observation to score
     * @return z-score {@code (sample - mean) / stdDev}, or 0.0 if stdDev &lt; 1e-9
     */
    public double zScore(double sample) {
        double sd = stdDev();
        if (sd < 1e-9) {
            return 0.0;
        }
        return (sample - mean) / sd;
    }

    /**
     * Checks if the accumulator has observed at least {@code minSamples}.
     *
     * @param minSamples minimum required observations
     * @return true if sample count meets or exceeds {@code minSamples}
     */
    public boolean isWarm(long minSamples) {
        return count >= minSamples;
    }
}
