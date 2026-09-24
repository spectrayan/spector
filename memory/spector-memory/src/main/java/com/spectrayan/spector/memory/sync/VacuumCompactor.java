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
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.store.IndexEntryMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executes tombstone compaction and surveys tier stores.
 *
 * <p>Implements {@code memory-durability-contract} R2:
 * <ul>
 *   <li><b>R2.1:</b> Relocates live records into dense sequential slots and physically measures reclaimed space</li>
 *   <li><b>R2.2:</b> Preserves ID-to-record resolution by updating {@link IndexEntryMemory} locations and reverse index</li>
 *   <li><b>R2.3:</b> Cleans up dead graph references via node detacher callback while keeping live monotonic graph slots stable</li>
 *   <li><b>R2.4:</b> Triggerable by threshold ({@link #DEFAULT_THRESHOLD}, 0.20) or explicit operator action</li>
 * </ul>
 *
 * @see com.spectrayan.spector.memory.sync.CompactionResult
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
public final class VacuumCompactor {

    private static final Logger log = LoggerFactory.getLogger(VacuumCompactor.class);

    /**
     * Tombstone ratio at which compaction is triggered (0.20 default).
     */
    public static final float DEFAULT_THRESHOLD =
            com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_VACUUM_DEFAULT_THRESHOLD;

    private VacuumCompactor() {} // utility class

    /**
     * Surveys a tier store, counting live versus tombstoned records without modifying layout.
     *
     * @param store the tier store to survey
     * @param type  the memory tier type
     * @return the census, or {@code null} if the store is absent or holds no tombstones
     */
    public static CompactionResult survey(EngramRegion store, MemoryType type) {
        if (store == null) {
            log.warn("Vacuum: store for {} is null, cannot survey", type);
            return null;
        }
        long startMs = System.currentTimeMillis();

        int totalRecords = store.size();
        int liveCount = 0;
        int tombstoneCount = 0;
        for (int i = 0; i < totalRecords; i++) {
            long offset = store.recordOffset(i);
            if (store.isTombstoned(offset) || store.isPurged(offset)) {
                tombstoneCount++;
            } else {
                liveCount++;
            }
        }

        if (tombstoneCount == 0) {
            log.debug("Vacuum: {} has no tombstoned records, skipping", type);
            return null;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        return CompactionResult.census(type, totalRecords, liveCount, tombstoneCount, durationMs);
    }

    /**
     * Compacts a tier store with forced execution and default partition 0.
     *
     * @param store the tier store to compact
     * @param type  the memory tier type
     * @return compaction result with measured reclaimed bytes, or null if no tombstones exist
     */
    public static CompactionResult compact(EngramRegion store, MemoryType type) {
        return compact(store, type, 0, null, null, DEFAULT_THRESHOLD, true);
    }

    /**
     * Compacts a tier store with full context.
     *
     * @param store        the tier store to compact
     * @param type         the memory tier type
     * @param partitionSeq the partition sequence number
     * @param index        the index entry memory to update (nullable for standalone stores)
     * @param nodeDetacher callback to detach graph edges for dead graph slots (nullable)
     * @param threshold    tombstone ratio threshold to trigger compaction
     * @param force        if true, compacts unconditionally if tombstones exist
     * @return compaction result with measured reclaimed bytes, or null if no tombstones exist
     */
    public static CompactionResult compact(
            EngramRegion store,
            MemoryType type,
            int partitionSeq,
            IndexEntryMemory index,
            Consumer<Integer> nodeDetacher,
            float threshold,
            boolean force
    ) {
        if (store == null) {
            log.warn("Vacuum: store for {} is null, cannot compact", type);
            return null;
        }

        if (store instanceof EpisodicMemory episodic) {
            return compactEpisodic(episodic, partitionSeq, index, nodeDetacher, threshold, force);
        } else if (store instanceof AbstractRecordMemory<?> arm) {
            return compactFixed(arm, type, partitionSeq, index, nodeDetacher, threshold, force);
        } else {
            log.warn("Vacuum: unsupported store type {} for physical compaction; falling back to survey",
                    store.getClass().getName());
            return survey(store, type);
        }
    }

    private static CompactionResult compactFixed(
            AbstractRecordMemory<?> arm,
            MemoryType type,
            int partitionSeq,
            IndexEntryMemory index,
            Consumer<Integer> nodeDetacher,
            float threshold,
            boolean force
    ) {
        long startMs = System.currentTimeMillis();
        int totalRecords = arm.visibleCount();
        if (totalRecords == 0) {
            return null;
        }

        int stride = arm.layout().recordStride();
        int tombstoneCount = 0;
        int liveCount = 0;

        for (int i = 0; i < totalRecords; i++) {
            long offset = arm.recordOffset(i);
            boolean isDead = (arm instanceof EngramRegion er)
                    ? (er.isTombstoned(offset) || er.isPurged(offset))
                    : false;
            if (isDead) {
                tombstoneCount++;
            } else {
                liveCount++;
            }
        }

        if (tombstoneCount == 0) {
            return null;
        }

        float ratio = (float) tombstoneCount / (float) totalRecords;
        if (!force && ratio < threshold) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("Vacuum census: {} partition {} — tombstone ratio {} below threshold {}, skipping compaction",
                    type, partitionSeq, ratio, threshold);
            return CompactionResult.census(type, totalRecords, liveCount, tombstoneCount, durationMs);
        }

        long beforeUsedBytes = (long) totalRecords * stride;
        int destSlot = 0;
        int tombstonesRemoved = 0;
        List<Integer> deadGraphSlots = new ArrayList<>();

        for (int srcSlot = 0; srcSlot < totalRecords; srcSlot++) {
            long srcOffset = arm.recordOffset(srcSlot);
            boolean isDead = (arm instanceof EngramRegion er)
                    ? (er.isTombstoned(srcOffset) || er.isPurged(srcOffset))
                    : false;

            if (isDead) {
                tombstonesRemoved++;
                if (index != null) {
                    String deadId = index.findIdByOffset(partitionSeq, type, srcOffset);
                    if (deadId != null) {
                        MemoryLocation deadLoc = index.locate(deadId);
                        if (deadLoc != null && deadLoc.graphSlot() >= 0) {
                            deadGraphSlots.add(deadLoc.graphSlot());
                        }
                        index.remove(deadId);
                    }
                }
                // Zero out dead slot
                arm.zeroRange(srcOffset, stride);
            } else {
                if (destSlot == srcSlot) {
                    destSlot++;
                } else {
                    long destOffset = arm.recordOffset(destSlot);
                    // 1. Copy live record to dense destOffset
                    arm.copyRecord(srcOffset, destOffset);
                    // 2. Relocate index entry atomically
                    if (index != null) {
                        String liveId = index.findIdByOffset(partitionSeq, type, srcOffset);
                        if (liveId != null) {
                            index.relocate(liveId, destOffset);
                        }
                    }
                    // 3. Zero out vacated slot
                    arm.zeroRange(srcOffset, stride);
                    destSlot++;
                }
            }
        }

        int newCount = destSlot;
        arm.resetCount(newCount);
        long afterUsedBytes = (long) newCount * stride;
        long bytesReclaimed = beforeUsedBytes - afterUsedBytes;

        if (nodeDetacher != null) {
            for (int slot : deadGraphSlots) {
                nodeDetacher.accept(slot);
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Vacuum compaction: {} partition {} — {} records -> {} live, {} tombstones removed, {} bytes reclaimed in {}ms",
                type, partitionSeq, totalRecords, newCount, tombstonesRemoved, bytesReclaimed, durationMs);
        return new CompactionResult(type, totalRecords, newCount, tombstonesRemoved, bytesReclaimed, durationMs, true);
    }

    private static CompactionResult compactEpisodic(
            EpisodicMemory episodic,
            int partitionSeq,
            IndexEntryMemory index,
            Consumer<Integer> nodeDetacher,
            float threshold,
            boolean force
    ) {
        long startMs = System.currentTimeMillis();
        long base = episodic.dataOffset();
        long oldUsedBytes = episodic.usedBytes();
        long limit = base + oldUsedBytes;

        var headerLayout = episodic.layout().headerLayout();
        int totalRecords = 0;
        int tombstoneCount = 0;
        int liveCount = 0;

        long current = base;
        while (current + EpisodicLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(episodic.segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(episodic.segment(), current);
                if (payloadBytes < 0 || current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                long recordLen = EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
                byte flags = headerLayout.readFlagsRecord(episodic.segment(), current);
                totalRecords++;
                if (EncodingHeaderFields.isTombstoned(flags) || EncodingHeaderFields.isPurged(flags)) {
                    tombstoneCount++;
                } else {
                    liveCount++;
                }
                current += recordLen;
            } else {
                break;
            }
        }

        if (tombstoneCount == 0) {
            return null;
        }

        float ratio = totalRecords > 0 ? (float) tombstoneCount / (float) totalRecords : 0.0f;
        if (!force && ratio < threshold) {
            long durationMs = System.currentTimeMillis() - startMs;
            return CompactionResult.census(MemoryType.EPISODIC, totalRecords, liveCount, tombstoneCount, durationMs);
        }

        long destOffset = base;
        current = base;
        int tombstonesRemoved = 0;
        List<Integer> deadGraphSlots = new ArrayList<>();

        while (current + EpisodicLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(episodic.segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(episodic.segment(), current);
                if (payloadBytes < 0 || current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                long recordLen = EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
                byte flags = headerLayout.readFlagsRecord(episodic.segment(), current);
                boolean isDead = EncodingHeaderFields.isTombstoned(flags) || EncodingHeaderFields.isPurged(flags);

                long relativeSrc = current - base;
                if (isDead) {
                    tombstonesRemoved++;
                    if (index != null) {
                        String deadId = index.findIdByOffset(partitionSeq, MemoryType.EPISODIC, relativeSrc);
                        if (deadId != null) {
                            MemoryLocation deadLoc = index.locate(deadId);
                            if (deadLoc != null && deadLoc.graphSlot() >= 0) {
                                deadGraphSlots.add(deadLoc.graphSlot());
                            }
                            index.remove(deadId);
                        }
                    }
                } else {
                    long relativeDest = destOffset - base;
                    if (destOffset == current) {
                        destOffset += recordLen;
                    } else {
                        episodic.copyBytes(current, destOffset, recordLen);
                        if (index != null) {
                            String liveId = index.findIdByOffset(partitionSeq, MemoryType.EPISODIC, relativeSrc);
                            if (liveId != null) {
                                index.relocate(liveId, relativeDest);
                            }
                        }
                        destOffset += recordLen;
                    }
                }
                current += recordLen;
            } else {
                break;
            }
        }

        long newUsedBytes = destOffset - base;
        if (destOffset < limit) {
            episodic.zeroBytes(destOffset, limit - destOffset);
        }
        episodic.resetUsedBytes(newUsedBytes, liveCount);
        long bytesReclaimed = oldUsedBytes - newUsedBytes;

        if (nodeDetacher != null) {
            for (int slot : deadGraphSlots) {
                nodeDetacher.accept(slot);
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Vacuum compaction: EPISODIC partition {} — {} turns -> {} live, {} tombstones removed, {} bytes reclaimed in {}ms",
                partitionSeq, totalRecords, liveCount, tombstonesRemoved, bytesReclaimed, durationMs);
        return new CompactionResult(MemoryType.EPISODIC, totalRecords, liveCount, tombstonesRemoved, bytesReclaimed, durationMs, true);
    }
}
