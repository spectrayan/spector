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

import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderLayout;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout.StrengthState;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.compat.LegacyEncodingHeaderReader;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HeaderMigratorTest")
class HeaderMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("copyV1HeaderToStrength copies offsets 4, 16, 36, 40, 48, 60 into StrengthLayout")
    void testCopyV1HeaderToStrength() {
        try (Arena arena = Arena.ofConfined()) {
            int stride = 64;
            MemorySegment engramSeg = arena.allocate(stride);
            MemorySegment strengthSeg = arena.allocate(StrengthLayout.STRIDE_BYTES);

            // Write V1 header offsets
            float expectedImportance = 0.85f;
            int expectedAgentRecallCount = 7;
            float expectedStorageStrength = 3.25f;
            int expectedSpectorRecallCount = 12;
            long expectedLastAutoLtp = 1716900005000L;
            byte expectedLastRecallProfile = (byte) 3;

            engramSeg.set(ValueLayout.JAVA_FLOAT, EncodingHeaderFields.OFFSET_IMPORTANCE, expectedImportance);
            engramSeg.set(ValueLayout.JAVA_INT, EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT, expectedAgentRecallCount);
            engramSeg.set(ValueLayout.JAVA_FLOAT, EncodingHeaderFields.OFFSET_STORAGE_STRENGTH, expectedStorageStrength);
            engramSeg.set(ValueLayout.JAVA_INT, EncodingHeaderFields.OFFSET_SPECTOR_RECALL_COUNT, expectedSpectorRecallCount);
            engramSeg.set(ValueLayout.JAVA_LONG, EncodingHeaderFields.OFFSET_LAST_AUTO_LTP, expectedLastAutoLtp);
            engramSeg.set(ValueLayout.JAVA_BYTE, EncodingHeaderFields.OFFSET_LAST_RECALL_PROFILE, expectedLastRecallProfile);

            HeaderMigrator.copyV1HeaderToStrength(engramSeg, 0, strengthSeg, 0, MemoryType.SEMANTIC);

            StrengthLayout layout = StrengthLayout.INSTANCE;
            StrengthState state = layout.readRecord(strengthSeg, 0);

            assertThat(state.memoryType()).isEqualTo(MemoryType.SEMANTIC);
            assertThat(state.effectiveImportance()).isEqualTo(expectedImportance);
            assertThat(state.agentRecallCount()).isEqualTo(expectedAgentRecallCount);
            assertThat(state.storageStrength()).isEqualTo(expectedStorageStrength);
            assertThat(state.spectorRecallCount()).isEqualTo(expectedSpectorRecallCount);
            assertThat(state.lastAutoLtp()).isEqualTo(expectedLastAutoLtp);
            assertThat(state.lastRecallProfile()).isEqualTo(expectedLastRecallProfile);
        }
    }

    @Test
    @DisplayName("migrateRecordsToStrength migrates full record array to StrengthMemory")
    void testMigrateRecordsToStrength() {
        try (Arena arena = Arena.ofConfined()) {
            int recordCount = 5;
            int stride = 64 + 16;
            long dataOffset = RegionPreamble.PREAMBLE_BYTES;
            MemorySegment engramSeg = arena.allocate(dataOffset + (long) recordCount * stride);

            for (int i = 0; i < recordCount; i++) {
                long offset = dataOffset + (long) i * stride;
                engramSeg.set(ValueLayout.JAVA_FLOAT, offset + EncodingHeaderFields.OFFSET_IMPORTANCE, 0.1f * (i + 1));
                engramSeg.set(ValueLayout.JAVA_INT, offset + EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT, i);
                engramSeg.set(ValueLayout.JAVA_FLOAT, offset + EncodingHeaderFields.OFFSET_STORAGE_STRENGTH, 1.0f + i * 0.5f);
                engramSeg.set(ValueLayout.JAVA_INT, offset + EncodingHeaderFields.OFFSET_SPECTOR_RECALL_COUNT, i * 2);
                engramSeg.set(ValueLayout.JAVA_LONG, offset + EncodingHeaderFields.OFFSET_LAST_AUTO_LTP, 1000L * i);
            }

            StrengthMemory store = StrengthMemory.heap(10, 10, 10);
            int migrated = HeaderMigrator.migrateRecordsToStrength(
                    engramSeg, dataOffset, stride, recordCount, store, MemoryType.SEMANTIC);

            assertThat(migrated).isEqualTo(recordCount);

            for (int i = 0; i < recordCount; i++) {
                StrengthState state = store.readStrengthState(MemoryType.SEMANTIC, i);
                assertThat(state.effectiveImportance()).isEqualTo(0.1f * (i + 1));
                assertThat(state.agentRecallCount()).isEqualTo(i);
                assertThat(state.storageStrength()).isEqualTo(1.0f + i * 0.5f);
                assertThat(state.spectorRecallCount()).isEqualTo(i * 2);
                assertThat(state.lastAutoLtp()).isEqualTo(1000L * i);
            }
        }
    }

    @Test
    @DisplayName("migrate converts file from V1 to V2 header and populates strength store")
    void testMigrateFileWithStrength() throws IOException {
        Path storePath = tempDir.resolve("semantic.mem");
        int vectorBytes = 16;
        int capacity = 10;
        int recordCount = 2;
        int v1Stride = 64 + vectorBytes;
        long totalSize = RegionPreamble.PREAMBLE_BYTES + (long) capacity * v1Stride;

        try (FileChannel fc = FileChannel.open(storePath,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            fc.write(ByteBuffer.wrap(new byte[]{0}), totalSize - 1);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = fc.map(FileChannel.MapMode.READ_WRITE, 0, totalSize, arena);
                RegionPreamble.write(seg, 0, 1, MemoryShape.RECORD, 1, capacity, recordCount,
                        v1Stride, EngramLayout.LAYOUT_ID, 1000L, 2000L);

                for (int i = 0; i < recordCount; i++) {
                    long recOff = RegionPreamble.PREAMBLE_BYTES + (long) i * v1Stride;
                    seg.set(ValueLayout.JAVA_FLOAT, recOff + EncodingHeaderFields.OFFSET_IMPORTANCE, 0.75f);
                    seg.set(ValueLayout.JAVA_INT, recOff + EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT, 3);
                    seg.set(ValueLayout.JAVA_FLOAT, recOff + EncodingHeaderFields.OFFSET_STORAGE_STRENGTH, 2.5f);
                    seg.set(ValueLayout.JAVA_INT, recOff + EncodingHeaderFields.OFFSET_SPECTOR_RECALL_COUNT, 4);
                }
            }
        }

        StrengthMemory store = StrengthMemory.heap(10, 10, 10);
        HeaderMigrator.MigrationReport report = HeaderMigrator.migrate(
                storePath, LegacyEncodingHeaderReader.INSTANCE, EncodingHeaderLayout.INSTANCE,
                vectorBytes, false, store, MemoryType.SEMANTIC);

        assertThat(report.recordsMigrated()).isEqualTo(recordCount);
        assertThat(Files.exists(report.backupPath())).isTrue();

        for (int i = 0; i < recordCount; i++) {
            StrengthState state = store.readStrengthState(MemoryType.SEMANTIC, i);
            assertThat(state.effectiveImportance()).isEqualTo(0.75f);
            assertThat(state.agentRecallCount()).isEqualTo(3);
            assertThat(state.storageStrength()).isEqualTo(2.5f);
            assertThat(state.spectorRecallCount()).isEqualTo(4);
        }
    }
}
