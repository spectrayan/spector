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
package com.spectrayan.spector.kernel.score;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isPinned;
import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isResolved;

/**
 * High-speed candidate screening and gating predicates for off-heap segment scan (Phases 1-4) (R17.1).
 *
 * <p>All methods are static final and designed for aggressive JIT inlining without allocations.</p>
 */
public final class RecordGates {

    public static final float FLASHBULB_MASS_FLOOR = 0.30f;
    public static final int DEFAULT_STALE_BUCKET_THRESHOLD = 6;
    public static final float DEFAULT_WEAK_MASS_THRESHOLD = 0.15f;

    private RecordGates() {}

    /**
     * Phase 1 & 1c: Evaluates tombstone and contradiction flags.
     */
    public static boolean isDeletedOrContradicted(
            final byte flags, final byte consolidationFlags, final boolean includeContradictions) {
        if (EncodingHeaderFields.isTombstoned(flags)) {
            return true;
        }
        return !includeContradictions && EncodingHeaderFields.isContradicted(consolidationFlags);
    }

    /**
     * Phase 1b: Evaluates temporal bounds and the 1-cycle future causal horizon gate.
     */
    public static boolean isTemporalGated(
            final long timestampMs, final Long minTimestamp, final Long maxTimestamp,
            final long queryTimeMs, final boolean allowFuture) {
        if (minTimestamp != null && timestampMs < minTimestamp) {
            return true;
        }
        if (maxTimestamp != null && timestampMs > maxTimestamp) {
            return true;
        }
        return !allowFuture && (timestampMs > queryTimeMs);
    }

    /**
     * Phase 2: Evaluates 64-bit synaptic tag mask matching.
     */
    public static boolean isTagGated(
            final long recordTags, final long queryTagMask, final long hyperfocusMask) {
        if (hyperfocusMask != 0L && (recordTags & hyperfocusMask) != hyperfocusMask) {
            return true;
        }
        if (queryTagMask != 0L && (recordTags & queryTagMask) == 0L) {
            return true;
        }
        return false;
    }

    /**
     * Phase 2: Evaluates 128-bit synaptic tag mask matching.
     */
    public static boolean isTagGated128(
            final long recordTagsLo, final long recordTagsHi,
            final long queryTagMaskLo, final long queryTagMaskHi,
            final long hyperfocusMaskLo, final long hyperfocusMaskHi) {
        if ((hyperfocusMaskLo != 0L || hyperfocusMaskHi != 0L)) {
            if ((recordTagsLo & hyperfocusMaskLo) != hyperfocusMaskLo
                    || (recordTagsHi & hyperfocusMaskHi) != hyperfocusMaskHi) {
                return true;
            }
        }
        if (queryTagMaskLo != 0L || queryTagMaskHi != 0L) {
            if ((recordTagsLo & queryTagMaskLo) == 0L && (recordTagsHi & queryTagMaskHi) == 0L) {
                return true;
            }
        }
        return false;
    }

    /**
     * Phase 3: Evaluates emotional valence bounds.
     */
    public static boolean isValenceGated(
            final byte recordValence, final byte minValence, final byte maxValence) {
        return recordValence < minValence || recordValence > maxValence;
    }

    /**
     * Phase 4: Evaluates stale and weak candidates using default thresholds.
     */
    public static boolean isStaleAndWeak(
            final int adjustedBucket, final float importance, final byte flags, final float cognitiveMass) {
        return isStaleAndWeak(adjustedBucket, importance, flags, cognitiveMass, DEFAULT_STALE_BUCKET_THRESHOLD, DEFAULT_WEAK_MASS_THRESHOLD);
    }

    /**
     * Phase 4: Evaluates stale and weak candidates using configurable thresholds.
     */
    public static boolean isStaleAndWeak(
            final int adjustedBucket, final float importance, final byte flags, final float cognitiveMass,
            final int staleBucketThreshold, final float weakMassThreshold) {
        if (cognitiveMass >= FLASHBULB_MASS_FLOOR) {
            return false;
        }
        if (isPinned(flags)) {
            return false;
        }
        if (!isResolved(flags)) {
            return false;
        }
        return adjustedBucket >= staleBucketThreshold && cognitiveMass < weakMassThreshold;
    }
}
