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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.MemoryType;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

/**
 * Immutable summary metadata for a partition used for query-time pruning (#447).
 *
 * <p>Captures the exact temporal bounds, cumulative 64-bit synaptic tag Bloom filter,
 * and per-tier visible record counts of a partition to enable $O(1)$ irrelevance
 * gating during recall fan-out.</p>
 *
 * @param seq sequence number
 * @param minTimestampMs minimum record timestamp (inclusive, epoch ms)
 * @param maxTimestampMs maximum record timestamp (inclusive, epoch ms)
 * @param synapticTagMask cumulative bitwise-OR of all visible record tag masks
 * @param semanticCount visible semantic records
 * @param episodicCount visible episodic records
 * @param proceduralCount visible procedural records
 * @param writable whether this summary belongs to the active (writable) partition
 */
public record PartitionSummary(
        int seq,
        long minTimestampMs,
        long maxTimestampMs,
        long synapticTagMask,
        int semanticCount,
        int episodicCount,
        int proceduralCount,
        boolean writable
) {

    /** Default empty / unbound summary for uninitialized or in-memory stores. */
    public static final PartitionSummary UNBOUNDED =
            new PartitionSummary(0, 0L, Long.MAX_VALUE, 0L, 0, 0, 0, true);

    /**
     * Total visible records across all three partition-scoped tiers.
     */
    public int visibleRecordCount() {
        return semanticCount + episodicCount + proceduralCount;
    }

    /**
     * Visible record count for a specific memory tier in this partition.
     */
    public int countFor(MemoryType type) {
        if (type == null) return 0;
        return switch (type) {
            case SEMANTIC -> semanticCount;
            case EPISODIC -> episodicCount;
            case PROCEDURAL -> proceduralCount;
            case WORKING -> 0; // Working memory is global
        };
    }

    /**
     * Checks if this partition has visible records for any of the requested target types.
     */
    public boolean hasRecordsFor(MemoryType[] targetTypes, CognitiveMemoryRouter router) {
        if (writable && router != null) {
            boolean hasEpi = (router.episodicLog() != null && router.episodicLog().writePosition() > 0)
                    || (router.episodic() != null && router.episodic().visibleCount() > 0);
            boolean hasStore = (router.semantic() != null || router.procedural() != null || router.episodicLog() != null || router.episodic() != null);
            if (hasStore) {
                if (targetTypes == null || targetTypes.length == 0) {
                    return (router.semantic() != null && router.semantic().visibleCount() > 0)
                            || hasEpi
                            || (router.procedural() != null && router.procedural().visibleCount() > 0);
                }
                for (MemoryType t : targetTypes) {
                    if (t == MemoryType.SEMANTIC && router.semantic() != null && router.semantic().visibleCount() > 0) return true;
                    if (t == MemoryType.EPISODIC && hasEpi) return true;
                    if (t == MemoryType.PROCEDURAL && router.procedural() != null && router.procedural().visibleCount() > 0) return true;
                }
                return false;
            }
        }

        if (targetTypes == null || targetTypes.length == 0) {
            return visibleRecordCount() > 0;
        }
        for (MemoryType t : targetTypes) {
            if (countFor(t) > 0) return true;
        }
        return false;
    }

    /**
     * Scans record headers across all stores of a partition to compute an exact, sound
     * {@link PartitionSummary}.
     *
     * @param seq partition sequence number
     * @param dir partition directory (nullable in IN_MEMORY mode)
     * @param router partition tier router
     * @param writable whether the partition is active/writable
     * @param nextEpochSecs optional epoch seconds of the succeeding partition
     * @return exact partition summary
     */
    public static PartitionSummary fromRouter(int seq, Path dir, CognitiveMemoryRouter router,
                                              boolean writable, Long nextEpochSecs) {
        if (router == null) {
            return new PartitionSummary(seq, 0L, Long.MAX_VALUE, 0L, 0, 0, 0, writable);
        }

        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        long tagMask = 0L;
        int semCount = 0;
        int epiCount = 0;
        int procCount = 0;

        // 1. Semantic store
        if (router.semantic() != null && router.semantic().visibleCount() > 0) {
            MemorySegment segment = router.semantic().segment();
            CognitiveRecordLayout layout = router.semantic().cognitiveLayout();
            int count = router.semantic().visibleCount();
            int stride = layout.stride();
            long base = router.semantic().dataOffset();
            for (int i = 0; i < count; i++) {
                long offset = base + (long) i * stride;
                byte flags = layout.headerLayout().readFlags(segment, offset);
                if (SynapticHeaderConstants.isTombstoned(flags)) continue;
                semCount++;
                long ts = layout.headerLayout().readTimestamp(segment, offset);
                long tags = layout.headerLayout().readSynapticTags(segment, offset);
                if (ts > 0) {
                    minTs = Math.min(minTs, ts);
                    maxTs = Math.max(maxTs, ts);
                }
                tagMask |= tags;
            }
        }

        // 2. Episodic store
        if (router.episodicLog() != null && router.episodicLog().writePosition() > 0) {
            long base = router.episodicLog().dataOffset();
            long limit = base + router.episodicLog().writePosition();
            long current = base;
            while (current + SynapticHeaderConstants.HEADER_BYTES <= limit) {
                byte flags = router.episodicLog().segment().get(SynapticHeaderConstants.LAYOUT_FLAGS, current + SynapticHeaderConstants.OFFSET_FLAGS);
                int bodyLength = router.episodicLog().segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + SynapticHeaderConstants.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!SynapticHeaderConstants.isTombstoned(flags)) {
                    epiCount++;
                    long ts = EpisodicFieldAccessor.readTimestamp(router.episodicLog().segment(), current);
                    if (ts > 0) {
                        minTs = Math.min(minTs, ts);
                        maxTs = Math.max(maxTs, ts);
                    }
                }
                current += SynapticHeaderConstants.HEADER_BYTES + bodyLength;
            }
        }
        if (router.episodic() != null && router.episodicLog() == null && router.episodic().visibleCount() > 0) {
            for (EpisodicPartition part : router.episodic().partitions()) {
                if (part.visibleCount() <= 0) continue;
                MemorySegment segment = part.segment();
                CognitiveRecordLayout layout = part.layout();
                int count = part.visibleCount();
                int stride = layout.stride();
                long base = part.dataOffset();
                for (int i = 0; i < count; i++) {
                    long offset = base + (long) i * stride;
                    byte flags = layout.headerLayout().readFlags(segment, offset);
                    if (SynapticHeaderConstants.isTombstoned(flags)) continue;
                    epiCount++;
                    long ts = layout.headerLayout().readTimestamp(segment, offset);
                    long tags = layout.headerLayout().readSynapticTags(segment, offset);
                    if (ts > 0) {
                        minTs = Math.min(minTs, ts);
                        maxTs = Math.max(maxTs, ts);
                    }
                    tagMask |= tags;
                }
            }
        }

        // 3. Procedural store
        if (router.procedural() != null && router.procedural().visibleCount() > 0) {
            MemorySegment segment = router.procedural().segment();
            CognitiveRecordLayout layout = router.procedural().cognitiveLayout();
            int count = router.procedural().visibleCount();
            int stride = layout.stride();
            long base = router.procedural().dataOffset();
            for (int i = 0; i < count; i++) {
                long offset = base + (long) i * stride;
                byte flags = layout.headerLayout().readFlags(segment, offset);
                if (SynapticHeaderConstants.isTombstoned(flags)) continue;
                procCount++;
                long ts = layout.headerLayout().readTimestamp(segment, offset);
                long tags = layout.headerLayout().readSynapticTags(segment, offset);
                if (ts > 0) {
                    minTs = Math.min(minTs, ts);
                    maxTs = Math.max(maxTs, ts);
                }
                tagMask |= tags;
            }
        }

        // Extract directory epoch bounds for fallback and consistency
        long dirStartMs = 0L;
        long dirEndMs = Long.MAX_VALUE;
        if (dir != null && dir.getFileName() != null) {
            String dirName = dir.getFileName().toString();
            if (StorageLayout.isPartitionDir(dirName)) {
                try {
                    long epochSecs = StorageLayout.parsePartitionEpoch(dirName);
                    if (epochSecs > 0) {
                        dirStartMs = epochSecs * 1000L;
                    }
                } catch (RuntimeException ignored) {
                    // fall through
                }
            }
        }
        if (nextEpochSecs != null && nextEpochSecs > 0) {
            dirEndMs = nextEpochSecs * 1000L;
        }

        if (minTs == Long.MAX_VALUE) {
            minTs = dirStartMs;
        } else if (dirStartMs > 0) {
            minTs = Math.min(minTs, dirStartMs);
        }

        if (writable) {
            maxTs = Long.MAX_VALUE;
        } else {
            if (maxTs == Long.MIN_VALUE) {
                maxTs = dirEndMs;
            } else if (dirEndMs < Long.MAX_VALUE) {
                maxTs = Math.max(maxTs, dirEndMs);
            }
        }

        return new PartitionSummary(seq, minTs, maxTs, tagMask, semCount, epiCount, procCount, writable);
    }
}
