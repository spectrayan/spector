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
