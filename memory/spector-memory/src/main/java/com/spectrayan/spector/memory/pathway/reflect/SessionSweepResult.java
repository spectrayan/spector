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

/**
 * Result of consolidating an individual session within a reflection sweep.
 *
 * @param sessionId         the episodic session ID
 * @param factsConsolidated number of semantic facts synthesized and ingested
 * @param turnsMarked       number of turns stamped as consolidated
 * @param success           true if distillation and ingestion succeeded
 * @param error             underlying error if consolidation failed, or null
 * @since 1.5.0
 */
public record SessionSweepResult(
        long sessionId,
        int factsConsolidated,
        int turnsMarked,
        boolean success,
        Throwable error
) {
    public static SessionSweepResult success(long sessionId, int factsConsolidated, int turnsMarked) {
        return new SessionSweepResult(sessionId, factsConsolidated, turnsMarked, true, null);
    }

    public static SessionSweepResult failure(long sessionId, Throwable error) {
        return new SessionSweepResult(sessionId, 0, 0, false, error);
    }
}
