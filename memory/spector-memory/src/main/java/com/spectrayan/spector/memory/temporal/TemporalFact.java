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
package com.spectrayan.spector.memory.temporal;

import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Instant;

/**
 * A Java record representing a temporal fact in the Spector Memory Temporal Knowledge Graph.
 * Analogous to a biological synaptic engram, this structure encodes declarative memories
 * with temporal boundaries and confidence levels.
 */
public record TemporalFact(
        int factId,
        int subjectEntityId,
        int predicateId,
        int objectEntityId,
        long objectTextOffset,
        short objectTextLength,
        long validFrom,
        long validTo,
        long txTime,
        float confidence,
        int retractsFactId,
        byte flags
) {

    public static final int SENTINEL_ENTITY = -1;
    public static final int SENTINEL_FACT = -1;

    /**
     * Reads a TemporalFact from off-heap memory.
     *
     * @param segment the memory segment
     * @param baseOffset the base offset of the record
     * @param layout the temporal fact layout
     * @return a new TemporalFact instance
     */
    public static TemporalFact readFrom(MemorySegment segment, long baseOffset, TemporalFactLayout layout) {
        return new TemporalFact(
                segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_FACT_ID),
                segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_SUBJECT_ENTITY_ID),
                segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_PREDICATE_ID),
                segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_ENTITY_ID),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_TEXT_OFFSET),
                segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_TEXT_LENGTH),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_VALID_FROM),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_VALID_TO),
                segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_TX_TIME),
                segment.get(ValueLayout.JAVA_FLOAT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_CONFIDENCE),
                segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_RETRACTS_FACT_ID),
                segment.get(ValueLayout.JAVA_BYTE, baseOffset + TemporalFactLayout.OFF_FLAGS)
        );
    }

    /**
     * Writes this TemporalFact to off-heap memory.
     *
     * @param segment the memory segment
     * @param baseOffset the base offset of the record
     * @param layout the temporal fact layout
     */
    public void writeTo(MemorySegment segment, long baseOffset, TemporalFactLayout layout) {
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_FACT_ID, factId);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_SUBJECT_ENTITY_ID, subjectEntityId);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_PREDICATE_ID, predicateId);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_ENTITY_ID, objectEntityId);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_TEXT_OFFSET, objectTextOffset);
        segment.set(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_OBJECT_TEXT_LENGTH, objectTextLength);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_VALID_FROM, validFrom);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_VALID_TO, validTo);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + TemporalFactLayout.OFF_TX_TIME, txTime);
        segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_CONFIDENCE, confidence);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + TemporalFactLayout.OFF_RETRACTS_FACT_ID, retractsFactId);
        segment.set(ValueLayout.JAVA_BYTE, baseOffset + TemporalFactLayout.OFF_FLAGS, flags);
    }

    /**
     * Checks if this fact is a retraction of another fact.
     *
     * @return true if this is a retraction
     */
    public boolean isRetraction() {
        return retractsFactId != SENTINEL_FACT;
    }

    /**
     * Checks if this fact was inferred rather than explicitly asserted.
     *
     * @return true if this fact was inferred
     */
    public boolean isInferred() {
        return (flags & TemporalFactLayout.FLAG_INFERRED) != 0;
    }

    /**
     * Checks if this fact has been resolved.
     *
     * @return true if resolved
     */
    public boolean isResolved() {
        return (flags & TemporalFactLayout.FLAG_RESOLVED) != 0;
    }

    /**
     * Checks if this fact is ongoing (has no end time).
     *
     * @return true if ongoing
     */
    public boolean isOngoing() {
        return validTo == Long.MAX_VALUE;
    }

    /**
     * Checks if this fact was valid at the given instant.
     *
     * @param instant the instant to check
     * @return true if valid at the instant
     */
    public boolean validAtInstant(Instant instant) {
        long epochMilli = instant.toEpochMilli();
        return validFrom <= epochMilli && epochMilli < validTo;
    }

    /**
     * Checks if this fact is valid during the given interval.
     *
     * @param fromMs interval start (inclusive)
     * @param toMs interval end (exclusive)
     * @return true if there is an overlap
     */
    public boolean validDuring(long fromMs, long toMs) {
        return validFrom < toMs && validTo > fromMs;
    }
}
