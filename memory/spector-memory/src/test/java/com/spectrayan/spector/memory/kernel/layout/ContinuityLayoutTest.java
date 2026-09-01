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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

class ContinuityLayoutTest {

    @Test
    void layoutConstantsConformToKernelStandard() {
        ContinuityLayout layout = ContinuityLayout.SINGLETON;
        assertThat(layout.layoutId()).isEqualTo(0x434F4E54); // 'CONT'
        assertThat(layout.schemaVersion()).isEqualTo(1);
        assertThat(layout.recordStride()).isEqualTo(32);
        assertThat(layout.crcEnabled()).isFalse();
        assertThat(layout.name()).isEqualTo("ContinuityLayout");
        assertThat(ContinuityLayout.DATA_START).isEqualTo(96L);
    }

    @Test
    void subHeaderReadWriteRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);

            ContinuityLayout.writeHeadIndex(seg, 42);
            ContinuityLayout.writeTotalSnapshots(seg, 105);
            ContinuityLayout.writeLastSnapshotTimestamp(seg, 1724350000000L);
            ContinuityLayout.writeCapacity(seg, 500);

            assertThat(ContinuityLayout.readHeadIndex(seg)).isEqualTo(42);
            assertThat(ContinuityLayout.readTotalSnapshots(seg)).isEqualTo(105);
            assertThat(ContinuityLayout.readLastSnapshotTimestamp(seg)).isEqualTo(1724350000000L);
            assertThat(ContinuityLayout.readCapacity(seg)).isEqualTo(500);
        }
    }

    @Test
    void recordReadWriteRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(1024);
            long off = ContinuityLayout.recordOffset(2);

            long ts = 1724351111111L;
            float phi = 0.89f;
            float trace = 12.5f;
            float drift = 0.045f;
            byte val = 12;
            byte ar = 45;
            byte en = 88;
            short soulVer = 3;

            ContinuityLayout.writeRecord(seg, off, ts, phi, trace, drift, val, ar, en, soulVer);

            assertThat(ContinuityLayout.readTimestamp(seg, off)).isEqualTo(ts);
            assertThat(ContinuityLayout.readPhiCc(seg, off)).isEqualTo(phi);
            assertThat(ContinuityLayout.readTraceG(seg, off)).isEqualTo(trace);
            assertThat(ContinuityLayout.readPriorDrift(seg, off)).isEqualTo(drift);
            assertThat(ContinuityLayout.readValence(seg, off)).isEqualTo(val);
            assertThat(ContinuityLayout.readArousal(seg, off)).isEqualTo(ar);
            assertThat(ContinuityLayout.readEnergy(seg, off)).isEqualTo(en);
            assertThat(ContinuityLayout.readSoulVersion(seg, off)).isEqualTo(soulVer);
        }
    }
}
