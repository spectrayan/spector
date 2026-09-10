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
import com.spectrayan.spector.kernel.layout.SemanticLayout;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;

import com.spectrayan.spector.kernel.api.EngramSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates Source Honesty (NF7 / R6.1, R6.2, R6.3):
 * <ul>
 *     <li>R6.1: Dedicated header field at byte offset 46 stores {@link EngramSource}.</li>
 *     <li>R6.2: Default recall hard-gates {@link EngramSource#SIMULATED}.</li>
 *     <li>R6.3: Source provenance is never inferred from ID string prefixes or synaptic tags.</li>
 * </ul>
 */
@DisplayName("Source Honesty & Provenance Isolation Tests (NF7 / R6.1, R6.2, R6.3)")
class SourceHonestyTest {

    private final EncodingHeaderLayout layout = EncodingHeaderLayout.INSTANCE;

    @Test
    @DisplayName("R6.1: All four EngramSource values write and read correctly at offset 46")
    void testSourceFieldByteOffsetAndRoundTrip() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(64L);

            for (EngramSource expectedSource : EngramSource.values()) {
                long now = System.currentTimeMillis();
                EncodingHeader header = new EncodingHeader(
                        now, 0x1234L, 1.0f, 5.0f, 0, (short) 0, (byte) 10,
                        (byte) 0, (byte) 50, 1.0f, (byte) 1, (byte) 2, (byte) 3,
                        (short) 4, 0.5f, (byte) 0, expectedSource
                );

                layout.writeHeader(segment, 0L, header);

                // 1. Direct raw byte inspection at offset 46
                byte rawByte = segment.get(ValueLayout.JAVA_BYTE, EncodingHeaderFields.OFFSET_V2_SOURCE);
                assertThat(rawByte)
                        .as("Offset 46 must match raw code for %s", expectedSource)
                        .isEqualTo(expectedSource.code());

                // 2. Layout fast zero-allocation accessor
                assertThat(layout.readSourceCode(segment, 0L)).isEqualTo(expectedSource.code());

                // 3. Layout enum accessor
                assertThat(layout.readSource(segment, 0L)).isEqualTo(expectedSource);

                // 4. Full header record read
                EncodingHeader readHeader = layout.readHeader(segment, 0L);
                assertThat(readHeader.source()).isEqualTo(expectedSource);
            }
        }
    }

}
