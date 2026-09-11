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
 * Online Exponential Moving Average (EMA) reinforcement tracker.
 *
 * <p>Contract: ADR-0033 Purity Tier T2 (Immutable Accumulator Record, Clock-Injected).
 * Unifies the previously duplicated {@code BanditStats} and {@code RunningStats} implementations.</p>
 *
 * <h3>Mathematical Formulation</h3>
 * <pre>
 *   EMA_0 = signal
 *   EMA_n = EMA_{n-1} * (1 - alpha) + signal * alpha
 *   winRate = positiveSignals / totalSignals
 * </pre>
 *
 * @param ema             exponential moving average of positive reinforcement rate (0.0–1.0)
 * @param totalSignals    total count of reinforcement signals received
 * @param positiveSignals count of positive reinforcement signals received
 * @param lastUpdatedMs   epoch timestamp (ms) when the last update occurred (injected by caller)
 */
public record EmaTracker(
        float ema,
        int totalSignals,
        int positiveSignals,
        long lastUpdatedMs
) {

    /** Empty statistics — no signals received, zero EMA. */
    public static final EmaTracker EMPTY = new EmaTracker(0f, 0, 0, 0L);

    /**
     * Pure static utility to compute the next EMA value given current state and a signal.
     *
     * @param currentEma current smoothed EMA value
     * @param totalCount total number of prior signals (0 triggers initial seed assignment)
     * @param signal     true for positive (1.0f), false for negative (0.0f)
     * @param alpha      smoothing factor in range (0.0, 1.0]
     * @return updated EMA value
     */
    public static float updateEma(float currentEma, int totalCount, boolean signal, float alpha) {
        float val = signal ? 1.0f : 0.0f;
        return totalCount == 0 ? val : (currentEma * (1.0f - alpha) + val * alpha);
    }

    /**
     * Returns a new immutable {@code EmaTracker} reflecting the incorporated signal.
     *
     * @param positive whether this reinforcement event was positive
     * @param alpha    EMA smoothing factor (0.0–1.0); higher values adapt faster to recent signals
     * @param nowMs    epoch timestamp of the observation (injected for deterministic testing)
     * @return updated accumulator instance
     */
    public EmaTracker update(boolean positive, float alpha, long nowMs) {
        float newEma = updateEma(ema, totalSignals, positive, alpha);
        return new EmaTracker(
                newEma,
                totalSignals + 1,
                positiveSignals + (positive ? 1 : 0),
                nowMs
        );
    }

    /**
     * Computes the empirical positive signal ratio (win rate).
     *
     * @return positiveSignals / totalSignals, or 0.0f if no signals recorded
     */
    public float winRate() {
        return totalSignals == 0 ? 0.0f : (float) positiveSignals / totalSignals;
    }
}
