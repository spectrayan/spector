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
package com.spectrayan.spector.kernel.layout;

import com.spectrayan.spector.kernel.store.ProvenanceEdge;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-0086 §5.5: ProvenanceLayout Extension & Reserved Bytes Tests")
class ProvenanceLayoutExtensionTest {

    private final ProvenanceLayout layout = ProvenanceLayout.INSTANCE;

    @Test
    @DisplayName("Constants SOURCE_SEMANTIC and SOURCE_PROCEDURAL exist with correct values")
    void sourceKindConstants() {
        assertThat(ProvenanceLayout.SOURCE_EPISODIC_LOG).isEqualTo((byte) 1);
        assertThat(ProvenanceLayout.SOURCE_SEMANTIC).isEqualTo((byte) 2);
        assertThat(ProvenanceLayout.SOURCE_PROCEDURAL).isEqualTo((byte) 3);
    }

    @Test
    @DisplayName("Read/write round-trip of sourceTsid and sourcePartition in ProvenanceLayout")
    void readWriteSourceTsidAndPartition() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ProvenanceLayout.RECORD_STRIDE);

            long expectedSourceTsid = 0x0123456789ABCDEFL;
            int expectedSourcePartition = 42;

            ProvenanceLayout.writeSourceTsid(seg, 0L, expectedSourceTsid);
            ProvenanceLayout.writeSourcePartition(seg, 0L, expectedSourcePartition);

            assertThat(ProvenanceLayout.readSourceTsid(seg, 0L)).isEqualTo(expectedSourceTsid);
            assertThat(ProvenanceLayout.readSourcePartition(seg, 0L)).isEqualTo(expectedSourcePartition);
        }
    }

    @Test
    @DisplayName("Composite readRecord/writeRecord round-trip preserves sourceTsid and sourcePartition")
    void compositeRecordRoundTripWithSourceTsid() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ProvenanceLayout.RECORD_STRIDE);

            ProvenanceLayout.ProvenanceState state = new ProvenanceLayout.ProvenanceState(
                    ProvenanceLayout.FLAG_LIVE,
                    ProvenanceLayout.SOURCE_SEMANTIC,
                    ProvenanceLayout.TARGET_PROCEDURAL,
                    (byte) 0,
                    (short) 1,
                    (short) 5,
                    123456L,
                    7891011L,
                    System.currentTimeMillis(),
                    1,
                    10,
                    15,
                    100,
                    200,
                    (byte) 0,
                    (byte) 1,
                    (short) 0x7FFF,
                    0xFEEDFACECAFEBEEFL,
                    7
            );

            ProvenanceLayout.writeRecord(seg, 0L, state);
            ProvenanceLayout.ProvenanceState read = ProvenanceLayout.readRecord(seg, 0L);

            assertThat(read.flags()).isEqualTo(ProvenanceLayout.FLAG_LIVE);
            assertThat(read.sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_SEMANTIC);
            assertThat(read.targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);
            assertThat(read.sourceTsid()).isEqualTo(0xFEEDFACECAFEBEEFL);
            assertThat(read.sourcePartition()).isEqualTo(7);
            assertThat(read.targetTsid()).isEqualTo(7891011L);
        }
    }

    @Test
    @DisplayName("Backward-compatible constructor defaults sourceTsid and sourcePartition to 0")
    void backwardCompatibleConstructor() {
        ProvenanceLayout.ProvenanceState legacyState = new ProvenanceLayout.ProvenanceState(
                ProvenanceLayout.FLAG_LIVE,
                ProvenanceLayout.SOURCE_EPISODIC_LOG,
                ProvenanceLayout.TARGET_SEMANTIC,
                (byte) 0,
                (short) 1,
                (short) 2,
                100L,
                200L,
                System.currentTimeMillis(),
                0,
                1,
                2,
                0,
                0,
                (byte) 0,
                (byte) 1,
                (short) 0
        );

        assertThat(legacyState.sourceTsid()).isEqualTo(0L);
        assertThat(legacyState.sourcePartition()).isEqualTo(0);
    }

    @Test
    @DisplayName("ProvenanceMemory appends and retrieves multi-tier provenance edge")
    void provenanceMemoryMultiTierAppend() {
        ProvenanceMemory mem = ProvenanceMemory.heap(10);

        ProvenanceEdge edge = new ProvenanceEdge(
                12345L,
                67890L,
                (short) 1,
                (byte) 0,
                (byte) 1,
                0,
                0,
                0,
                0,
                0,
                (short) 1,
                (short) 0,
                System.currentTimeMillis(),
                ProvenanceLayout.SOURCE_SEMANTIC,
                ProvenanceLayout.TARGET_PROCEDURAL,
                (byte) 0,
                999888777L,
                3
        );

        int slot = mem.append(edge);
        assertThat(slot).isGreaterThanOrEqualTo(0);

        var retrieved = mem.findByTarget(67890L);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().sourceKind()).isEqualTo(ProvenanceLayout.SOURCE_SEMANTIC);
        assertThat(retrieved.get().targetKind()).isEqualTo(ProvenanceLayout.TARGET_PROCEDURAL);
        assertThat(retrieved.get().sourceTsid()).isEqualTo(999888777L);
        assertThat(retrieved.get().sourcePartition()).isEqualTo(3);
    }
}
