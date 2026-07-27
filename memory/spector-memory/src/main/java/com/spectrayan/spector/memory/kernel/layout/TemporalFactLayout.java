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

import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Memory layout for 64-byte temporal fact records.
 *
 * <p>Each node is exactly 64 bytes:
 * - 4B factId (int)
 * - 4B subjectEntityId (int)
 * - 4B predicateId (int)
 * - 4B objectEntityId (int)
 * - 8B objectTextOffset (long)
 * - 2B objectTextLength (short)
 * - 1B flags (byte)
 * - 1B reserved (byte)
 * - 8B validFrom (long)
 * - 8B validTo (long)
 * - 8B txTime (long)
 * - 4B confidence (float)
 * - 4B retractsFactId (int)
 * - 4B crc32c (int)
 * </p>
 */
public final class TemporalFactLayout implements MemoryLayout {

    private static final int STRIDE = 64;
    private static final int LAYOUT_ID = 0x54464354; // 'TFCT'
    private static final int VERSION = 1;
    
    public static final int OFF_OBJECT_TEXT_OFFSET = 0;
    public static final int OFF_VALID_FROM = 8;
    public static final int OFF_VALID_TO = 16;
    public static final int OFF_TX_TIME = 24;

    public static final int OFF_FACT_ID = 32;
    public static final int OFF_SUBJECT_ENTITY_ID = 36;
    public static final int OFF_PREDICATE_ID = 40;
    public static final int OFF_OBJECT_ENTITY_ID = 44;
    public static final int OFF_CONFIDENCE = 48;
    public static final int OFF_RETRACTS_FACT_ID = 52;
    public static final int OFF_CRC32C = 56;

    public static final int OFF_OBJECT_TEXT_LENGTH = 60;
    public static final int OFF_FLAGS = 62;
    public static final int OFF_RESERVED = 63;
    
    public static final byte FLAG_INFERRED = 0x01;
    public static final byte FLAG_RESOLVED = 0x02;

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
        return STRIDE;
    }

    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "TemporalFact";
    }
}
