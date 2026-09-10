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
package com.spectrayan.spector.memory.cortex.adaptor;

import com.spectrayan.spector.core.math.EmaTracker;

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
 * @deprecated Use {@link EmaTracker} instead. Scheduled for removal in 0.3.0.
 *
 * @param ema             exponential moving average of the positive reinforcement rate (0.0–1.0)
 * @param totalSignals    total number of reinforcement signals received
 * @param positiveSignals count of positive reinforcement signals
 * @param lastUpdatedMs   epoch milliseconds of the last update
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
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
     * @param positive whether this signal is a positive reinforcement
     * @param alpha    the EMA smoothing factor (0.0–1.0); higher = more responsive
     * @return a new {@code RunningStats} reflecting the updated state
     */
    public RunningStats update(boolean positive, float alpha) {
        EmaTracker updated = toTracker().update(positive, alpha, System.currentTimeMillis());
        return fromTracker(updated);
    }

    public EmaTracker toTracker() {
        return new EmaTracker(ema, totalSignals, positiveSignals, lastUpdatedMs);
    }

    public static RunningStats fromTracker(EmaTracker tracker) {
        return new RunningStats(tracker.ema(), tracker.totalSignals(), tracker.positiveSignals(), tracker.lastUpdatedMs());
    }
}
