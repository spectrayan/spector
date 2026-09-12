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

import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks replication byte counts split by bundle type: sealed partitions vs mutable set
 * (ADR-0034 §11.2, Req R2.6, R11.2, Task 2.6).
 *
 * <p>Exposes {@code spector.replication.snapshot.bytes{type="sealed|mutable"}} observability
 * proving that sealed partitions are shipped at most once per replica (Invariant N1).</p>
 */
public final class ReplicationByteTracker {

    private final LongAdder sealedBytes = new LongAdder();
    private final LongAdder mutableBytes = new LongAdder();

    /**
     * Records bytes transferred for sealed partition bundles.
     */
    public void recordSealedBytes(long bytes) {
        if (bytes > 0) {
            sealedBytes.add(bytes);
        }
    }

    /**
     * Records bytes transferred for mutable bundles (runtime.bundle, active partition, WAL slice).
     */
    public void recordMutableBytes(long bytes) {
        if (bytes > 0) {
            mutableBytes.add(bytes);
        }
    }

    /**
     * Returns total sealed partition bytes replicated.
     */
    public long sealedBytes() {
        return sealedBytes.sum();
    }

    /**
     * Returns total mutable bundle and WAL bytes replicated.
     */
    public long mutableBytes() {
        return mutableBytes.sum();
    }

    /**
     * Resets counters to zero.
     */
    public void reset() {
        sealedBytes.reset();
        mutableBytes.reset();
    }
}
