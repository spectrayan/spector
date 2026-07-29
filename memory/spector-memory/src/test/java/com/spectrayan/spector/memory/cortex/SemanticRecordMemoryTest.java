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

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticRecordMemoryTest {

    private CognitiveHeader createHeader() {
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        return new CognitiveHeader(12345L, 0L, 1.0f, 0.5f, 0, (short)0, (byte)0, flags, (byte)0, 1.0f);
    }

    @Test
    @DisplayName("append increments size and visibleCount")
    void appendIncrementsSizeAndVisibleCount() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            assertThat(store.size()).isZero();
            store.append(createHeader(), new byte[128]);
            assertThat(store.size()).isEqualTo(1);
            assertThat(store.visibleCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("append with null vector succeeds")
    void appendWithNullVectorSucceeds() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            store.append(createHeader(), null);
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("append at capacity throws tier full exception")
    void appendAtCapacityThrowsTierFull() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 1)) {
            store.append(createHeader(), new byte[128]);
            assertThatThrownBy(() -> store.append(createHeader(), new byte[128]))
                .isInstanceOf(SpectorMemoryTierFullException.class);
        }
    }

    @Test
    @DisplayName("readHeader returns written data")
    void readHeaderReturnsWrittenData() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            store.write(createHeader(), new byte[128]);
            CognitiveHeader readHeader = store.readHeader(0);
            assertThat(readHeader.timestampMs()).isEqualTo(12345L);
        }
    }

    @Test
    @DisplayName("write returns correct byte offset")
    void writeReturnsCorrectByteOffset() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            long offset1 = store.write(createHeader(), new byte[128]);
            long offset2 = store.write(createHeader(), new byte[128]);
            assertThat(offset1).isNotEqualTo(offset2);
            assertThat(offset2).isGreaterThan(offset1);
        }
    }

    @Test
    @DisplayName("type returns SEMANTIC")
    void typeReturnsSemantic() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            assertThat(store.type()).isEqualTo(MemoryType.SEMANTIC);
        }
    }

    @Test
    @DisplayName("store header only returns index")
    void storeHeaderOnlyReturnsIndex() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            int index = store.store(createHeader());
            assertThat(index).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("headerSlab is same as primary segment")
    void headerSlabIsSameAsPrimarySegment() {
        try (SemanticRecordMemory store = new SemanticRecordMemory(128, 100)) {
            assertThat(store.headerSlab()).isNotNull();
        }
    }
}
