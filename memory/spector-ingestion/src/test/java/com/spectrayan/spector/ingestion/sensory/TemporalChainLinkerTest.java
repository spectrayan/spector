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
package com.spectrayan.spector.ingestion.sensory;

import com.spectrayan.spector.ingestion.sensory.SensoryExtractor.ExtractionChunk;
import com.spectrayan.spector.ingestion.sensory.TemporalChainLinker.TemporalLink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TemporalChainLinker}.
 */
@DisplayName("TemporalChainLinker")
class TemporalChainLinkerTest {

    // ══════════════════════════════════════════════════════════════
    // SEQUENTIAL LINKING
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sequential Linking")
    class SequentialTests {

        @Test
        @DisplayName("Links two chunks")
        void linksTwoChunks() {
            var chunks = List.of(
                    chunk("frame-0", "First frame", Map.of("timestamp_seconds", "0")),
                    chunk("frame-1", "Second frame", Map.of("timestamp_seconds", "10"))
            );

            List<TemporalLink> links = TemporalChainLinker.linkSequential(chunks, 1);

            assertEquals(1, links.size());
            assertEquals("frame-0", links.getFirst().predecessorChunkId());
            assertEquals("frame-1", links.getFirst().successorChunkId());
            assertEquals(1, links.getFirst().sessionId());
            assertEquals(10, links.getFirst().timestampSeconds());
        }

        @Test
        @DisplayName("Links three chunks into chain")
        void linksThreeChunks() {
            var chunks = List.of(
                    chunk("f-0", "Frame at 0s", Map.of("timestamp_seconds", "0")),
                    chunk("f-1", "Frame at 10s", Map.of("timestamp_seconds", "10")),
                    chunk("f-2", "Frame at 20s", Map.of("timestamp_seconds", "20"))
            );

            List<TemporalLink> links = TemporalChainLinker.linkSequential(chunks, 42);

            assertEquals(2, links.size());

            // First link: f-0 → f-1
            assertEquals("f-0", links.get(0).predecessorChunkId());
            assertEquals("f-1", links.get(0).successorChunkId());
            assertEquals(10, links.get(0).timestampSeconds());

            // Second link: f-1 → f-2
            assertEquals("f-1", links.get(1).predecessorChunkId());
            assertEquals("f-2", links.get(1).successorChunkId());
            assertEquals(20, links.get(1).timestampSeconds());

            // Both share same session
            assertEquals(42, links.get(0).sessionId());
            assertEquals(42, links.get(1).sessionId());
        }

        @Test
        @DisplayName("Five chunks produce four links")
        void fiveChunksFourLinks() {
            var chunks = List.of(
                    chunk("a", "T1", Map.of()),
                    chunk("b", "T2", Map.of()),
                    chunk("c", "T3", Map.of()),
                    chunk("d", "T4", Map.of()),
                    chunk("e", "T5", Map.of())
            );

            List<TemporalLink> links = TemporalChainLinker.linkSequential(chunks, 0);
            assertEquals(4, links.size());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Null chunks returns empty list")
        void nullChunksReturnsEmpty() {
            assertTrue(TemporalChainLinker.linkSequential(null, 0).isEmpty());
        }

        @Test
        @DisplayName("Empty list returns empty")
        void emptyListReturnsEmpty() {
            assertTrue(TemporalChainLinker.linkSequential(List.of(), 0).isEmpty());
        }

        @Test
        @DisplayName("Single chunk returns empty (no pair)")
        void singleChunkReturnsEmpty() {
            var chunks = List.of(chunk("only", "Only chunk", Map.of()));
            assertTrue(TemporalChainLinker.linkSequential(chunks, 0).isEmpty());
        }

        @Test
        @DisplayName("Missing timestamp defaults to -1")
        void missingTimestampDefaults() {
            var chunks = List.of(
                    chunk("a", "First", Map.of()),
                    chunk("b", "Second", Map.of())
            );

            List<TemporalLink> links = TemporalChainLinker.linkSequential(chunks, 0);
            assertEquals(-1, links.getFirst().timestampSeconds());
        }

        @Test
        @DisplayName("Invalid timestamp defaults to -1")
        void invalidTimestampDefaults() {
            var chunks = List.of(
                    chunk("a", "First", Map.of("timestamp_seconds", "abc")),
                    chunk("b", "Second", Map.of("timestamp_seconds", "not-a-number"))
            );

            List<TemporalLink> links = TemporalChainLinker.linkSequential(chunks, 0);
            assertEquals(-1, links.getFirst().timestampSeconds());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PREFIXED LINKING
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Prefixed Linking")
    class PrefixedTests {

        @Test
        @DisplayName("Adds prefix to chunk IDs")
        void addsPrefix() {
            var chunks = List.of(
                    chunk("frame-0", "F1", Map.of("timestamp_seconds", "0")),
                    chunk("frame-1", "F2", Map.of("timestamp_seconds", "5"))
            );

            List<TemporalLink> links =
                    TemporalChainLinker.linkSequential(chunks, 1, "mem-123::");

            assertEquals("mem-123::frame-0", links.getFirst().predecessorChunkId());
            assertEquals("mem-123::frame-1", links.getFirst().successorChunkId());
        }

        @Test
        @DisplayName("Null prefix treated as empty")
        void nullPrefixEmpty() {
            var chunks = List.of(
                    chunk("a", "First", Map.of()),
                    chunk("b", "Second", Map.of())
            );

            List<TemporalLink> links =
                    TemporalChainLinker.linkSequential(chunks, 0, null);

            assertEquals("a", links.getFirst().predecessorChunkId());
            assertEquals("b", links.getFirst().successorChunkId());
        }

        @Test
        @DisplayName("Empty prefix has no effect")
        void emptyPrefixNoEffect() {
            var chunks = List.of(
                    chunk("x", "First", Map.of()),
                    chunk("y", "Second", Map.of())
            );

            List<TemporalLink> links =
                    TemporalChainLinker.linkSequential(chunks, 0, "");

            assertEquals("x", links.getFirst().predecessorChunkId());
        }

        @Test
        @DisplayName("Single chunk returns empty even with prefix")
        void singleChunkEmpty() {
            var chunks = List.of(chunk("only", "Only", Map.of()));
            assertTrue(TemporalChainLinker.linkSequential(chunks, 0, "pfx::").isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TEMPORAL LINK RECORD
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("TemporalLink Record")
    class TemporalLinkTests {

        @Test
        @DisplayName("durationGap computes difference")
        void durationGapComputes() {
            var link1 = new TemporalLink("a", "b", 1, 10);
            var link2 = new TemporalLink("b", "c", 1, 25);

            assertEquals(15, link2.durationGap(link1));
        }

        @Test
        @DisplayName("durationGap with null previous returns -1")
        void durationGapNullPrevious() {
            var link = new TemporalLink("a", "b", 1, 10);
            assertEquals(-1, link.durationGap(null));
        }

        @Test
        @DisplayName("durationGap with missing timestamps returns -1")
        void durationGapMissingTimestamps() {
            var link1 = new TemporalLink("a", "b", 1, -1);
            var link2 = new TemporalLink("b", "c", 1, 10);

            assertEquals(-1, link2.durationGap(link1));
        }

        @Test
        @DisplayName("Record equality works")
        void recordEquality() {
            var link1 = new TemporalLink("a", "b", 1, 10);
            var link2 = new TemporalLink("a", "b", 1, 10);
            assertEquals(link1, link2);
            assertEquals(link1.hashCode(), link2.hashCode());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════

    private static ExtractionChunk chunk(String id, String text, Map<String, String> metadata) {
        return new ExtractionChunk(id, text, metadata);
    }
}
