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

import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.store.DefaultHeaderCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Byte-parity test asserting that operations through HeaderCursor produce
 * byte-identical 64-byte encoding headers compared to direct layout mutations (Req: R6.5).
 */
@DisplayName("HeaderCursor Byte Parity Verification (R6.5)")
class HeaderCursorByteParityTest {

    private static final int DIMS = 8;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);
    private static final int STRIDE = LAYOUT.stride();

    @Test
    @DisplayName("HeaderCursor mutations produce byte-identical header relative to direct layout mutations")
    void cursorMutationsMatchDirectLayoutBytes() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segDirect = arena.allocate(STRIDE);
            MemorySegment segCursor = arena.allocate(STRIDE);

            long ts = 1_700_000_000_000L;
            byte flags = (byte) 0b0000_0100; // Semantic tier
            byte valence = 10;
            byte arousal = 50;
            float importance = 4.2f;

            EncodingHeader initHeader = new EncodingHeader(
                    ts, 0x1234_5678L, 1.0f, importance, 0, (short) 10,
                    valence, flags, arousal, 1.0f,
                    (byte) 1, (byte) 20, (byte) 30, (short) 5, 0.0f, (byte) 0,
                    EngramSource.EXPERIENCED
            );

            // Initialize both buffers with the exact same header bytes
            LAYOUT.writeHeader(segDirect, 0, initHeader);
            LAYOUT.writeHeader(segCursor, 0, initHeader);

            // Direct pre-change mutation path on segDirect
            LAYOUT.headerLayout().writeValenceRelease(segDirect, 0, (byte) 75);
            LAYOUT.headerLayout().casImportance(segDirect, 0, imp -> imp + 1.8f);
            LAYOUT.headerLayout().mergeSynapticTags128(segDirect, 0, 0x0F0F_0F0FL, 0xF0F0_F0F0L);
            LAYOUT.headerLayout().markPinned(segDirect, 0);
            LAYOUT.headerLayout().markConsolidated(segDirect, 0);

            // HeaderCursor mutation path on segCursor
            try (var cursor = DefaultHeaderCursor.forSegment(segCursor, LAYOUT)) {
                cursor.seek(0);
                cursor.valenceRelease((byte) 75);
                cursor.updateImportance(imp -> imp + 1.8f);
                cursor.mergeSynapticTags(0x0F0F_0F0FL, 0xF0F0_F0F0L);
                cursor.pin();
                cursor.markConsolidated();
            }

            // Assert 100% byte parity across the 64-byte encoding header
            long mismatchOffset = MemorySegment.mismatch(segDirect, 0, 64, segCursor, 0, 64);
            assertThat(mismatchOffset)
                    .as("HeaderCursor mutations must produce byte-identical header vs direct layout writes (mismatch at %d)", mismatchOffset)
                    .isEqualTo(-1L);
        }
    }
}
