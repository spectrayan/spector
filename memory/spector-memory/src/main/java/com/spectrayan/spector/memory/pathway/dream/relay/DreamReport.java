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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;

import java.time.Duration;

/**
 * Immutable execution telemetry report returned upon completing a {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway} cycle.
 *
 * <h3>Biological Analog: Post-Sleep Metrics</h3>
 * <p>Telemetry reflecting memory consolidation efficiency during rest cycles.</p>
 *
 * @since 1.4.0
 */
public record DreamReport(
        int seedsSampled,
        int scenesConstructed,
        int scenesTriaged,
        int insightsIngested,
        int journalEntriesWritten,
        int failedPairsInhibited,
        Duration elapsed,
        DreamMode mode,
        ConductionOutcome outcome
) {
    public DreamReport(
            int seedsSampled,
            int scenesConstructed,
            int scenesTriaged,
            int insightsIngested,
            int journalEntriesWritten,
            int failedPairsInhibited,
            Duration elapsed,
            DreamMode mode
    ) {
        this(seedsSampled, scenesConstructed, scenesTriaged, insightsIngested, journalEntriesWritten, failedPairsInhibited, elapsed, mode, null);
    }

    public static DreamReport empty() {
        return new DreamReport(0, 0, 0, 0, 0, 0, Duration.ZERO, null, null);
    }

    public static DreamReport empty(ConductionOutcome outcome) {
        return new DreamReport(0, 0, 0, 0, 0, 0, Duration.ZERO, null, outcome);
    }
}
