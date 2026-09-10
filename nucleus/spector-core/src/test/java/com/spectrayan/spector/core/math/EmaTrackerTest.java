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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property-based tests for {@link EmaTracker}.
 */
class EmaTrackerTest {

    @Test
    void emptyTrackerHasDefaultZeros() {
        EmaTracker tracker = EmaTracker.EMPTY;
        assertThat(tracker.ema()).isEqualTo(0.0f);
        assertThat(tracker.totalSignals()).isEqualTo(0);
        assertThat(tracker.positiveSignals()).isEqualTo(0);
        assertThat(tracker.lastUpdatedMs()).isEqualTo(0L);
        assertThat(tracker.winRate()).isEqualTo(0.0f);
    }

    @Test
    void firstObservationSeedsEmaDirectly() {
        EmaTracker tPos = EmaTracker.EMPTY.update(true, 0.2f, 1000L);
        assertThat(tPos.ema()).isEqualTo(1.0f);
        assertThat(tPos.totalSignals()).isEqualTo(1);
        assertThat(tPos.positiveSignals()).isEqualTo(1);
        assertThat(tPos.lastUpdatedMs()).isEqualTo(1000L);
        assertThat(tPos.winRate()).isEqualTo(1.0f);

        EmaTracker tNeg = EmaTracker.EMPTY.update(false, 0.2f, 2000L);
        assertThat(tNeg.ema()).isEqualTo(0.0f);
        assertThat(tNeg.totalSignals()).isEqualTo(1);
        assertThat(tNeg.positiveSignals()).isEqualTo(0);
        assertThat(tNeg.lastUpdatedMs()).isEqualTo(2000L);
        assertThat(tNeg.winRate()).isEqualTo(0.0f);
    }

    @Test
    void sequentialUpdatesFollowExponentialDecay() {
        // Start with true (ema = 1.0f)
        EmaTracker t = EmaTracker.EMPTY.update(true, 0.1f, 100L);
        assertThat(t.ema()).isEqualTo(1.0f);

        // Incorporate false: 1.0 * 0.9 + 0.0 * 0.1 = 0.9
        t = t.update(false, 0.1f, 200L);
        assertThat(t.ema()).isCloseTo(0.9f, within(1e-6f));
        assertThat(t.totalSignals()).isEqualTo(2);
        assertThat(t.positiveSignals()).isEqualTo(1);
        assertThat(t.winRate()).isCloseTo(0.5f, within(1e-6f));

        // Incorporate true: 0.9 * 0.9 + 1.0 * 0.1 = 0.81 + 0.1 = 0.91
        t = t.update(true, 0.1f, 300L);
        assertThat(t.ema()).isCloseTo(0.91f, within(1e-6f));
        assertThat(t.totalSignals()).isEqualTo(3);
        assertThat(t.positiveSignals()).isEqualTo(2);
        assertThat(t.winRate()).isCloseTo(2.0f / 3.0f, within(1e-6f));
        assertThat(t.lastUpdatedMs()).isEqualTo(300L);
    }

    @Property(tries = 100)
    void emaAndWinRateRemainNormalized(
            @ForAll @Size(min = 1, max = 100) List<Boolean> signals,
            @ForAll @FloatRange(min = 0.01f, max = 1.0f) float alpha
    ) {
        EmaTracker tracker = EmaTracker.EMPTY;
        long time = 1000L;
        for (boolean sig : signals) {
            tracker = tracker.update(sig, alpha, time++);
        }

        assertThat(tracker.totalSignals()).isEqualTo(signals.size());
        assertThat(tracker.winRate()).isBetween(0.0f, 1.0f);
        assertThat(tracker.ema()).isBetween(0.0f, 1.0f);
    }
}
