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
import com.spectrayan.spector.kernel.bundle.AbstractBundle;
import com.spectrayan.spector.kernel.bundle.RegionLease;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.error.StaleRegionException;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.store.DefaultHeaderCursor;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HeaderCursor Round-Trip and Generation Contract Tests (R6.2, R6.3)")
class HeaderCursorRoundTripTest {

    private static final int DIMS = 16;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);

    @Test
    @DisplayName("Round-trip test for every field defined in EncodingHeaderFields")
    void roundTripAllEncodingHeaderFields() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(LAYOUT.stride() * 2);
            StrengthMemory strength = StrengthMemory.heap(2, 2, 2);

            var initHeader = EncodingHeader.create(1_700_000_000_000L, 0L, 1.0f, 1.0f, (short) 0, MemoryType.SEMANTIC);
            LAYOUT.writeHeader(seg, 0, initHeader);

            try (var cursor = new DefaultHeaderCursor(seg, LAYOUT, MemoryType.SEMANTIC, 2, 0, strength)) {
                cursor.seek(0);

                // Version & base metadata
                assertThat(cursor.headerVersion()).isEqualTo((byte) 2);
                cursor.flags((byte) 0b0010_1010);
                assertThat(cursor.flags()).isEqualTo((byte) 0b0010_1010);

                // Affect
                cursor.valenceRelease((byte) 42);
                assertThat(cursor.valence()).isEqualTo((byte) 42);
                assertThat(cursor.compareAndSetValence((byte) 42, (byte) 84)).isTrue();
                assertThat(cursor.valence()).isEqualTo((byte) 84);
                assertThat(cursor.compareAndSetValence((byte) 99, (byte) 100)).isFalse();
                assertThat(cursor.valence()).isEqualTo((byte) 84);

                cursor.arousal((byte) 200);
                assertThat(cursor.arousal()).isEqualTo((byte) 200);

                // Importance
                cursor.initializeImportance(3.5f);
                assertThat(cursor.importance()).isEqualTo(3.5f);
                float updatedImp = cursor.updateImportance(f -> f * 2.0f);
                assertThat(updatedImp).isEqualTo(7.0f);
                assertThat(cursor.importance()).isEqualTo(7.0f);

                // Timestamp, Exact Norm, Centroid ID
                cursor.timestampMs(1_700_000_000_123L);
                assertThat(cursor.timestampMs()).isEqualTo(1_700_000_000_123L);

                cursor.exactNorm(12.34f);
                assertThat(cursor.exactNorm()).isEqualTo(12.34f);

                cursor.centroidId((short) 1024);
                assertThat(cursor.centroidId()).isEqualTo((short) 1024);

                // 128-bit Synaptic Tags
                cursor.initializeSynapticTags(0x0123_4567_89AB_CDEFL, 0xFEDC_BA98_7654_3210L);
                assertThat(cursor.synapticTagsLo()).isEqualTo(0x0123_4567_89AB_CDEFL);
                assertThat(cursor.synapticTagsHi()).isEqualTo(0xFEDC_BA98_7654_3210L);

                cursor.mergeSynapticTags(0x1000_0000_0000_0000L, 0x0000_0000_0000_0001L);
                assertThat(cursor.synapticTagsLo()).isEqualTo(0x1123_4567_89AB_CDEFL);
                assertThat(cursor.synapticTagsHi()).isEqualTo(0xFEDC_BA98_7654_3211L);

                // Consolidation and cognitive state
                cursor.consolidationFlags((byte) 0b0001_0001);
                assertThat(cursor.consolidationFlags()).isEqualTo((byte) 0b0001_0001);

                cursor.encodingProfile((byte) 3);
                assertThat(cursor.encodingProfile()).isEqualTo((byte) 3);

                cursor.encodingAlpha((byte) 120);
                assertThat(cursor.encodingAlpha()).isEqualTo((byte) 120);

                cursor.encodingBeta((byte) 240);
                assertThat(cursor.encodingBeta()).isEqualTo((byte) 240);

                cursor.encodingSurprise(2.718f);
                assertThat(cursor.encodingSurprise()).isEqualTo(2.718f);

                // Soul version and Provenance
                cursor.soulVersion((short) 42);
                assertThat(cursor.soulVersion()).isEqualTo((short) 42);

                cursor.source(EngramSource.DISTILLED);
                assertThat(cursor.source()).isEqualTo(EngramSource.DISTILLED);
                assertThat(cursor.sourceCode()).isEqualTo((byte) EngramSource.DISTILLED.code());

                // Modality bit manipulation
                cursor.sourceModality(SourceModality.AUDIO);
                assertThat(cursor.sourceModality()).isEqualTo(SourceModality.AUDIO);

                // Flags bit predicates and actions (start from clean flags)
                cursor.flags((byte) 0);
                cursor.consolidationFlags((byte) 0);
                assertThat(cursor.isPinned()).isFalse();
                cursor.pin();
                assertThat(cursor.isPinned()).isTrue();

                assertThat(cursor.isResolved()).isFalse();
                cursor.markResolved();
                assertThat(cursor.isResolved()).isTrue();
                cursor.markUnresolved();
                assertThat(cursor.isResolved()).isFalse();

                assertThat(cursor.isConsolidated()).isFalse();
                cursor.markConsolidated();
                assertThat(cursor.isConsolidated()).isTrue();

                assertThat(cursor.isContradicted()).isFalse();
                cursor.markContradicted();
                assertThat(cursor.isContradicted()).isTrue();

                assertThat(cursor.isTombstoned()).isFalse();
                cursor.tombstone();
                assertThat(cursor.isTombstoned()).isTrue();

                // Strength fields
                cursor.activationCount(5);
                assertThat(cursor.activationCount()).isEqualTo(5);
                assertThat(cursor.addActivationCount(3)).isEqualTo(8);
                assertThat(cursor.activationCount()).isEqualTo(8);

                cursor.lastAccessEpochMs(1_700_000_500_000L);
                assertThat(cursor.lastAccessEpochMs()).isEqualTo(1_700_000_500_000L);

                cursor.storageStrength(2.5f);
                assertThat(cursor.storageStrength()).isEqualTo(2.5f);
                float nextS = cursor.updateStorageStrength(s -> s + 0.5f);
                assertThat(nextS).isEqualTo(3.0f);
                assertThat(cursor.storageStrength()).isEqualTo(3.0f);

                cursor.spectorRecallCount(12);
                assertThat(cursor.spectorRecallCount()).isEqualTo(12);

                // ACT-R ring buffer
                cursor.recordActRRecall(1_700_000_000_000L, 1_700_000_015_000L);
                int[] actrTs = cursor.readActRTimestamps();
                assertThat(actrTs[0]).isEqualTo(15);
                assertThat(cursor.computeActRActivation(1_700_000_000_000L, 1_700_000_020_000L)).isBetween(0.0f, 1.0f);

                // Snapshot
                EncodingHeader header = cursor.readHeader();
                assertThat(header).isNotNull();
                assertThat(header.timestampMs()).isEqualTo(1_700_000_000_123L);
                assertThat(header.source()).isEqualTo(EngramSource.DISTILLED);
            }
        }
    }

    @Test
    @DisplayName("seek and seekOffset throw StaleRegionException after growRegion invalidation (R6.2, R6.3)")
    void seekThrowsStaleRegionExceptionAfterGrow() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(LAYOUT.stride() * 2);

            AtomicInteger gen = new AtomicInteger(1);
            AbstractBundle bundle = mock(AbstractBundle.class);
            when(bundle.generation(any())).thenAnswer(inv -> gen.get());
            when(bundle.currentSlice(any())).thenReturn(seg);
            when(bundle.lease(any())).thenReturn(new RegionLease(seg, () -> {}));

            RegionRef ref = new RegionRef(bundle, RegionId.BM25);
            DefaultHeaderCursor cursor = new DefaultHeaderCursor(ref, LAYOUT, MemoryType.SEMANTIC, 10, 0, null);
            cursor.seek(0);
            assertThat(cursor.currentSlot()).isEqualTo(0);

            // Generation increments (as happens when growRegion unmaps and remaps)
            gen.incrementAndGet();

            // Attempting to seek or seekOffset must detect stale generation and throw StaleRegionException
            assertThatThrownBy(() -> cursor.seek(1))
                    .isInstanceOf(StaleRegionException.class)
                    ;

            assertThatThrownBy(() -> cursor.seekOffset(LAYOUT.stride()))
                    .isInstanceOf(StaleRegionException.class);

            // Positioned access must also throw StaleRegionException
            assertThatThrownBy(cursor::flags)
                    .isInstanceOf(StaleRegionException.class);

            cursor.close();
        }
    }

    @Test
    @DisplayName("HeaderCursor.close() releases active region lease and is idempotent")
    void cursorCloseDropsLease(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, LAYOUT.stride(), 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            RegionRef ref = bundle.regionRef(RegionId.BM25);

            assertThat(bundle.activeLeases()).isZero();

            DefaultHeaderCursor cursor = new DefaultHeaderCursor(ref, LAYOUT, MemoryType.SEMANTIC, 10, 0, null);
            assertThat(bundle.activeLeases()).isEqualTo(1);

            cursor.close();
            assertThat(bundle.activeLeases()).isZero();

            // Idempotent
            cursor.close();
            assertThat(bundle.activeLeases()).isZero();

            // Operations on closed cursor throw IllegalStateException
            assertThatThrownBy(() -> cursor.seek(0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Test
    @DisplayName("Cursor unpositioned throws IllegalStateException on field access")
    void unpositionedCursorThrows() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(LAYOUT.stride());
            try (var cursor = DefaultHeaderCursor.forSegment(seg, LAYOUT)) {
                assertThatThrownBy(cursor::flags)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("not positioned");
            }
        }
    }
}
