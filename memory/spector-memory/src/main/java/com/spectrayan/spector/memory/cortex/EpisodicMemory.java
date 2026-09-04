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

import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.EpisodeCodec;
import com.spectrayan.spector.memory.kernel.layout.EpisodeLayout;
import com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor;
import com.spectrayan.spector.memory.kernel.layout.compat.LegacyEpisodeHeaderReader;
import com.spectrayan.spector.memory.kernel.shape.AbstractAppendMemory;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Log-structured episodic conversation memory store (ADR-0010 / ADR-0030, D2 Option B).
 *
 * <h3>Record Format (Option B: 80B Fixed Framing Overhead)</h3>
 * <pre>
 *   +0    prefix          16B   payloadBytes (4) | sequence_id (4) | checksum (4) | magic (4)
 *   +16   EncodingHeader  64B   I, valence, arousal, tags, source, flags, timestamp
 *   +80   payload         N     conversation metadata + CBOR body
 *   next  = 80 + N
 * </pre>
 *
 * <h3>Dual-Read Backward Compatibility (R4.4)</h3>
 * <p>Supports reading legacy punned records (64B header + body) alongside new Option B
 * records via automatic format discrimination.</p>
 *
 * @since 1.4.0
 * @see EpisodeLayout
 * @see EpisodeCodec
 * @see EpisodicHeaderAccessor
 * @see LegacyEpisodeHeaderReader
 */
public final class EpisodicMemory extends AbstractAppendMemory<EpisodeLayout> implements EngramMemory {

    private static final Logger log = LoggerFactory.getLogger(EpisodicMemory.class);

    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicInteger liveTurnCount = new AtomicInteger(0);

    // ── Constructors ──

    /**
     * Creates a volatile (heap-backed) episodic memory store with given capacity and buffer size.
     */
    public EpisodicMemory(int capacity, long capacityBytes) {
        super(SystemMemoryId.EPISODIC.id(), EpisodeLayout.INSTANCE, capacity, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with given byte buffer size.
     */
    public EpisodicMemory(long capacityBytes) {
        this(0, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with default 16MB buffer.
     */
    public static EpisodicMemory heap() {
        return new EpisodicMemory(0, 16 * 1024 * 1024L);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with specified byte buffer size.
     */
    public static EpisodicMemory heap(long capacityBytes) {
        return new EpisodicMemory(0, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with specified record capacity and byte buffer size.
     */
    public static EpisodicMemory heap(int capacity, long capacityBytes) {
        return new EpisodicMemory(capacity, capacityBytes);
    }

    private EpisodicMemory(Arena arena, MemorySegment regionSlice, int capacity,
                           java.nio.file.Path bundlePath, boolean isNew) {
        super(SystemMemoryId.EPISODIC.id(), EpisodeLayout.INSTANCE, capacity,
              arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0),
              true, bundlePath, null, true);

        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    EpisodeLayout.INSTANCE.recordStride(),
                    EpisodeLayout.INSTANCE.layoutId(), now, now);
            log.info("EpisodicMemory initialized new bundle region in: {} ({}KB, cap={})",
                    bundlePath, regionSlice.byteSize() / 1024, capacity);
        } else {
            log.info("EpisodicMemory loaded from bundle region in: {} (cursor={}B, cap={})",
                    bundlePath, count, capacity);
            this.liveTurnCount.set(countLiveTurns());
        }
    }

    /**
     * Factory method for creating a bundle-backed episodic store with specified capacity.
     */
    public static EpisodicMemory fromBundle(Arena arena, MemorySegment regionSlice, int capacity,
                                            java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicMemory(arena, regionSlice, capacity, bundlePath, isNew);
    }

    /**
     * Factory method for creating a bundle-backed episodic store.
     */
    public static EpisodicMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                            java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicMemory(arena, regionSlice, 0, bundlePath, isNew);
    }

    // ── Write path ──

    /**
     * Appends a conversation turn with real affect and provenance (NF6, NF7).
     */
    public long appendTurn(ConversationRole role, int sequenceId,
                           long timestampMs, long sessionId,
                           byte[] body, short modelId,
                           int tokenIn, int tokenOut,
                           int latencyMs, long userId,
                           short soulVersion, SourceModality modality,
                           float importance, byte valence, byte arousal,
                           EngramSource source) {
        byte[] payload = EpisodeCodec.encode(role, sessionId, modelId, tokenIn, tokenOut, latencyMs, userId, body);
        int payloadBytes = payload.length;
        int totalRecordSize = EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes;

        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = EncodingHeaderFields.withSourceModality(flags, modality.ordinal());
        }

        EncodingHeader header = new EncodingHeader(
                timestampMs,
                sessionId,
                0.0f,
                importance,
                0,
                modelId,
                valence,
                flags,
                arousal,
                1.0f,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                soulVersion,
                0.0f,
                (byte) 0,
                source != null ? source : EngramSource.EXPERIENCED
        );

        writeLock.lock();
        try {
            if (capacity > 0 && liveTurnCount.get() >= capacity) {
                throw new SpectorMemoryTierFullException(MemoryType.EPISODIC.name(), capacity);
            }

            long writeOffset = dataOffset() + count;

            if (writeOffset + totalRecordSize > segment().byteSize()) {
                throw new IndexOutOfBoundsException(
                        "Episodic memory full: cursor=" + count + ", record=" + totalRecordSize
                                + ", capacity=" + (segment().byteSize() - dataOffset()));
            }

            // Write 64B EncodingHeader at writeOffset + 16
            layout().headerLayout().writeHeaderRecord(segment(), writeOffset, header);

            // Compute CRC32C over sequenceId, 64B header, and payload
            MemorySegment headerSlice = segment().asSlice(writeOffset + EpisodeLayout.PREFIX_BYTES, EpisodeLayout.HEADER_BYTES);
            int checksum = EpisodeCodec.computeChecksum(sequenceId, headerSlice, payload);

            // Write 16B prefix at writeOffset + 0
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset, payloadBytes);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 4, sequenceId);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 8, checksum);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 12, EpisodeLayout.MAGIC);

            // Write payload at writeOffset + 80
            MemorySegment.copy(
                    MemorySegment.ofArray(payload), 0,
                    segment(), writeOffset + EpisodeLayout.FIXED_OVERHEAD_BYTES,
                    payloadBytes);

            long recordOffset = count;
            count += totalRecordSize;
            persistCount();
            liveTurnCount.incrementAndGet();

            return recordOffset;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Backward-compatible overload for turns without explicit affect (defaults to 5.0f baseline importance).
     */
    public long appendTurn(ConversationRole role, int sequenceId,
                           long timestampMs, long sessionId,
                           byte[] body, short modelId,
                           int tokenIn, int tokenOut,
                           int latencyMs, long userId,
                           short soulVersion, SourceModality modality) {
        return appendTurn(role, sequenceId, timestampMs, sessionId, body, modelId,
                tokenIn, tokenOut, latencyMs, userId, soulVersion, modality,
                5.0f, (byte) 0, (byte) 0, EngramSource.EXPERIENCED);
    }

    // ── Read path ──

    /**
     * Reads an episodic record at the given byte offset (relative to dataOffset), with dual-read support.
     */
    public EpisodeRecord readTurn(long offset, boolean includeBody) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            int payloadBytes = headerLayout.readPayloadBytes(segment(), absoluteOffset);
            int sequenceId = headerLayout.readSequenceId(segment(), absoluteOffset);
            EncodingHeader header = headerLayout.readHeaderRecord(segment(), absoluteOffset);

            long payloadOffset = absoluteOffset + EpisodeLayout.FIXED_OVERHEAD_BYTES;
            EpisodeCodec.DecodedPayload decoded = EpisodeCodec.decode(segment(), payloadOffset, payloadBytes, includeBody);

            return new EpisodeRecord(
                    decoded.role(),
                    sequenceId,
                    header.timestampMs(),
                    decoded.sessionId(),
                    decoded.bodyLength(),
                    decoded.body(),
                    decoded.modelId(),
                    decoded.tokenIn(),
                    decoded.tokenOut(),
                    decoded.latencyMs(),
                    decoded.userId(),
                    header.soulVersion(),
                    headerLayout.readModalityRecord(segment(), absoluteOffset),
                    header.flags(),
                    header.importance(),
                    header.valence(),
                    header.arousal(),
                    header.source()
            );
        } else {
            return LegacyEpisodeHeaderReader.readRecord(segment(), absoluteOffset, includeBody);
        }
    }

    /**
     * Reads multiple episodic records at given byte offsets.
     */
    public List<EpisodeRecord> readTurns(List<Long> offsets, boolean includeBody) {
        List<EpisodeRecord> records = new ArrayList<>(offsets.size());
        for (long offset : offsets) {
            EpisodeRecord record = readTurn(offset, includeBody);
            if (!EncodingHeaderFields.isTombstoned(record.flags())) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Tombstones a record at the given relative byte offset.
     */
    public void tombstone(long offset) {
        long absoluteOffset = dataOffset() + offset;
        boolean wasTombstoned;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            wasTombstoned = headerLayout.isTombstonedRecord(segment(), absoluteOffset);
            headerLayout.tombstoneRecord(segment(), absoluteOffset);
        } else {
            byte flags = LegacyEpisodeHeaderReader.readFlags(segment(), absoluteOffset);
            wasTombstoned = EncodingHeaderFields.isTombstoned(flags);
            flags = (byte) (flags | EncodingHeaderFields.FLAG_TOMBSTONE);
            segment().set(ValueLayout.JAVA_BYTE, absoluteOffset + EncodingHeaderFields.OFFSET_FLAGS, flags);
        }
        if (!wasTombstoned) {
            liveTurnCount.decrementAndGet();
        }
    }

    /**
     * Marks a record as consolidated at the given relative byte offset.
     */
    public void markConsolidated(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            headerLayout.markConsolidatedRecord(segment(), absoluteOffset);
        } else {
            byte flags = LegacyEpisodeHeaderReader.readFlags(segment(), absoluteOffset);
            flags = (byte) (flags | EncodingHeaderFields.FLAG_CONSOLIDATED);
            segment().set(ValueLayout.JAVA_BYTE, absoluteOffset + EncodingHeaderFields.OFFSET_FLAGS, flags);
        }
    }

    /**
     * Marks a record as resolved (Zeigarnik Effect) at the given relative byte offset.
     */
    public void markResolved(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            headerLayout.markResolvedRecord(segment(), absoluteOffset);
        } else {
            byte flags = LegacyEpisodeHeaderReader.readFlags(segment(), absoluteOffset);
            flags = (byte) (flags | EncodingHeaderFields.FLAG_RESOLVED);
            segment().set(ValueLayout.JAVA_BYTE, absoluteOffset + EncodingHeaderFields.OFFSET_FLAGS, flags);
        }
    }

    /**
     * Marks a record as unresolved (Zeigarnik Effect) at the given relative byte offset.
     */
    public void markUnresolved(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            headerLayout.markUnresolvedRecord(segment(), absoluteOffset);
        } else {
            byte flags = LegacyEpisodeHeaderReader.readFlags(segment(), absoluteOffset);
            flags = (byte) (flags & ~EncodingHeaderFields.FLAG_RESOLVED);
            segment().set(ValueLayout.JAVA_BYTE, absoluteOffset + EncodingHeaderFields.OFFSET_FLAGS, flags);
        }
    }

    /**
     * Returns current write cursor position.
     */
    public long writePosition() {
        return count;
    }

    /**
     * Rebuilds session index from this store's mmap region.
     */
    public int rebuildSessionIndex(EpisodicSessionIndex sessionIndex) {
        return sessionIndex.rebuild(segment(), dataOffset(), dataOffset() + count);
    }

    /**
     * Scans this episodic memory from beginning to cursor, collecting offsets of all live,
     * non-consolidated turns.
     */
    public List<Long> unconsolidatedTurnOffsets() {
        List<Long> offsets = new ArrayList<>();
        long base = dataOffset();
        long limit = base + count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodeLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
                if (payloadBytes < 0 || current + EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                byte flags = headerLayout.readFlagsRecord(segment(), current);
                if (!EncodingHeaderFields.isTombstoned(flags) && !EncodingHeaderFields.isConsolidated(flags)) {
                    offsets.add(current - base);
                }
                current += EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                byte flags = segment().get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
                int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!EncodingHeaderFields.isTombstoned(flags) && !EncodingHeaderFields.isConsolidated(flags)) {
                    offsets.add(current - base);
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }

        return offsets;
    }

    /**
     * Returns the total count of live (non-tombstoned) turns in this episodic store.
     */
    public int liveTurnCount() {
        return liveTurnCount.get();
    }

    @Override
    public int size() {
        return liveTurnCount.get();
    }

    private int countLiveTurns() {
        int liveCount = 0;
        long base = dataOffset();
        long limit = base + this.count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodeLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
                if (payloadBytes < 0 || current + EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                byte flags = headerLayout.readFlagsRecord(segment(), current);
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                }
                current += EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                byte flags = segment().get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
                int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }
        return liveCount;
    }

    /**
     * Returns remaining unallocated bytes in this store.
     */
    public long remainingBytes() {
        return segment().byteSize() - dataOffset() - count;
    }

    /**
     * Flushes buffered state to underlying backing file.
     */
    public void force() {
        flush();
    }

    /**
     * Decays importance of unconsolidated turns older than thresholdMs by multiplying by factor.
     *
     * @param thresholdMs timestamp before which turns should decay
     * @param factor decay multiplier in [0.0, 1.0]
     * @return number of records decayed
     */
    public int decayOldTurns(long thresholdMs, float factor) {
        int decayed = 0;
        long base = dataOffset();
        long limit = base + count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodeLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
                if (payloadBytes < 0 || current + EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                byte flags = headerLayout.readFlagsRecord(segment(), current);
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    long ts = headerLayout.readTimestampRecord(segment(), current);
                    if (ts < thresholdMs) {
                        float oldImp = headerLayout.readImportanceRecord(segment(), current);
                        headerLayout.writeImportanceRecord(segment(), current, oldImp * factor);
                        decayed++;
                    }
                }
                current += EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }
        return decayed;
    }

    // ── EngramMemory Implementation (ADR-0030) ──

    @Override
    public MemoryType type() {
        return MemoryType.EPISODIC;
    }

    @Override
    public int visibleCount() {
        return liveTurnCount.get();
    }

    @Override
    public float tombstoneRatio() {
        return 0.0f;
    }

    @Override
    public MemorySegment headerSlab() {
        return null;
    }

    @Override
    public long recordOffset(long index) {
        throw new UnsupportedOperationException("EpisodicMemory records are variable-length; recordOffset by index is unsupported");
    }

    @Override
    public long write(EncodingHeader header, byte[] payload) {
        return appendTurn(
                ConversationRole.SYSTEM, 0,
                header.timestampMs(), header.synapticTags(),
                payload != null ? payload : new byte[0],
                (short) 0, 0, 0, 0, 0L,
                header.soulVersion(),
                SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(header.flags())),
                header.importance(), header.valence(), header.arousal(),
                header.source()
        );
    }

    /**
     * Checks if the record at the given relative byte offset is tombstoned.
     */
    public boolean isTombstoned(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            return headerLayout.isTombstonedRecord(segment(), absoluteOffset);
        } else {
            return EncodingHeaderFields.isTombstoned(LegacyEpisodeHeaderReader.readFlags(segment(), absoluteOffset));
        }
    }

    /**
     * Reads the 64-byte encoding header of a record at the given relative byte offset.
     */
    public EncodingHeader readHeader(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            return headerLayout.readHeaderRecord(segment(), absoluteOffset);
        } else {
            var rec = LegacyEpisodeHeaderReader.readRecord(segment(), absoluteOffset, false);
            return new EncodingHeader(
                    rec.timestampMs(), rec.sessionId(), 0.0f, rec.importance(), 0, (short) 0,
                    rec.valence(), rec.flags(), rec.arousal(), 1.0f, (byte) 0, (byte) 0, (byte) 0,
                    rec.soulVersion(), 0.0f, (byte) 0, rec.source() != null ? rec.source() : EngramSource.EXPERIENCED
            );
        }
    }
}
