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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.core.math.EmaTracker;

/**
 * Immutable running statistics for multi-armed bandit / reinforcement tracking.
 *
 * @deprecated Use {@link EmaTracker} instead. Scheduled for removal in 0.3.0.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public record BanditStats(
        float ema,
        int totalSignals,
        int positiveSignals,
        long lastUpdatedMs
) {
    public static final BanditStats EMPTY = new BanditStats(0f, 0, 0, 0L);

    public BanditStats update(boolean positive, float alpha) {
        EmaTracker updated = toTracker().update(positive, alpha, System.currentTimeMillis());
        return fromTracker(updated);
    }

    public EmaTracker toTracker() {
        return new EmaTracker(ema, totalSignals, positiveSignals, lastUpdatedMs);
    }

    public static BanditStats fromTracker(EmaTracker tracker) {
        return new BanditStats(tracker.ema(), tracker.totalSignals(), tracker.positiveSignals(), tracker.lastUpdatedMs());
    }
}
