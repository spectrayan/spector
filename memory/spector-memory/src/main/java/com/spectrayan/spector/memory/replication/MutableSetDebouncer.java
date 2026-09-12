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
package com.spectrayan.spector.memory.replication;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Debounces mutable-set snapshot generation based on both a minimum interval and a minimum
 * change count (ADR-0034 §9.7, §10, Req R3.2, Task 3.3).
 *
 * <p>Default configuration matches Redis-style persistence:
 * {@code snapshotInterval=60s} AND {@code snapshotMinChanges=100}.</p>
 */
public final class MutableSetDebouncer {

    public static final long DEFAULT_SNAPSHOT_INTERVAL_MS = 60_000L; // 60s
    public static final long DEFAULT_SNAPSHOT_MIN_CHANGES = 100L;

    private final long snapshotIntervalMs;
    private final long snapshotMinChanges;

    private final AtomicLong lastSnapshotTimeMs;
    private final AtomicLong changeCountSinceLastSnapshot = new AtomicLong(0);

    public MutableSetDebouncer() {
        this(DEFAULT_SNAPSHOT_INTERVAL_MS, DEFAULT_SNAPSHOT_MIN_CHANGES, System.currentTimeMillis());
    }

    public MutableSetDebouncer(long snapshotIntervalMs, long snapshotMinChanges, long initialTimeMs) {
        this.snapshotIntervalMs = snapshotIntervalMs;
        this.snapshotMinChanges = snapshotMinChanges;
        this.lastSnapshotTimeMs = new AtomicLong(initialTimeMs);
    }

    /**
     * Records one or more mutations against the active mutable set.
     */
    public void recordMutations(long count) {
        if (count > 0) {
            changeCountSinceLastSnapshot.addAndGet(count);
        }
    }

    /**
     * Checks whether a snapshot should be triggered given the current timestamp.
     *
     * <p>Both the elapsed time since the last snapshot must exceed {@code snapshotIntervalMs}
     * AND the recorded mutations must exceed {@code snapshotMinChanges}.</p>
     *
     * @param nowMs current epoch milliseconds
     * @return true if both interval and change thresholds are satisfied
     */
    public boolean shouldSnapshot(long nowMs) {
        long elapsed = nowMs - lastSnapshotTimeMs.get();
        long changes = changeCountSinceLastSnapshot.get();
        return elapsed >= snapshotIntervalMs && changes >= snapshotMinChanges;
    }

    /**
     * Marks that a snapshot has been generated, resetting the debounce counter and updating
     * the last snapshot timestamp.
     *
     * @param nowMs timestamp of snapshot generation
     */
    public void onSnapshotProduced(long nowMs) {
        lastSnapshotTimeMs.set(nowMs);
        changeCountSinceLastSnapshot.set(0);
    }

    public long lastSnapshotTimeMs() {
        return lastSnapshotTimeMs.get();
    }

    public long changeCountSinceLastSnapshot() {
        return changeCountSinceLastSnapshot.get();
    }

    public long snapshotIntervalMs() {
        return snapshotIntervalMs;
    }

    public long snapshotMinChanges() {
        return snapshotMinChanges;
    }
}
