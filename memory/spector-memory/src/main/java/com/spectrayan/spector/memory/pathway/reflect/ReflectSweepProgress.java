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
package com.spectrayan.spector.memory.pathway.reflect;

import java.time.Instant;
import java.util.Objects;

/**
 * Observable telemetry progress snapshot for a reflection sweep.
 *
 * @param sweepId           unique sweep identifier
 * @param sessionsCompleted total sessions consolidated so far
 * @param factsIngested     total semantic facts synthesized
 * @param turnsMarked       total turns marked consolidated
 * @param backlogRemaining  number of unconsolidated sessions pending
 * @param status            current sweep status
 * @param startedAt         timestamp when the sweep began
 * @param updatedAt         timestamp of this progress snapshot
 * @since 1.5.0
 */
public record ReflectSweepProgress(
        String sweepId,
        int sessionsCompleted,
        int factsIngested,
        int turnsMarked,
        int backlogRemaining,
        ReflectSweepStatus status,
        Instant startedAt,
        Instant updatedAt
) {
    public ReflectSweepProgress {
        Objects.requireNonNull(sweepId, "sweepId cannot be null");
        status = (status != null) ? status : ReflectSweepStatus.IDLE;
        startedAt = (startedAt != null) ? startedAt : Instant.now();
        updatedAt = (updatedAt != null) ? updatedAt : Instant.now();
    }

    public static ReflectSweepProgress from(ReflectCheckpoint checkpoint, Instant startedAt) {
        if (checkpoint == null) {
            return new ReflectSweepProgress("unknown", 0, 0, 0, 0, ReflectSweepStatus.IDLE, startedAt, Instant.now());
        }
        return new ReflectSweepProgress(
                checkpoint.sweepId(),
                checkpoint.sessionsCompleted(),
                checkpoint.factsIngested(),
                checkpoint.turnsMarked(),
                checkpoint.backlogRemaining(),
                checkpoint.status(),
                startedAt,
                checkpoint.updatedAt()
        );
    }
}
