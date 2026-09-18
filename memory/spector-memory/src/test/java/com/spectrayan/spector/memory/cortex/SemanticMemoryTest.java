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
package com.spectrayan.spector.memory.cortex;
import com.spectrayan.spector.kernel.store.SemanticMemory;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.error.SpectorMemoryTierFullException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticMemoryTest {

    private EncodingHeader createHeader() {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        return new EncodingHeader(12345L, 0L, 1.0f, 0.5f, 0, (short)0, (byte)0, flags, (byte)0, 1.0f);
    }

    @Test
    @DisplayName("append increments size and visibleCount")
    void appendIncrementsSizeAndVisibleCount() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            assertThat(store.size()).isZero();
            store.append(createHeader(), new byte[128]);
            assertThat(store.size()).isEqualTo(1);
            assertThat(store.visibleCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("append with null vector succeeds")
    void appendWithNullVectorSucceeds() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            store.append(createHeader(), null);
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("append at capacity throws tier full exception")
    void appendAtCapacityThrowsTierFull() {
        try (SemanticMemory store = new SemanticMemory(128, 1)) {
            store.append(createHeader(), new byte[128]);
            assertThatThrownBy(() -> store.append(createHeader(), new byte[128]))
                .isInstanceOf(SpectorMemoryTierFullException.class);
        }
    }

    @Test
    @DisplayName("readHeader returns written data")
    void readHeaderReturnsWrittenData() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            store.write(createHeader(), new byte[128]);
            EncodingHeader readHeader = store.readHeader(0);
            assertThat(readHeader.timestampMs()).isEqualTo(12345L);
        }
    }

    @Test
    @DisplayName("write returns correct byte offset")
    void writeReturnsCorrectByteOffset() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            long offset1 = store.write(createHeader(), new byte[128]);
            long offset2 = store.write(createHeader(), new byte[128]);
            assertThat(offset1).isNotEqualTo(offset2);
            assertThat(offset2).isGreaterThan(offset1);
        }
    }

    @Test
    @DisplayName("type returns SEMANTIC")
    void typeReturnsSemantic() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            assertThat(store.type()).isEqualTo(MemoryType.SEMANTIC);
        }
    }

    @Test
    @DisplayName("store header only returns index")
    void storeHeaderOnlyReturnsIndex() {
        try (SemanticMemory store = new SemanticMemory(128, 100)) {
            int index = store.store(createHeader());
            assertThat(index).isEqualTo(0);
        }
    }
}
