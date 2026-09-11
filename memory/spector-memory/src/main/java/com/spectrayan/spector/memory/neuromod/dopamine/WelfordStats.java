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
package com.spectrayan.spector.memory.neuromod.dopamine;

import com.spectrayan.spector.core.math.WelfordAccumulator;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Welford's online algorithm for computing running mean and standard deviation.
 *
 * <p>O(1) space, O(1) per update, numerically stable. Thread-safe via lock-free atomic updates.</p>
 *
 * <h3>Biological Analog: Baseline Prediction</h3>
 * <p>The brain's dopamine system maintains an internal baseline of "expected" stimuli.
 * Welford's algorithm computes that baseline (mean) and the expected variance (stddev),
 * enabling the {@link SurpriseDetector} to calculate z-scores against the running distribution.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm">
 *     Welford's Algorithm (Wikipedia)</a>
 */
public final class WelfordStats {

    private final AtomicReference<WelfordAccumulator> accum = new AtomicReference<>(WelfordAccumulator.EMPTY);

    /**
     * Incorporates a new sample into the running statistics.
     *
     * @param value the new observation
     */
    public void update(double value) {
        accum.updateAndGet(a -> a.update(value));
    }

    /**
     * Returns the current running mean.
     *
     * @return mean of all observed values, or 0.0 if no values observed
     */
    public double mean() {
        return accum.get().mean();
    }

    /**
     * Returns the current population standard deviation.
     *
     * @return stddev, or 0.0 if fewer than 2 values observed
     */
    public double stddev() {
        return accum.get().stdDev();
    }

    /**
     * Computes the z-score of a value against the running distribution.
     *
     * @param value the value to score
     * @return z-score (0.0 if stddev is zero or fewer than 2 samples)
     */
    public double zScore(double value) {
        return accum.get().zScore(value);
    }

    /**
     * Returns the number of samples observed.
     */
    public long count() {
        return accum.get().count();
    }

    /**
     * Checks if the accumulator has observed at least {@code minSamples}.
     *
     * @param minSamples minimum required observations
     * @return true if sample count meets or exceeds {@code minSamples}
     */
    public boolean isWarm(long minSamples) {
        return accum.get().isWarm(minSamples);
    }

    /**
     * Returns an immutable snapshot of the underlying pure accumulator.
     */
    public WelfordAccumulator accumulator() {
        return accum.get();
    }

    /**
     * Resets all statistics.
     */
    public void reset() {
        accum.set(WelfordAccumulator.EMPTY);
    }
}
