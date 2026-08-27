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
package com.spectrayan.spector.memory.dream.relay;

import java.time.Duration;

/**
 * Immutable execution telemetry report returned upon completing a {@link com.spectrayan.spector.memory.DreamPathway} cycle.
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
        DreamMode mode
) {
    public static DreamReport empty() {
        return new DreamReport(0, 0, 0, 0, 0, 0, Duration.ZERO, null);
    }
}
