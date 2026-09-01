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
package com.spectrayan.spector.memory.model;

import java.time.Instant;

/**
 * Temporal gating and WAL replay parameters for recall queries.
 *
 * @param minTimestamp     minimum memory timestamp inclusive (null = no filter)
 * @param maxTimestamp     maximum memory timestamp inclusive (null = no filter)
 * @param replayTimestamp  WAL replay target timestamp (null = disabled)
 * @param maxReplayEvents  maximum WAL events to replay (default: 100,000)
 */
public record TemporalOptions(
        Long minTimestamp,
        Long maxTimestamp,
        Instant replayTimestamp,
        int maxReplayEvents
) {
    /** Default: no temporal filtering, no replay. */
    public static final TemporalOptions DEFAULT = new TemporalOptions(
            null, null, null, 100_000);

    /** Returns true if any temporal filter is active. */
    public boolean hasTimeRange() {
        return minTimestamp != null || maxTimestamp != null;
    }

    /** Returns true if WAL replay mode is configured. */
    public boolean hasReplay() {
        return replayTimestamp != null;
    }
}
