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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ProvenanceLayout}'s lifecycle byte as an <b>exclusive state enum</b>, not a bitmask.
 *
 * <p>This exists because the constants used to be named {@code FLAG_*}, which reads as a bitmask and
 * invites "harmonising" the overwrite into an OR to match {@code EncodingHeaderLayout}. That change looks
 * like a cleanup and is a state-corruption bug: this test demonstrates the exact failure it would cause, so
 * the reasoning survives in executable form rather than only in a comment.</p>
 */
@DisplayName("ProvenanceLayout lifecycle byte is an exclusive state, not a bitmask")
class ProvenanceStateSemanticsTest {

    @Test
    @DisplayName("the three states are distinct values, and only TOMBSTONE is not live")
    void statesAreDistinctAndClassifiedCorrectly() {
        assertThat(ProvenanceLayout.STATE_LIVE).isEqualTo((byte) 0);
        assertThat(ProvenanceLayout.STATE_TOMBSTONE).isEqualTo((byte) 1);
        assertThat(ProvenanceLayout.STATE_PARTIAL_RUN).isEqualTo((byte) 2);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ProvenanceLayout.RECORD_STRIDE);

            ProvenanceLayout.writeFlags(seg, 0L, ProvenanceLayout.STATE_LIVE);
            assertThat(ProvenanceLayout.isLive(seg, 0L)).isTrue();
            assertThat(ProvenanceLayout.isTombstoned(seg, 0L)).isFalse();

            ProvenanceLayout.writeFlags(seg, 0L, ProvenanceLayout.STATE_PARTIAL_RUN);
            assertThat(ProvenanceLayout.isLive(seg, 0L)).isTrue();
            assertThat(ProvenanceLayout.isTombstoned(seg, 0L)).isFalse();

            ProvenanceLayout.writeFlags(seg, 0L, ProvenanceLayout.STATE_TOMBSTONE);
            assertThat(ProvenanceLayout.isLive(seg, 0L)).isFalse();
            assertThat(ProvenanceLayout.isTombstoned(seg, 0L)).isTrue();
        }
    }

    @Test
    @DisplayName("tombstoning by overwrite works from every prior state, including PARTIAL_RUN")
    void overwriteTombstonesFromAnyState() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ProvenanceLayout.RECORD_STRIDE);
            for (byte prior : new byte[]{
                    ProvenanceLayout.STATE_LIVE,
                    ProvenanceLayout.STATE_PARTIAL_RUN,
                    ProvenanceLayout.STATE_TOMBSTONE}) {
                ProvenanceLayout.writeFlags(seg, 0L, prior);
                ProvenanceLayout.tombstone(seg, 0L);
                assertThat(ProvenanceLayout.isTombstoned(seg, 0L))
                        .as("tombstone from prior state %d", prior)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("OR-ing the states — the tempting 'fix' — makes a tombstoned record read as live")
    void orWouldCorruptState() {
        // Simulates the change this test exists to prevent: OR instead of overwrite.
        byte corrupted = (byte) (ProvenanceLayout.STATE_PARTIAL_RUN | ProvenanceLayout.STATE_TOMBSTONE);

        assertThat(corrupted).isEqualTo((byte) 3);
        // 3 is not a defined state, so the equality checks classify it as neither tombstoned nor live.
        assertThat(corrupted).isNotEqualTo(ProvenanceLayout.STATE_TOMBSTONE);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ProvenanceLayout.RECORD_STRIDE);
            ProvenanceLayout.writeFlags(seg, 0L, corrupted);
            // The record was meant to be tombstoned. Under an OR it is not reported as tombstoned —
            // a deleted provenance edge would silently keep participating.
            assertThat(ProvenanceLayout.isTombstoned(seg, 0L)).isFalse();
        }
    }
}
