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
package com.spectrayan.spector.kernel.scan;

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.HeaderBits;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.score.CognitiveMass;
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.kernel.score.RecordGates;
import com.spectrayan.spector.kernel.store.StrengthMemory;

import java.lang.foreign.MemorySegment;

/**
 * High-performance fused filter-then-scan kernel (R7.1b, R7.1c, R7.6).
 *
 * <p>Evaluates Phases 1-4 directly against the 64-byte header and authoritative
 * strength region; non-surviving slots SKIP Phase 5 SIMD L2 distance entirely and
 * never invoke the visitor callback.</p>
 */
public final class SlabScanner {

    private SlabScanner() {}

    public static void scan(
            final MemorySegment segment,
            final int recordCount,
            final FixedEngramLayout layout,
            final float[] queryVector,
            final float[] mins,
            final float[] scales,
            final ScanFilter filter,
            final StrengthMemory strengthMemory,
            final MemoryType tier,
            final long baseOffset,
            final int partitionSeq,
            final SlotVisitor visitor) {

        final int stride = layout.stride();
        final boolean hasArousal = layout.headerLayout().version() >= 2;
        final boolean hasStorageStrength = hasArousal;
        final boolean useStrength = strengthMemory != null && tier != null && tier != MemoryType.WORKING;
        final long nowMs = filter.nowMs() > 0 ? filter.nowMs() : System.currentTimeMillis();

        final int dims = queryVector.length;
        final float[] effectiveMins = mins != null ? mins : IdentityCalibration.mins(dims);
        final float[] effectiveScales = scales != null ? scales : IdentityCalibration.scales(dims);
        final byte tierOrdinal = (byte) (tier != null ? tier.ordinal() : 0);

        for (int i = 0; i < recordCount; i++) {
            final long offset = baseOffset + (long) i * stride;
            if (offset + stride > segment.byteSize()) {
                break;
            }

            // Phase 1: Tombstone check (~1 cycle)
            final byte flags = layout.readFlags(segment, offset);
            if (EncodingHeaderFields.isTombstoned(flags)) {
                continue;
            }
            if (filter.requiredFlags() != 0 && (flags & filter.requiredFlags()) != filter.requiredFlags()) {
                continue;
            }
            if (filter.excludedFlags() != 0 && (flags & filter.excludedFlags()) != 0) {
                continue;
            }

            // Phase 1c: Contradiction & simulation gating
            if (!filter.includeContradictions() || !filter.allowSimulated()) {
                final byte cFlags = layout.readConsolidationFlags(segment, offset);
                if (!filter.includeContradictions() && EncodingHeaderFields.isContradicted(cFlags)) {
                    continue;
                }
                if (!filter.allowSimulated()) {
                    if (layout.readSourceCode(segment, offset) == EncodingHeaderFields.SOURCE_SIMULATED) {
                        continue;
                    }
                    if (EncodingHeaderFields.isSimulated(cFlags)) {
                        continue;
                    }
                }
            }

            // Phase 1b: Temporal gating & Future causal horizon gate
            final long timestamp = layout.readTimestamp(segment, offset);
            if (RecordGates.isTemporalGated(timestamp, filter.minTimestampMs(), filter.maxTimestampMs(), nowMs, filter.allowFuture())) {
                continue;
            }

            // Phase 2: Synaptic tag gating (128-bit Bloom filter)
            final long recordTagsLo = layout.readSynapticTags(segment, offset);
            final long recordTagsHi = layout.headerLayout().readSynapticTagsHi(segment, offset);
            if (RecordGates.isTagGated128(recordTagsLo, recordTagsHi,
                    filter.tagMaskLo(), filter.tagMaskHi(),
                    filter.hyperfocusMaskLo(), filter.hyperfocusMaskHi())) {
                continue;
            }

            // Phase 3: Valence filter
            final byte valence = layout.readValence(segment, offset);
            if (RecordGates.isValenceGated(valence, filter.minValence(), filter.maxValence())) {
                continue;
            }

            // Phase 4: Stale and weak pre-screen with reconsolidation & high-mass exemption
            final float rawImportance = layout.readImportance(segment, offset);
            final float importance;
            if (useStrength) {
                float eff = strengthMemory.readEffectiveImportance(tier, i);
                importance = eff != 0.0f ? eff : rawImportance;
            } else {
                importance = rawImportance;
            }
            if (importance < filter.minImportance()) {
                continue;
            }

            final int agentRecallCount = useStrength
                    ? strengthMemory.readAgentRecallCount(tier, i)
                    : layout.readAgentRecallCount(segment, offset);
            final int rawBucket = DecayStrategy.ageToBucket(timestamp, nowMs);
            int adjustedBucket = DecayStrategy.adjustForReconsolidation(rawBucket, agentRecallCount);

            if (hasStorageStrength || useStrength) {
                final int spectorRecallCount = useStrength
                        ? strengthMemory.readSpectorRecallCount(tier, i)
                        : layout.readSpectorRecallCount(segment, offset);
                adjustedBucket = DecayStrategy.adjustForAutoRecall(adjustedBucket, spectorRecallCount);
            }

            final byte arousal = hasArousal ? layout.readArousal(segment, offset) : (byte) 0;
            final float storageStrength;
            if (useStrength) {
                float s = strengthMemory.readStorageStrength(tier, i);
                storageStrength = s > 0.0f ? s : 1.0f;
            } else if (hasStorageStrength) {
                storageStrength = layout.readStorageStrength(segment, offset);
            } else {
                storageStrength = 1.0f;
            }
            final float cognitiveMass = CognitiveMass.computeCognitiveMass(importance, arousal, storageStrength);

            if (RecordGates.isStaleAndWeak(adjustedBucket, importance, flags, cognitiveMass,
                    filter.staleBucketThreshold(), filter.weakMassThreshold())) {
                continue;
            }

            // Phase 5: Calibrated SIMD L2 distance
            // Failing records above skipped this computation entirely!
            final float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    queryVector, segment, layout.vectorOffset(offset),
                    effectiveMins, effectiveScales, layout.quantizedVecBytes());

            // Pack HeaderBits and dispatch to visitor
            final long headerBits = HeaderBits.pack(
                    flags, valence, arousal,
                    (short) agentRecallCount,
                    importance, storageStrength,
                    tierOrdinal);

            visitor.accept(i, partitionSeq, offset, headerBits, l2dist, timestamp, recordTagsLo);
        }
    }
}
