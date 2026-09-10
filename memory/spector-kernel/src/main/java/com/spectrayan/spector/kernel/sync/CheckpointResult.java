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
package com.spectrayan.spector.kernel.sync;

/**
 * Result metrics for a completed kernel checkpoint operation (R9.1).
 *
 * @param walHighWaterMark the sequence number of the highest checkpointed WAL event
 * @param flushedRegions number of storage regions synced to disk
 * @param durationNanos elapsed checkpoint duration in nanoseconds
 * @param success true if all specified regions flushed cleanly without I/O error
 */
public record CheckpointResult(
        long walHighWaterMark,
        int flushedRegions,
        long durationNanos,
        boolean success
) {
    public static CheckpointResult empty() {
        return new CheckpointResult(0L, 0, 0L, true);
    }
}
