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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.SemanticLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@code purge} physically destroys record content, as distinct from {@code tombstone}, which
 * only hides it.
 *
 * <p>Every assertion here reads the <b>raw {@link MemorySegment} bytes</b>. That is the point of the test.
 * Asserting "recall no longer returns it" would pass just as happily against a plain tombstone, and a
 * tombstone leaves every content byte sitting in the file and in every backup of it — which is precisely the
 * false durability claim this test exists to prevent from reappearing.</p>
 */
@DisplayName("Engram purge — physical payload destruction")
class EngramPurgeTest {

    private static final int VEC_BYTES = 32;
    private static final SemanticLayout LAYOUT = new SemanticLayout(VEC_BYTES);

    /** Allocates a one-record region and fills its payload with a recognisable non-zero pattern. */
    private static MemorySegment newRecord(Arena arena) {
        MemorySegment seg = arena.allocate(LAYOUT.stride(), 64);
        EncodingHeader header = new EncodingHeader(
                1_716_900_000_000L,          // timestampMs
                0x0123456789ABCDEFL,          // synapticTagsLo
                1.25f,                        // exactNorm
                4.5f,                         // importance
                0,                            // agentRecallCount
                (short) 42,                   // centroidId
                (byte) 10,                    // valence
                EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal()));
        LAYOUT.headerLayout().writeHeader(seg, 0L, header);
        LAYOUT.headerLayout().writeSynapticTags(seg, 0L, 0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
        for (int i = 0; i < VEC_BYTES; i++) {
            seg.set(ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(0L) + i, (byte) (i + 1));
        }
        return seg;
    }

    private static boolean payloadIsAllZero(MemorySegment seg) {
        for (int i = 0; i < VEC_BYTES; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(0L) + i) != 0) {
                return false;
            }
        }
        return true;
    }

    @Nested
    @DisplayName("The distinction between tombstone and purge")
    class TombstoneVersusPurge {

        @Test
        @DisplayName("tombstone leaves every payload byte readable on disk — the defect purge exists to fix")
        void tombstoneDoesNotDestroyAnything() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                LAYOUT.tombstone(seg, 0L);

                assertThat(EncodingHeaderFields.isTombstoned(LAYOUT.readFlags(seg, 0L))).isTrue();
                // This is the honest, uncomfortable fact: the content is entirely intact.
                assertThat(payloadIsAllZero(seg)).isFalse();
                for (int i = 0; i < VEC_BYTES; i++) {
                    assertThat(seg.get(ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(0L) + i))
                            .isEqualTo((byte) (i + 1));
                }
                assertThat(LAYOUT.isPurged(seg, 0L)).isFalse();
            }
        }

        @Test
        @DisplayName("purge overwrites the whole payload range with zeros, verified byte by byte")
        void purgeZeroesThePayload() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                int zeroed = LAYOUT.purge(seg, 0L);

                assertThat(zeroed).isEqualTo(VEC_BYTES);
                for (int i = 0; i < VEC_BYTES; i++) {
                    assertThat(seg.get(ValueLayout.JAVA_BYTE, LAYOUT.vectorOffset(0L) + i))
                            .as("payload byte %d", i)
                            .isZero();
                }
            }
        }

        @Test
        @DisplayName("purge sets both the purged and the tombstone bit")
        void purgeSetsBothBits() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                LAYOUT.purge(seg, 0L);

                assertThat(LAYOUT.isPurged(seg, 0L)).isTrue();
                // The tombstone bit matters: the many existing read gates check only that, and they must
                // keep hiding the record. Setting only FLAG_PURGED would make a purged record read as live.
                assertThat(EncodingHeaderFields.isTombstoned(LAYOUT.readFlags(seg, 0L))).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Content-derived header fields")
    class HeaderScrubbing {

        @Test
        @DisplayName("purge clears exactNorm, the synaptic-tag Bloom filter, and centroidId")
        void purgeClearsDerivedContentFields() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                EncodingHeaderLayout hdr = LAYOUT.headerLayout();
                assertThat(hdr.readExactNorm(seg, 0L)).isNotZero();
                assertThat(hdr.readSynapticTagsLo(seg, 0L)).isNotZero();
                assertThat(hdr.readSynapticTagsHi(seg, 0L)).isNotZero();
                assertThat(hdr.readCentroidId(seg, 0L)).isNotZero();

                LAYOUT.purge(seg, 0L);

                // Each of these is derived from the content. Left behind, the Bloom filter still answers
                // "this record was about X" and can be probed by trial, and centroidId localises the vector.
                assertThat(hdr.readExactNorm(seg, 0L)).isZero();
                assertThat(hdr.readSynapticTagsLo(seg, 0L)).isZero();
                assertThat(hdr.readSynapticTagsHi(seg, 0L)).isZero();
                assertThat(hdr.readCentroidId(seg, 0L)).isZero();
            }
        }

        @Test
        @DisplayName("purge deliberately retains lifecycle metadata, so the erasure stays auditable")
        void purgeRetainsLifecycleMetadata() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                LAYOUT.purge(seg, 0L);

                EncodingHeaderLayout hdr = LAYOUT.headerLayout();
                // Not an oversight — a documented, disclosable choice. None of these can reconstruct
                // content, and without them a purge leaves no evidence that anything was ever there.
                assertThat(hdr.readTimestamp(seg, 0L)).isEqualTo(1_716_900_000_000L);
                assertThat(hdr.readHeaderVersion(seg, 0L))
                        .isEqualTo((byte) EncodingHeaderFields.HEADER_VERSION_V2);
                assertThat(EncodingHeaderFields.memoryTypeOf(LAYOUT.readFlags(seg, 0L)))
                        .isEqualTo(MemoryType.SEMANTIC);
            }
        }
    }

    @Nested
    @DisplayName("Reading a purged record")
    class ReadPaths {

        @Test
        @DisplayName("readVector reports absence rather than handing back the zeros")
        void readVectorReturnsNullNotZeros() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                EngramRegion region = EngramRegion.of(seg, 1, LAYOUT, MemoryType.SEMANTIC, 0L);

                assertThat(region.readVector(0L)).isNotNull();

                region.purge(0L);

                // Returning the zeros would be worse than useless: they decode to a legitimate all-zero
                // vector and get scored as data, so the purged record would quietly participate in results.
                assertThat(region.readVector(0L)).isNull();
                assertThat(region.isPurged(0L)).isTrue();
                assertThat(region.isTombstoned(0L)).isTrue();
            }
        }

        @Test
        @DisplayName("purge is idempotent")
        void purgeTwiceIsSafe() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                assertThat(LAYOUT.purge(seg, 0L)).isEqualTo(VEC_BYTES);
                assertThat(LAYOUT.purge(seg, 0L)).isEqualTo(VEC_BYTES);
                assertThat(payloadIsAllZero(seg)).isTrue();
                assertThat(LAYOUT.isPurged(seg, 0L)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("The purged flag in the header bitmask")
    class FlagBit {

        @Test
        @DisplayName("FLAG_PURGED is 0x40 and does not collide with any existing consolidation flag")
        void flagPurgedOccupiesTheLastFreeBit() {
            assertThat(EncodingHeaderFields.FLAG_PURGED).isEqualTo((byte) 0x40);

            byte others = (byte) (EncodingHeaderFields.FLAG_CONTRADICTED
                    | EncodingHeaderFields.FLAG_RETRACTED
                    | EncodingHeaderFields.FLAG_UNVERIFIED
                    | EncodingHeaderFields.FLAG_RESTRICTED
                    | EncodingHeaderFields.FLAG_CRYSTALLIZED
                    | EncodingHeaderFields.FLAG_SIMULATED
                    | EncodingHeaderFields.FLAG_DREAMED);
            assertThat(EncodingHeaderFields.FLAG_PURGED & others).isZero();

            // Byte 40 is now fully allocated. If this fails, someone freed or claimed a bit and the
            // "no free flag bit left" note in EncodingHeaderFields needs revisiting.
            assertThat((byte) (others | EncodingHeaderFields.FLAG_PURGED)).isEqualTo((byte) 0xFF);
        }

        @Test
        @DisplayName("purged is orthogonal to the other consolidation flags, not exclusive with them")
        void purgedCoexistsWithOtherFlags() {
            byte flags = EncodingHeaderFields.withRetracted((byte) 0, true);
            flags = EncodingHeaderFields.withPurged(flags, true);

            assertThat(EncodingHeaderFields.isPurged(flags)).isTrue();
            assertThat(EncodingHeaderFields.isRetracted(flags)).isTrue();
            assertThat(EncodingHeaderFields.isDreamed(flags)).isFalse();
        }

        @Test
        @DisplayName("the purged bit survives a full header round trip through EncodingHeader")
        void purgedSurvivesHeaderRewrite() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = newRecord(arena);
                LAYOUT.purge(seg, 0L);

                EncodingHeaderLayout hdr = LAYOUT.headerLayout();
                // writeHeader writes consolidationFlags wholesale, so a read-modify-write cycle would
                // silently clear FLAG_PURGED if the record did not carry the flags through.
                EncodingHeader readBack = hdr.readHeader(seg, 0L);
                assertThat(EncodingHeaderFields.isPurged(readBack.consolidationFlags())).isTrue();

                hdr.writeHeader(seg, 0L, readBack);
                assertThat(hdr.isPurged(seg, 0L)).isTrue();
            }
        }
    }
}
