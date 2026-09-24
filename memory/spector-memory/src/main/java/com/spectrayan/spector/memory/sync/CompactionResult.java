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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Result of a vacuum operation on a tier store.
 *
 * <p><b>This is currently a census, not a compaction.</b> No implementation relocates records or reclaims
 * space, so {@link #bytesReclaimed()} is always {@code 0} and {@link #compacted()} is always
 * {@code false}. Until issue #981/#983 this record carried {@code bytesReclaimed} computed as
 * {@code tombstoneCount * recordStride} — a multiplication, not a measurement — and the caller logged
 * "reclaimed {}KB" for an operation that wrote nothing. The documented REST endpoint described it as
 * purging tombstones and defragmenting off-heap pages.</p>
 *
 * <p>Real compaction is owned by {@code spectrayan/.kiro/specs/memory-durability-contract} R2, which
 * requires reclaimed bytes to be <i>measured</i> and id→record resolution preserved across relocation.</p>
 *
 * @param tier               the memory tier that was surveyed
 * @param beforeCount        total records surveyed (live + tombstoned)
 * @param afterCount         live records
 * @param tombstonesRemoved  tombstoned records <b>found</b>; none are removed while {@code compacted} is
 *                           {@code false}
 * @param bytesReclaimed     bytes actually freed. Always {@code 0} until compaction exists — never a
 *                           computed estimate
 * @param durationMs         survey duration in milliseconds
 * @param compacted          whether records were relocated and space reclaimed
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
public record CompactionResult(
    MemoryType tier,
    int beforeCount,
    int afterCount,
    int tombstonesRemoved,
    long bytesReclaimed,
    long durationMs,
    boolean compacted
) {
    /**
     * Creates a census result: tombstones counted, nothing reclaimed.
     *
     * @param tier              the tier surveyed
     * @param beforeCount       total records surveyed
     * @param afterCount        live records
     * @param tombstonesFound   tombstoned records found
     * @param durationMs        survey duration
     * @return a result reporting zero reclaimed bytes and {@code compacted=false}
     */
    public static CompactionResult census(MemoryType tier, int beforeCount, int afterCount,
                                          int tombstonesFound, long durationMs) {
        return new CompactionResult(tier, beforeCount, afterCount, tombstonesFound, 0L, durationMs, false);
    }
}
