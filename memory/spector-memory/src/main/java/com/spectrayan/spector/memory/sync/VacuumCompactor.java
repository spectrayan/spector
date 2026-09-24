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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Surveys a tier store for tombstoned records.
 *
 * <p><b>Despite the name, this does not compact.</b> It counts live versus tombstoned records and returns
 * the census. No record is relocated, no byte is zeroed, no space is reclaimed, and no index or graph
 * structure is rewritten.</p>
 *
 * <p>Until #983 it computed {@code bytesReclaimed = tombstoneCount * recordStride} and logged
 * "reclaimed {}KB", so the documented REST endpoint reported freeing space it had not freed. The name is
 * retained rather than changed because {@code memory-durability-contract} R2 implements real compaction
 * here; renaming now and back later would churn callers for no gain. The javadoc and the returned
 * {@code compacted=false} carry the truth in the meantime.</p>
 *
 * @see com.spectrayan.spector.memory.sync.CompactionResult
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
public final class VacuumCompactor {

    private static final Logger log = LoggerFactory.getLogger(VacuumCompactor.class);

    /**
     * Tombstone ratio at which compaction would be worthwhile, once compaction exists.
     *
     * <p>Documented as 20% in {@code spector-yml.md}. Note the consolidation docs claimed a 30% automatic
     * partition rebuild, which was a conflation with {@code circadian.tombstone-threshold} — a different
     * knob governing when episodic memories are tombstoned, not when a partition is rebuilt.</p>
     */
    public static final float DEFAULT_THRESHOLD = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_VACUUM_DEFAULT_THRESHOLD;

    private VacuumCompactor() {} // utility class

    /**
     * Surveys a tier store, counting live versus tombstoned records.
     *
     * @param store the tier store to survey
     * @param type  the memory tier type
     * @return the census, or {@code null} if the store is absent or holds no tombstones
     */
    public static CompactionResult compact(EngramRegion store, MemoryType type) {
        if (store == null) {
            log.warn("Vacuum: store for {} is null, cannot survey", type);
            return null;
        }
        long startMs = System.currentTimeMillis();

        int totalRecords = store.size();

        // Phase 1: Count live and tombstoned records
        int liveCount = 0;
        int tombstoneCount = 0;
        for (int i = 0; i < totalRecords; i++) {
            long offset = store.recordOffset(i);
            if (store.isTombstoned(offset)) {
                tombstoneCount++;
            } else {
                liveCount++;
            }
        }

        if (tombstoneCount == 0) {
            log.info("Vacuum: {} has no tombstoned records, skipping", type);
            return null;
        }

        long durationMs = System.currentTimeMillis() - startMs;

        // Reports 0 reclaimed bytes and compacted=false because nothing is reclaimed. The previous
        // implementation returned tombstoneCount * stride -- a multiplication presented as a measurement --
        // and logged "reclaimed {}KB" for an operation that performed no write. An operator calling the
        // documented endpoint saw a success response quoting kilobytes freed and nothing had happened.
        CompactionResult result = CompactionResult.census(
                type, totalRecords, liveCount, tombstoneCount, durationMs);

        log.info("Vacuum census: {} — {} records ({} live, {} tombstoned) surveyed in {}ms. "
                        + "No compaction performed: reclamation is not implemented, so no space was freed "
                        + "and no record was relocated.",
                type, totalRecords, liveCount, tombstoneCount, durationMs);

        return result;
    }
}
