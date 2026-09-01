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
package com.spectrayan.spector.memory.synapse.scan;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.synapse.DecayStrategy;

import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.isPinned;
import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.isResolved;

/**
 * High-speed candidate screening and gating predicates for off-heap segment scan (Phases 1-4).
 *
 * <p>All methods are static final and designed for aggressive JIT inlining without allocations.</p>
 */
public final class RecordGates {

    private RecordGates() {
        // utility class
    }

    /**
     * Phase 1 & 1c: Evaluates tombstone and contradiction flags.
     *
     * @param flags                synaptic header flags byte
     * @param consolidationFlags   consolidation flags byte
     * @param includeContradictions whether contradicted records should be admitted
     * @return true if the record is rejected
     */
    public static boolean isDeletedOrContradicted(
            final byte flags, final byte consolidationFlags, final boolean includeContradictions) {
        if (SynapticHeaderConstants.isTombstoned(flags)) {
            return true;
        }
        return !includeContradictions && SynapticHeaderConstants.isContradicted(consolidationFlags);
    }

    /**
     * Phase 1b: Evaluates temporal bounds and the 1-cycle future causal horizon gate.
     *
     * @param timestampMs  memory record timestamp in epoch milliseconds
     * @param minTimestamp optional minimum timestamp filter
     * @param maxTimestamp optional maximum timestamp filter
     * @param queryTimeMs  query reference timestamp in epoch milliseconds
     * @param allowFuture  true to allow future/predictive memory recall (DMN mode)
     * @return true if the record is rejected
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
        return !allowFuture && queryTimeMs > 0 && timestampMs > queryTimeMs;
    }

    /**
     * Phase 2: Evaluates synaptic tag masks for standard containment or hyperfocus tunnels.
     *
     * @param recordTags     memory record 64-bit synaptic tag mask
     * @param queryTagMask   query tag mask
     * @param hyperfocusMask hyperfocus strict tag mask (0 if inactive)
     * @return true if the record is rejected
     */
    public static boolean isTagGated(
            final long recordTags, final long queryTagMask, final long hyperfocusMask) {
        if (hyperfocusMask != 0) {
            return (recordTags & hyperfocusMask) != hyperfocusMask;
        }
        if (queryTagMask != 0) {
            return (recordTags & queryTagMask) == 0;
        }
        return false;
    }

    /**
     * Phase 3: Evaluates emotional valence bounds.
     *
     * @param valence    memory record signed 8-bit valence [-128, 127]
     * @param minValence minimum allowable valence
     * @param maxValence maximum allowable valence
     * @return true if the record is rejected
     */
    public static boolean isValenceGated(
            final byte valence, final byte minValence, final byte maxValence) {
        return valence < minValence || valence > maxValence;
    }

    /** Minimum cognitive mass required to exempt low-importance memories (I < 1.0) from stale pruning. */
    public static final float FLASHBULB_MASS_FLOOR = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_RECALL_FLASHBULB_MASS_FLOOR;

    /**
     * Phase 4: Evaluates temporal age decay against the maximum age threshold with high-mass and Zeigarnik exemptions.
     *
     * @param adjustedBucket adjusted decay age bucket [0..11]
     * @param importance     raw importance score [0.0..10.0]
     * @param flags          synaptic header flags byte
     * @param cognitiveMass  computed cognitive mass M_i
     * @return true if the record is stale and low-importance, and should be skipped
     */
    public static boolean isStaleAndWeak(
            final int adjustedBucket, final float importance, final byte flags, final float cognitiveMass) {
        return adjustedBucket >= DecayStrategy.MAX_BUCKET
                && importance < 1.0f
                && cognitiveMass < FLASHBULB_MASS_FLOOR
                && !isPinned(flags)
                && isResolved(flags);
    }
}
