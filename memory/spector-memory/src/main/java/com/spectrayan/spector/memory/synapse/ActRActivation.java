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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Full ACT-R base-level activation using an 8-slot recall-timestamp ring buffer (ADR-0028).
 *
 * <h3>ACT-R Base-Level Activation</h3>
 * <p>Anderson's (1993) ACT-R architecture computes base-level activation as:</p>
 * <pre>
 *   B_i = ln(Σ_{j=1}^{n} t_j^{-d})
 * </pre>
 * <p>Where t_j are the times since each past recall. This captures both
 * <b>recency</b> and <b>frequency</b>, and models the <b>spacing effect</b>:
 * recalls spaced over time produce stronger activation than massed practice.</p>
 *
 * <h3>Storage: 8-Slot Ring Buffer in Audit Region</h3>
 * <p>Under ADR-0028 dual-region architecture, 32 bytes in {@link StrengthLayout}
 * are dedicated to an 8-slot circular ring buffer of relative-second timestamps.</p>
 *
 * <h3>Performance</h3>
 * <p>Zero {@code Math.pow}, zero {@code Math.log}, zero {@code Math.exp} at query time.
 * Each recall timestamp is mapped to a decay bucket via
 * {@link DecayStrategy#ageToBucket} → array lookup (~7 cycles per slot).
 * The sigmoid normalization uses the algebraic identity
 * {@code σ(ln(x)) = x / (x + 1)} — a single float division.
 * Total: ~35 CPU cycles for 8 recall slots.</p>
 *
 * @see DecayStrategy
 * @see DecayConfig
 * @see StrengthLayout
 * @see com.spectrayan.spector.memory.kernel.layout.EncodingHeaderLayout
 */
public final class ActRActivation {

    private ActRActivation() {}

    /** Number of recall timestamp slots in the ring buffer (8 slots). */
    public static final int RING_BUFFER_SLOTS = StrengthLayout.ACT_R_RING_BUFFER_SLOTS;

    /**
     * Records a recall timestamp into the audit record's 8-slot ring buffer.
     *
     * @param auditSeg          off-heap audit memory segment
     * @param auditRecordOffset byte offset where this audit record starts
     * @param creationMs        memory creation timestamp (epoch millis)
     * @param recallMs          current recall timestamp (epoch millis)
     */
    public static void recordRecall(MemorySegment auditSeg, long auditRecordOffset,
                                    long creationMs, long recallMs) {
        StrengthLayout.INSTANCE.recordActRRecall(auditSeg, auditRecordOffset, creationMs, recallMs);
    }

    /**
     * Reads all 8 recall timestamps from the audit record's ring buffer.
     *
     * @param auditSeg          off-heap audit memory segment
     * @param auditRecordOffset byte offset where this audit record starts
     * @return array of 8 relative-second values (0 = empty slot)
     */
    public static int[] readRecallTimestamps(MemorySegment auditSeg, long auditRecordOffset) {
        return StrengthLayout.INSTANCE.readActRTimestamps(auditSeg, auditRecordOffset);
    }

    /**
     * Computes the full ACT-R base-level activation using bucket lookups.
     *
     * <h4>The Math</h4>
     * <pre>
     *   B_i = ln(Σ_{j=1}^{n} t_j^{-d})
     *   σ(B_i) = 1 / (1 + e^{-ln(sum)}) = sum / (sum + 1)   ← algebraic identity!
     * </pre>
     *
     * @param auditSeg          off-heap audit memory segment
     * @param auditRecordOffset byte offset where this audit record starts
     * @param creationMs        memory creation timestamp (epoch millis)
     * @param nowMs             current time (epoch millis)
     * @param decayExponent     unused (kept for API compatibility)
     * @return normalized base-level activation in [0, 1], or -1 if no recall data
     */
    public static float computeBaseLevelActivation(MemorySegment auditSeg, long auditRecordOffset,
                                                    long creationMs, long nowMs,
                                                    float decayExponent) {
        if (auditSeg == null) return -1.0f;
        return StrengthLayout.INSTANCE.computeActRActivation(auditSeg, auditRecordOffset, creationMs, nowMs);
    }

    /**
     * Computes the decay multiplier using the full ACT-R model when recall
     * timestamps are available, otherwise falls back to bucket-based decay.
     *
     * @param auditSeg          off-heap audit memory segment (or null for fallback)
     * @param auditRecordOffset byte offset where this audit record starts
     * @param creationMs        memory creation timestamp (epoch millis)
     * @param nowMs             current time (epoch millis)
     * @param agentRecallCount  simplified recall count (for fallback)
     * @param decayExponent     power-law decay exponent
     * @return decay multiplier in [0, 1]
     */
    public static float computeDecayWithActR(MemorySegment auditSeg, long auditRecordOffset,
                                              long creationMs, long nowMs,
                                              int agentRecallCount, float decayExponent) {
        if (auditSeg != null) {
            float actr = computeBaseLevelActivation(auditSeg, auditRecordOffset, creationMs, nowMs, decayExponent);
            if (actr >= 0) {
                return actr; // Full ACT-R result
            }
        }
        // Fallback: simplified bucket-based decay
        return DecayStrategy.computeDecay(creationMs, nowMs, agentRecallCount);
    }
}
