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
package com.spectrayan.spector.memory.adaptor;


/**
 * Immutable running statistics for reinforcement-based profile adaptation.
 *
 * <h3>Biological Analog: Basal Ganglia Procedural Learning</h3>
 * <p>The basal ganglia learn through dopamine-mediated reinforcement — each
 * positive outcome (reward) strengthens the action that produced it, while
 * negative outcomes (punishment) weaken it. This record models that process
 * as an exponential moving average (EMA) of the positive reinforcement rate.</p>
 *
 * <p>The EMA tracks the recent "success rate" of a cognitive profile. When
 * a profile consistently produces good recall outcomes (user accepts the
 * result, clicks through, etc.), its EMA rises toward 1.0. When outcomes
 * are negative (user ignores, re-queries, etc.), the EMA decays toward 0.0.
 * The {@code alpha} parameter controls how quickly the EMA adapts — higher
 * values make it more responsive to recent signals, lower values make it
 * more stable.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Start with no history
 *   RunningStats stats = RunningStats.EMPTY;
 *
 *   // Record a positive reinforcement signal
 *   stats = stats.update(true, 0.1f);  // EMA → 1.0 (first signal)
 *
 *   // Record a negative signal
 *   stats = stats.update(false, 0.1f); // EMA → 0.9 (decays from 1.0)
 *
 *   // Check the running success rate
 *   float successRate = stats.ema(); // 0.9
 * }</pre>
 *
 * @param ema             exponential moving average of the positive reinforcement rate (0.0–1.0)
 * @param totalSignals    total number of reinforcement signals received
 * @param positiveSignals count of positive reinforcement signals
 * @param lastUpdatedMs   epoch milliseconds of the last update
 */
public record RunningStats(
        float ema,
        int totalSignals,
        int positiveSignals,
        long lastUpdatedMs
) {

    /** Empty statistics — no signals received, zero EMA. */
    public static final RunningStats EMPTY = new RunningStats(0f, 0, 0, 0L);

    /**
     * Returns new statistics incorporating a reinforcement signal.
     *
     * <p>The EMA is updated using the standard formula:
     * {@code newEma = ema * (1 - alpha) + value * alpha}, where {@code value}
     * is 1.0 for positive and 0.0 for negative signals. On the first signal,
     * the EMA is set directly to the value (no prior history to blend with).</p>
     *
     * <p>This method is pure — it returns a new {@code RunningStats} instance
     * without modifying the current one.</p>
     *
     * @param positive whether this signal is a positive reinforcement
     * @param alpha    the EMA smoothing factor (0.0–1.0); higher = more responsive
     * @return a new {@code RunningStats} reflecting the updated state
     */
    public RunningStats update(boolean positive, float alpha) {
        float value = positive ? 1.0f : 0.0f;
        float newEma = totalSignals == 0 ? value : ema * (1 - alpha) + value * alpha;
        return new RunningStats(
                newEma,
                totalSignals + 1,
                positiveSignals + (positive ? 1 : 0),
                System.currentTimeMillis());
    }
}
