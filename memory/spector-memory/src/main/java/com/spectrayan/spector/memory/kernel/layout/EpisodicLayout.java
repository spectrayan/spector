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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.memory.kernel.engram.EncodingHeader;

import com.spectrayan.spector.memory.kernel.engram.EpisodicHeaderLayout;

import com.spectrayan.spector.memory.kernel.layout.RegionLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Dedicated record layout for the Episodic memory tier (ADR-0030).
 *
 * <p>Variable-length append-only record layout pairing a 16-byte framing prefix,
 * a 64-byte {@link EpisodicHeaderLayout}, and a variable-length CBOR payload.</p>
 *
 * @param headerLayout dedicated episodic encoding header layout
 * @since 1.5.0
 * @see EpisodicHeaderLayout
 */
public record EpisodicLayout(
        EpisodicHeaderLayout headerLayout
) implements RegionLayout {

    public static final int LAYOUT_ID = 0x4550494C; // 'EPIL'
    public static final int VERSION = 2;
    public static final int PREFIX_BYTES = 16;
    public static final int HEADER_BYTES = 64;
    public static final int FIXED_OVERHEAD_BYTES = PREFIX_BYTES + HEADER_BYTES; // 80
    public static final int MAGIC = 0x45504953; // 'EPIS'

    public static final EpisodicLayout INSTANCE = new EpisodicLayout();

    public EpisodicLayout() {
        this(EpisodicHeaderLayout.defaultLayout());
    }

    public static EpisodicLayout defaultLayout() {
        return INSTANCE;
    }

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    @Override
    public int recordStride() {
        return 0; // variable-length
    }

    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "EpisodicLayout";
    }

    /**
     * Summary statistics collected from an episodic framing walk.
     *
     * @param liveCount number of non-tombstoned episodic records
     * @param minTimestampMs minimum record timestamp in epoch milliseconds (0 if none)
     * @param maxTimestampMs maximum record timestamp in epoch milliseconds (0 if none)
     */
    public record FramingStats(int liveCount, long minTimestampMs, long maxTimestampMs) {}

    /**
     * Walks the episodic framing records in {@code segment} from {@code base} up to {@code limit},
     * collecting summary statistics without leaking raw segment offsets or layout internals.
     *
     * @param segment off-heap memory segment backing episodic memory
     * @param base starting byte offset of the record framing sequence
     * @param limit byte offset limit for the walk
     * @return summary statistics of the walked records
     */
    public static FramingStats walkFraming(MemorySegment segment, long base, long limit) {
        if (segment == null || base < 0 || limit <= base) {
            return new FramingStats(0, 0L, 0L);
        }
        int liveCount = 0;
        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        long current = base;

        while (current < limit) {
            if (current + 16 > limit) {
                break;
            }
            int magic = segment.get(ValueLayout.JAVA_INT_UNALIGNED, current);
            if (magic == MAGIC) {
                // Option B record
                int payloadBytes = segment.get(ValueLayout.JAVA_INT_UNALIGNED, current + 4);
                if (payloadBytes < 0 || current + FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                long headerOffset = current + PREFIX_BYTES;
                byte flags = segment.get(EncodingHeaderFields.LAYOUT_FLAGS, headerOffset + EncodingHeaderFields.OFFSET_FLAGS);
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                    long ts = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOffset + EncodingHeaderFields.OFFSET_TIMESTAMP_MS);
                    if (ts > 0) {
                        minTs = Math.min(minTs, ts);
                        maxTs = Math.max(maxTs, ts);
                    }
                }
                current += FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                // Legacy punned turn (64B header + body)
                if (current + EncodingHeaderFields.HEADER_BYTES > limit) {
                    break;
                }
                byte flags = segment.get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
                int bodyLength = segment.get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                    long ts = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, current + EncodingHeaderFields.OFFSET_TIMESTAMP_MS);
                    if (ts > 0) {
                        minTs = Math.min(minTs, ts);
                        maxTs = Math.max(maxTs, ts);
                    }
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }
        return new FramingStats(
                liveCount,
                minTs == Long.MAX_VALUE ? 0L : minTs,
                maxTs == Long.MIN_VALUE ? 0L : maxTs
        );
    }
}
