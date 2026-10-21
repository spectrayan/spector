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
package com.spectrayan.spector.kernel.engram;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.score.RecordGates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * markRetracted sets only the retracted bit, and retracted records are always gated (#1144).
 */
class MarkRetractedTest {

    private static final EngramLayout LAYOUT = new EngramLayout(16);

    @Test
    @DisplayName("markRetracted sets FLAG_RETRACTED and keeps the other consolidation flags")
    void markRetractedSetsOnlyTheRetractedBit() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(LAYOUT.stride());
            LAYOUT.writeHeader(seg, 0, EncodingHeader.create(
                    1_700_000_000_000L, 0L, 1.0f, 1.0f, (short) 0, MemoryType.SEMANTIC));
            LAYOUT.markContradicted(seg, 0);
            assertThat(EncodingHeaderFields.isRetracted(LAYOUT.readConsolidationFlags(seg, 0))).isFalse();

            LAYOUT.markRetracted(seg, 0);

            byte cFlags = LAYOUT.readConsolidationFlags(seg, 0);
            assertThat(EncodingHeaderFields.isRetracted(cFlags)).isTrue();
            assertThat(EncodingHeaderFields.isContradicted(cFlags)).isTrue();
        }
    }

    @Test
    @DisplayName("retracted records are gated even when contradictions are included")
    void retractedIsAlwaysGated() {
        byte retracted = EncodingHeaderFields.FLAG_RETRACTED;
        assertThat(RecordGates.isDeletedOrContradicted((byte) 0, retracted, true)).isTrue();
        assertThat(RecordGates.isDeletedOrContradicted((byte) 0, retracted, false)).isTrue();
        assertThat(RecordGates.isDeletedOrContradicted((byte) 0, (byte) 0, true)).isFalse();
    }
}
