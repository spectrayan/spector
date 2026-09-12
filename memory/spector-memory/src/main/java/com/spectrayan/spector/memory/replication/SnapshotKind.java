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

/**
 * Distinguishes the replication scope of a snapshot (ADR-0034 §9.7, §15.2, Req R1.4, Task 1.4).
 */
public enum SnapshotKind {

    /**
     * Complete snapshot carrying all sealed partitions, active mutable partition,
     * runtime bundle, and WAL records up to the high-water mark.
     */
    FULL,

    /**
     * Incremental snapshot shipping only the mutable set (runtime bundle and active partition)
     * plus the WAL slice between the prior and current high-water marks.
     */
    INCREMENTAL,

    /**
     * Single freshly sealed partition bundle shipped immediately upon roll.
     */
    SEALED_ONLY
}
