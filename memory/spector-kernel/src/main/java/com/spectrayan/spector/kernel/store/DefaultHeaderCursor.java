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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.HeaderCursor;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.kernel.bundle.RegionLease;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;
import com.spectrayan.spector.kernel.engram.EpisodicHeaderLayout;
import com.spectrayan.spector.kernel.engram.FloatUnaryOperator;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.error.StaleRegionException;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Default implementation of {@link HeaderCursor} satisfying the D8 generation contract (R6.2, R6.3).
 *
 * <p>Pins the region generation on creation; {@link #seek(int)} and {@link #seekOffset(long)}
 * throw {@link StaleRegionException} if {@code growRegion} intervened.
 * {@link #close()} releases the active region lease and drops the generation pin.</p>
 */
public final class DefaultHeaderCursor implements HeaderCursor {

    private final RegionRef engramRef;
    private final MemorySegment fixedSegment; // used when not bundle-backed (e.g. standalone tests)
    private final EncodingHeaderLayout headerLayout;
    private final EpisodicHeaderLayout episodicHeaderLayout;
    private final StrengthMemory strengthMemory;
    private final MemoryType tier;
    private final int capacity;
    private final long dataOffset;
    private final int stride;

    private final int pinnedEngramGeneration;
    private final RegionLease engramLease;
    private final int pinnedStrengthGeneration;
    private final RegionLease strengthLease;

    private int currentSlot = -1;
    private long currentOffset = -1L;
    private volatile boolean closed = false;

    /**
     * Creates a bundle-backed HeaderCursor for a fixed-stride engram store.
     */
    public DefaultHeaderCursor(RegionRef engramRef,
                               FixedEngramLayout layout,
                               MemoryType tier,
                               int capacity,
                               long dataOffset,
                               StrengthMemory strengthMemory) {
        this.engramRef = Objects.requireNonNull(engramRef, "engramRef");
        this.fixedSegment = null;
        this.headerLayout = layout != null ? layout.headerLayout() : EncodingHeaderLayout.INSTANCE;
        this.episodicHeaderLayout = (layout instanceof EpisodicHeaderLayout ehl) ? ehl : null;
        this.tier = tier != null ? tier : MemoryType.SEMANTIC;
        this.capacity = capacity;
        this.dataOffset = dataOffset;
        this.stride = layout != null ? layout.stride() : 64;
        this.strengthMemory = strengthMemory;

        this.pinnedEngramGeneration = engramRef.generation();
        this.engramLease = engramRef.lease();

        if (strengthMemory != null && strengthMemory.regionRef() != null) {
            this.pinnedStrengthGeneration = strengthMemory.regionRef().generation();
            this.strengthLease = strengthMemory.regionRef().lease();
        } else {
            this.pinnedStrengthGeneration = 0;
            this.strengthLease = null;
        }
    }

    /**
     * Creates a bundle-backed HeaderCursor for Episodic variable-length memory.
     */
    public DefaultHeaderCursor(RegionRef engramRef,
                               EpisodicLayout layout,
                               long dataOffset,
                               int capacity,
                               StrengthMemory strengthMemory) {
        this.engramRef = Objects.requireNonNull(engramRef, "engramRef");
        this.fixedSegment = null;
        this.headerLayout = EpisodicHeaderLayout.INSTANCE;
        this.episodicHeaderLayout = EpisodicHeaderLayout.INSTANCE;
        this.tier = MemoryType.EPISODIC;
        this.capacity = capacity;
        this.dataOffset = dataOffset;
        this.stride = -1; // variable length
        this.strengthMemory = strengthMemory;

        this.pinnedEngramGeneration = engramRef.generation();
        this.engramLease = engramRef.lease();

        if (strengthMemory != null && strengthMemory.regionRef() != null) {
            this.pinnedStrengthGeneration = strengthMemory.regionRef().generation();
            this.strengthLease = strengthMemory.regionRef().lease();
        } else {
            this.pinnedStrengthGeneration = 0;
            this.strengthLease = null;
        }
    }

    /**
     * Creates a segment-backed HeaderCursor (for standalone tests or volatile in-memory stores).
     */
    public static DefaultHeaderCursor forSegment(MemorySegment segment, FixedEngramLayout layout) {
        return new DefaultHeaderCursor(segment, layout, MemoryType.SEMANTIC,
                layout != null ? (int) (segment.byteSize() / layout.stride()) : 1, 0, null);
    }

    public static DefaultHeaderCursor forSegment(MemorySegment segment, int stride) {
        return new DefaultHeaderCursor(segment, null, MemoryType.SEMANTIC,
                stride > 0 ? (int) (segment.byteSize() / stride) : 1, 0, null);
    }

    public DefaultHeaderCursor(MemorySegment segment,
                               EpisodicLayout layout,
                               long dataOffset,
                               int capacity,
                               StrengthMemory strengthMemory) {
        this.engramRef = null;
        this.fixedSegment = Objects.requireNonNull(segment, "segment");
        this.headerLayout = EpisodicHeaderLayout.INSTANCE;
        this.episodicHeaderLayout = EpisodicHeaderLayout.INSTANCE;
        this.tier = MemoryType.EPISODIC;
        this.capacity = capacity;
        this.dataOffset = dataOffset;
        this.stride = -1;
        this.strengthMemory = strengthMemory;

        this.pinnedEngramGeneration = 0;
        this.engramLease = null;
        this.pinnedStrengthGeneration = 0;
        this.strengthLease = null;
    }

    public DefaultHeaderCursor(MemorySegment segment,
                               FixedEngramLayout layout,
                               MemoryType tier,
                               int capacity,
                               long dataOffset,
                               StrengthMemory strengthMemory) {
        this.engramRef = null;
        this.fixedSegment = Objects.requireNonNull(segment, "segment");
        this.headerLayout = layout != null ? layout.headerLayout() : EncodingHeaderLayout.INSTANCE;
        this.episodicHeaderLayout = (layout instanceof EpisodicHeaderLayout ehl) ? ehl : null;
        this.tier = tier != null ? tier : MemoryType.SEMANTIC;
        this.capacity = capacity;
        this.dataOffset = dataOffset;
        this.stride = layout != null ? layout.stride() : 64;
        this.strengthMemory = strengthMemory;

        this.pinnedEngramGeneration = 0;
        this.engramLease = null;
        this.pinnedStrengthGeneration = 0;
        this.strengthLease = null;
    }

    private MemorySegment segment() {
        return engramRef != null ? engramRef.resolve() : fixedSegment;
    }

    private void checkPosition() {
        if (closed) {
            throw new IllegalStateException("HeaderCursor is closed");
        }
        if (currentOffset < 0) {
            throw new IllegalStateException("HeaderCursor is not positioned; call seek() first");
        }
        if (engramRef != null && engramRef.generation() != pinnedEngramGeneration) {
            throw new StaleRegionException(pinnedEngramGeneration, engramRef.generation());
        }
        if (strengthMemory != null && strengthMemory.regionRef() != null
                && strengthMemory.regionRef().generation() != pinnedStrengthGeneration) {
            throw new StaleRegionException(pinnedStrengthGeneration, strengthMemory.regionRef().generation());
        }
    }

    private long headerOffset() {
        if (episodicHeaderLayout != null) {
            return currentOffset + EpisodicLayout.PREFIX_BYTES;
        }
        return currentOffset;
    }

    @Override
    public HeaderCursor seek(int slot) {
        if (closed) {
            throw new IllegalStateException("HeaderCursor is closed");
        }
        if (engramRef != null && engramRef.generation() != pinnedEngramGeneration) {
            throw new StaleRegionException(pinnedEngramGeneration, engramRef.generation());
        }
        if (strengthMemory != null && strengthMemory.regionRef() != null
                && strengthMemory.regionRef().generation() != pinnedStrengthGeneration) {
            throw new StaleRegionException(pinnedStrengthGeneration, strengthMemory.regionRef().generation());
        }
        if (slot < 0 || (capacity > 0 && slot >= capacity)) {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of bounds [0, " + capacity + ")");
        }
        if (stride <= 0) {
            throw new UnsupportedOperationException("seek(slot) is not supported for variable-length records; use seekOffset(byteOffset)");
        }
        this.currentSlot = slot;
        this.currentOffset = dataOffset + (long) slot * stride;
        return this;
    }

    @Override
    public HeaderCursor seekOffset(long byteOffset) {
        if (closed) {
            throw new IllegalStateException("HeaderCursor is closed");
        }
        if (engramRef != null && engramRef.generation() != pinnedEngramGeneration) {
            throw new StaleRegionException(pinnedEngramGeneration, engramRef.generation());
        }
        if (strengthMemory != null && strengthMemory.regionRef() != null
                && strengthMemory.regionRef().generation() != pinnedStrengthGeneration) {
            throw new StaleRegionException(pinnedStrengthGeneration, strengthMemory.regionRef().generation());
        }
        long maxBytes = segment().byteSize();
        if (byteOffset < 0 || byteOffset >= maxBytes) {
            throw new IndexOutOfBoundsException("Offset " + byteOffset + " out of bounds [0, " + maxBytes + ")");
        }
        this.currentOffset = byteOffset;
        this.currentSlot = (stride > 0 && byteOffset >= dataOffset)
                ? (int) ((byteOffset - dataOffset) / stride)
                : -1;
        return this;
    }

    @Override
    public int currentSlot() {
        return currentSlot;
    }

    @Override
    public long currentOffset() {
        return currentOffset;
    }

    @Override
    public byte headerVersion() {
        checkPosition();
        return headerLayout.readHeaderVersion(segment(), headerOffset());
    }

    @Override
    public byte flags() {
        checkPosition();
        return headerLayout.readFlags(segment(), headerOffset());
    }

    @Override
    public void flags(byte flags) {
        checkPosition();
        headerLayout.writeFlags(segment(), headerOffset(), flags);
    }

    @Override
    public byte valence() {
        checkPosition();
        return headerLayout.readValence(segment(), headerOffset());
    }

    @Override
    public void valenceRelease(byte v) {
        checkPosition();
        headerLayout.writeValenceRelease(segment(), headerOffset(), v);
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeLastRecallValence(tier, currentSlot, v);
        }
    }

    @Override
    public boolean compareAndSetValence(byte expected, byte update) {
        checkPosition();
        boolean ok = headerLayout.compareAndSetValence(segment(), headerOffset(), expected, update);
        if (ok && strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeLastRecallValence(tier, currentSlot, update);
        }
        return ok;
    }

    @Override
    public byte arousal() {
        checkPosition();
        return headerLayout.readArousal(segment(), headerOffset());
    }

    @Override
    public void arousal(byte arousal) {
        checkPosition();
        headerLayout.writeArousal(segment(), headerOffset(), arousal);
    }

    @Override
    public float importance() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readEffectiveImportance(tier, currentSlot);
        }
        return headerLayout.readImportance(segment(), headerOffset());
    }

    @Override
    public void initializeImportance(float importance) {
        checkPosition();
        headerLayout.writeImportance(segment(), headerOffset(), importance);
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeEffectiveImportance(tier, currentSlot, importance);
        }
    }

    @Override
    public float updateImportance(FloatUnaryOperator fn) {
        checkPosition();
        float next = headerLayout.casImportance(segment(), headerOffset(), fn);
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.casEffectiveImportance(tier, currentSlot, fn);
        }
        return next;
    }

    @Override
    public long timestampMs() {
        checkPosition();
        return headerLayout.readTimestamp(segment(), headerOffset());
    }

    @Override
    public void timestampMs(long timestampMs) {
        checkPosition();
        headerLayout.writeTimestamp(segment(), headerOffset(), timestampMs);
    }

    @Override
    public float exactNorm() {
        checkPosition();
        return headerLayout.readExactNorm(segment(), headerOffset());
    }

    @Override
    public void exactNorm(float exactNorm) {
        checkPosition();
        headerLayout.writeExactNorm(segment(), headerOffset(), exactNorm);
    }

    @Override
    public short centroidId() {
        checkPosition();
        return headerLayout.readCentroidId(segment(), headerOffset());
    }

    @Override
    public void centroidId(short centroidId) {
        checkPosition();
        headerLayout.writeCentroidId(segment(), headerOffset(), centroidId);
    }

    @Override
    public long synapticTagsLo() {
        checkPosition();
        return headerLayout.readSynapticTagsLo(segment(), headerOffset());
    }

    @Override
    public long synapticTagsHi() {
        checkPosition();
        return headerLayout.readSynapticTagsHi(segment(), headerOffset());
    }

    @Override
    public void initializeSynapticTags(long lo, long hi) {
        checkPosition();
        headerLayout.writeSynapticTags(segment(), headerOffset(), lo, hi);
    }

    @Override
    public void mergeSynapticTags(long lo, long hi) {
        checkPosition();
        headerLayout.mergeSynapticTags128(segment(), headerOffset(), lo, hi);
    }

    @Override
    public byte consolidationFlags() {
        checkPosition();
        return headerLayout.readConsolidationFlags(segment(), headerOffset());
    }

    @Override
    public void consolidationFlags(byte flags) {
        checkPosition();
        headerLayout.writeConsolidationFlags(segment(), headerOffset(), flags);
    }

    @Override
    public byte encodingProfile() {
        checkPosition();
        return headerLayout.readEncodingProfile(segment(), headerOffset());
    }

    @Override
    public void encodingProfile(byte profile) {
        checkPosition();
        headerLayout.writeEncodingProfile(segment(), headerOffset(), profile);
    }

    @Override
    public byte encodingAlpha() {
        checkPosition();
        return headerLayout.readEncodingAlpha(segment(), headerOffset());
    }

    @Override
    public void encodingAlpha(byte alpha) {
        checkPosition();
        headerLayout.writeEncodingAlpha(segment(), headerOffset(), alpha);
    }

    @Override
    public byte encodingBeta() {
        checkPosition();
        return headerLayout.readEncodingBeta(segment(), headerOffset());
    }

    @Override
    public void encodingBeta(byte beta) {
        checkPosition();
        headerLayout.writeEncodingBeta(segment(), headerOffset(), beta);
    }

    @Override
    public short soulVersion() {
        checkPosition();
        return headerLayout.readSoulVersion(segment(), headerOffset());
    }

    @Override
    public void soulVersion(short soulVersion) {
        checkPosition();
        headerLayout.writeSoulVersion(segment(), headerOffset(), soulVersion);
    }

    @Override
    public byte sourceCode() {
        checkPosition();
        return headerLayout.readSourceCode(segment(), headerOffset());
    }

    @Override
    public void sourceCode(byte sourceCode) {
        checkPosition();
        headerLayout.writeSourceCode(segment(), headerOffset(), sourceCode);
    }

    @Override
    public EngramSource source() {
        checkPosition();
        return headerLayout.readSource(segment(), headerOffset());
    }

    @Override
    public void source(EngramSource source) {
        checkPosition();
        headerLayout.writeSource(segment(), headerOffset(), source);
    }

    @Override
    public float encodingSurprise() {
        checkPosition();
        return headerLayout.readEncodingSurprise(segment(), headerOffset());
    }

    @Override
    public void encodingSurprise(float surprise) {
        checkPosition();
        headerLayout.writeEncodingSurprise(segment(), headerOffset(), surprise);
    }

    @Override
    public boolean isTombstoned() {
        return EncodingHeaderFields.isTombstoned(flags());
    }

    @Override
    public void tombstone() {
        checkPosition();
        headerLayout.markTombstoned(segment(), headerOffset());
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.resetRecord(tier, currentSlot);
        }
    }

    @Override
    public boolean isPinned() {
        return EncodingHeaderFields.isPinned(flags());
    }

    @Override
    public void pin() {
        checkPosition();
        headerLayout.markPinned(segment(), headerOffset());
    }

    @Override
    public boolean isResolved() {
        return EncodingHeaderFields.isResolved(flags());
    }

    @Override
    public void markResolved() {
        checkPosition();
        headerLayout.markResolved(segment(), headerOffset());
    }

    @Override
    public void markUnresolved() {
        checkPosition();
        headerLayout.markUnresolved(segment(), headerOffset());
    }

    @Override
    public boolean isConsolidated() {
        return EncodingHeaderFields.isConsolidated(flags());
    }

    @Override
    public void markConsolidated() {
        checkPosition();
        headerLayout.markConsolidated(segment(), headerOffset());
    }

    @Override
    public boolean isContradicted() {
        return EncodingHeaderFields.isContradicted(consolidationFlags());
    }

    @Override
    public void markContradicted() {
        checkPosition();
        headerLayout.markContradicted(segment(), headerOffset());
    }

    @Override
    public SourceModality sourceModality() {
        return SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags()));
    }

    @Override
    public void sourceModality(SourceModality modality) {
        byte updated = EncodingHeaderFields.withSourceModality(flags(), modality != null ? modality.ordinal() : 0);
        flags(updated);
    }

    @Override
    public long sessionId() {
        checkPosition();
        if (episodicHeaderLayout != null) {
            return episodicHeaderLayout.readSessionId(segment(), headerOffset());
        }
        return 0L;
    }

    @Override
    public void sessionId(long sessionId) {
        checkPosition();
        if (episodicHeaderLayout != null) {
            episodicHeaderLayout.writeSessionId(segment(), headerOffset(), sessionId);
        }
    }

    @Override
    public short modelId() {
        checkPosition();
        if (episodicHeaderLayout != null) {
            return episodicHeaderLayout.readModelId(segment(), headerOffset());
        }
        return 0;
    }

    @Override
    public void modelId(short modelId) {
        checkPosition();
        if (episodicHeaderLayout != null) {
            episodicHeaderLayout.writeModelId(segment(), headerOffset(), modelId);
        }
    }

    @Override
    public byte role() {
        checkPosition();
        if (episodicHeaderLayout != null) {
            return episodicHeaderLayout.readRole(segment(), headerOffset());
        }
        return 0;
    }

    @Override
    public void role(byte role) {
        checkPosition();
        if (episodicHeaderLayout != null) {
            episodicHeaderLayout.writeRole(segment(), headerOffset(), role);
        }
    }

    @Override
    public int payloadBytes() {
        checkPosition();
        if (episodicHeaderLayout != null) {
            return episodicHeaderLayout.readPayloadBytes(segment(), currentOffset);
        }
        return 0;
    }

    @Override
    public boolean isOptionBRecord() {
        checkPosition();
        if (episodicHeaderLayout != null) {
            return episodicHeaderLayout.isOptionBRecord(segment(), currentOffset);
        }
        return false;
    }

    @Override
    public long fallbackSessionId(int payloadBytes) {
        checkPosition();
        if (payloadBytes >= com.spectrayan.spector.kernel.store.codec.EpisodeCodec.PAYLOAD_METADATA_BYTES) {
            long payloadOffset = currentOffset + EpisodicLayout.FIXED_OVERHEAD_BYTES;
            return segment().get(ValueLayout.JAVA_LONG_UNALIGNED, payloadOffset + com.spectrayan.spector.kernel.store.codec.EpisodeCodec.OFFSET_SESSION_ID);
        }
        return 0L;
    }

    @Override
    public int activationCount() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readAgentRecallCount(tier, currentSlot);
        }
        return headerLayout.readAgentRecallCount(segment(), headerOffset());
    }

    @Override
    public void activationCount(int count) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeAgentRecallCount(tier, currentSlot, count);
        }
    }

    @Override
    public int addActivationCount(int delta) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.addAgentRecallCount(tier, currentSlot, delta);
        }
        return delta;
    }

    @Override
    public long lastAccessEpochMs() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readLastAutoLtp(tier, currentSlot);
        }
        return 0L;
    }

    @Override
    public void lastAccessEpochMs(long timestampMs) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeLastAutoLtp(tier, currentSlot, timestampMs);
        }
    }

    @Override
    public float storageStrength() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readStorageStrength(tier, currentSlot);
        }
        return headerLayout.readStorageStrength(segment(), headerOffset());
    }

    @Override
    public void storageStrength(float strength) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeStorageStrength(tier, currentSlot, strength);
        }
    }

    @Override
    public float updateStorageStrength(FloatUnaryOperator fn) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.casStorageStrength(tier, currentSlot, fn);
        }
        return 1.0f;
    }

    @Override
    public int spectorRecallCount() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readSpectorRecallCount(tier, currentSlot);
        }
        return 0;
    }

    @Override
    public void spectorRecallCount(int count) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.writeSpectorRecallCount(tier, currentSlot, count);
        }
    }

    @Override
    public void recordActRRecall(long creationMs, long recallMs) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            strengthMemory.recordRecall(tier, currentSlot, creationMs, recallMs, (byte) 0, 0);
        } else {
            com.spectrayan.spector.kernel.layout.StrengthLayout.INSTANCE.recordActRRecall(
                    segment(), headerOffset(), creationMs, recallMs);
        }
    }

    @Override
    public int[] readActRTimestamps() {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.readActRTimestamps(tier, currentSlot);
        }
        return com.spectrayan.spector.kernel.layout.StrengthLayout.INSTANCE.readActRTimestamps(segment(), headerOffset());
    }

    @Override
    public float computeActRActivation(long creationMs, long nowMs) {
        checkPosition();
        if (strengthMemory != null && currentSlot >= 0 && tier != MemoryType.WORKING) {
            return strengthMemory.computeActRActivation(tier, currentSlot, creationMs, nowMs);
        }
        return com.spectrayan.spector.kernel.layout.StrengthLayout.INSTANCE.computeActRActivation(
                segment(), headerOffset(), creationMs, nowMs);
    }

    @Override
    public EncodingHeader readHeader() {
        checkPosition();
        return headerLayout.readHeader(segment(), headerOffset());
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (engramLease != null) {
                engramLease.close();
            }
            if (strengthLease != null) {
                strengthLease.close();
            }
        }
    }
}
