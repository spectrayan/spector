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

class ProceduralMemoryStoreTest {

    private CognitiveHeader createHeader() {
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.PROCEDURAL.ordinal());
        return new CognitiveHeader(12345L, 0L, 1.0f, 0.5f, 0, (short)0, (byte)0, flags, (byte)0, 1.0f);
    }

    @Test
    @DisplayName("append increments size and visibleCount")
    void appendIncrementsSizeAndVisibleCount() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128, 100)) {
            assertThat(store.size()).isZero();
            store.append(createHeader(), new byte[128]);
            assertThat(store.size()).isEqualTo(1);
            assertThat(store.visibleCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("append at capacity throws tier full exception")
    void appendAtCapacityThrowsTierFull() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128, 1)) {
            store.append(createHeader(), new byte[128]);
            assertThatThrownBy(() -> store.append(createHeader(), new byte[128]))
                .isInstanceOf(SpectorMemoryTierFullException.class);
        }
    }

    @Test
    @DisplayName("write returns correct byte offset")
    void writeReturnsCorrectByteOffset() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128, 100)) {
            long offset1 = store.write(createHeader(), new byte[128]);
            long offset2 = store.write(createHeader(), new byte[128]);
            assertThat(offset1).isNotEqualTo(offset2);
            assertThat(offset2).isGreaterThan(offset1);
        }
    }

    @Test
    @DisplayName("type returns PROCEDURAL")
    void typeReturnsProcedural() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128, 100)) {
            assertThat(store.type()).isEqualTo(MemoryType.PROCEDURAL);
        }
    }

    @Test
    @DisplayName("default capacity is 1000")
    void defaultCapacityIs1000() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128)) {
            assertThat(store.capacity()).isEqualTo(1000);
        }
    }

    @Test
    @DisplayName("header and vector round trip")
    void headerAndVectorRoundTrip() {
        try (ProceduralMemoryStore store = new ProceduralMemoryStore(128, 100)) {
            store.write(createHeader(), new byte[128]);
            assertThat(store.size()).isEqualTo(1);
        }
    }
}
