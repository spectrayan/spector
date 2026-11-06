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

import com.spectrayan.spector.core.math.XxHash64;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.TextBlobMemory.EraseOutcome;
import com.spectrayan.spector.kernel.store.TextBlobMemory.TextEntry;
import com.spectrayan.spector.kernel.store.TextBlobMemory.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link TextBlobMemory} content-hash deduplication under {@link XxHash64} collisions (#1189).
 */
@DisplayName("TextBlobMemory hash collision handling (#1189)")
class TextBlobHashCollisionTest {

    /**
     * Distinct 16-byte UTF-8 texts that produce the exact same 64-bit {@link XxHash64#hash(byte[])}.
     */
    private static final String COLLIDING_TEXT_A = "alpha_fact_one_1";
    private static final String COLLIDING_TEXT_B = "b0014134zqYry9Pi";
    private static final String COLLIDING_TEXT_C = "b020905758bER2do";

    @TempDir
    Path tempDir;

    private static void assertForcedCollisionPreconditions() {
        byte[] bytesA = COLLIDING_TEXT_A.getBytes(StandardCharsets.UTF_8);
        byte[] bytesB = COLLIDING_TEXT_B.getBytes(StandardCharsets.UTF_8);
        byte[] bytesC = COLLIDING_TEXT_C.getBytes(StandardCharsets.UTF_8);

        assertThat(COLLIDING_TEXT_A)
                .isNotEqualTo(COLLIDING_TEXT_B)
                .isNotEqualTo(COLLIDING_TEXT_C);
        assertThat(COLLIDING_TEXT_B).isNotEqualTo(COLLIDING_TEXT_C);
        assertThat(bytesA.length)
                .isEqualTo(bytesB.length)
                .isEqualTo(bytesC.length);
        assertThat(XxHash64.hash(bytesA))
                .as("forced collision texts must share the same 64-bit XxHash64")
                .isEqualTo(XxHash64.hash(bytesB))
                .isEqualTo(XxHash64.hash(bytesC));
    }

    @Test
    @DisplayName("colliding equal-length texts are stored as separate blobs and each id reads back its own text")
    void collidingEqualLengthTextsDoNotConflate() {
        assertForcedCollisionPreconditions();

        Path file = tempDir.resolve("text.dat");
        try (TextBlobMemory store = new TextBlobMemory(file)) {
            TextPosition posA = store.write("mem-a", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            TextPosition posB = store.write("mem-b", MemoryType.SEMANTIC, COLLIDING_TEXT_B);

            assertThat(posB.textOffset())
                    .as("distinct equal-length texts with the same XxHash64 must not share a blob offset")
                    .isNotEqualTo(posA.textOffset());
            assertThat(store.readTextDirect(posA.textOffset(), posA.textLength()))
                    .isEqualTo(COLLIDING_TEXT_A);
            assertThat(store.readTextDirect(posB.textOffset(), posB.textLength()))
                    .isEqualTo(COLLIDING_TEXT_B);

            assertThat(store.textPositions().get("mem-a")).isEqualTo(posA);
            assertThat(store.textPositions().get("mem-b")).isEqualTo(posB);

            Map<String, TextEntry> entries = store.readAll();
            assertThat(entries).containsKeys("mem-a", "mem-b");
            assertThat(entries.get("mem-a").text()).isEqualTo(COLLIDING_TEXT_A);
            assertThat(entries.get("mem-b").text()).isEqualTo(COLLIDING_TEXT_B);
        }
    }

    @Test
    @DisplayName("identical texts still deduplicate to one blob alongside colliding texts")
    void identicalTextsStillDeduplicateWhenCollisionBucketHasMultipleEntries() {
        assertForcedCollisionPreconditions();

        Path file = tempDir.resolve("text.dat");
        try (TextBlobMemory store = new TextBlobMemory(file)) {
            TextPosition posA1 = store.write("mem-a1", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            TextPosition posB1 = store.write("mem-b1", MemoryType.SEMANTIC, COLLIDING_TEXT_B);
            TextPosition posA2 = store.write("mem-a2", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            TextPosition posB2 = store.write("mem-b2", MemoryType.SEMANTIC, COLLIDING_TEXT_B);

            assertThat(posA2).isEqualTo(posA1);
            assertThat(posB2).isEqualTo(posB1);
            assertThat(posA1).isNotEqualTo(posB1);

            assertThat(store.readTextDirect(posA2.textOffset(), posA2.textLength()))
                    .isEqualTo(COLLIDING_TEXT_A);
            assertThat(store.readTextDirect(posB2.textOffset(), posB2.textLength()))
                    .isEqualTo(COLLIDING_TEXT_B);

            Map<String, TextEntry> physicalEntries = store.readAll();
            assertThat(physicalEntries).hasSize(2);
            assertThat(physicalEntries.get("mem-a1").text()).isEqualTo(COLLIDING_TEXT_A);
            assertThat(physicalEntries.get("mem-b1").text()).isEqualTo(COLLIDING_TEXT_B);
        }
    }

    @Test
    @DisplayName("restart preserves colliding entries and does not conflate subsequent colliding or duplicate writes")
    void restartPreservesCollisionEntriesWithoutConflation() {
        assertForcedCollisionPreconditions();

        Path file = tempDir.resolve("text.dat");
        TextPosition initialPosA;
        TextPosition initialPosB;
        try (TextBlobMemory store = new TextBlobMemory(file)) {
            initialPosA = store.write("mem-a", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            initialPosB = store.write("mem-b", MemoryType.EPISODIC, COLLIDING_TEXT_B);
            store.flush();
        }

        try (TextBlobMemory reopened = new TextBlobMemory(file)) {
            Map<String, TextEntry> loaded = reopened.readAll();
            assertThat(loaded).hasSize(2);
            assertThat(loaded.get("mem-a").text()).isEqualTo(COLLIDING_TEXT_A);
            assertThat(loaded.get("mem-b").text()).isEqualTo(COLLIDING_TEXT_B);

            TextPosition loadedPosA = reopened.textPositions().get("mem-a");
            TextPosition loadedPosB = reopened.textPositions().get("mem-b");
            assertThat(loadedPosA).isEqualTo(initialPosA);
            assertThat(loadedPosB).isEqualTo(initialPosB);
            assertThat(reopened.readTextDirect(loadedPosA.textOffset(), loadedPosA.textLength()))
                    .isEqualTo(COLLIDING_TEXT_A);
            assertThat(reopened.readTextDirect(loadedPosB.textOffset(), loadedPosB.textLength()))
                    .isEqualTo(COLLIDING_TEXT_B);

            // A third colliding text written after restart must allocate a new blob
            TextPosition posC = reopened.write("mem-c", MemoryType.SEMANTIC, COLLIDING_TEXT_C);
            assertThat(posC.textOffset())
                    .isNotEqualTo(loadedPosA.textOffset())
                    .isNotEqualTo(loadedPosB.textOffset());
            assertThat(reopened.readTextDirect(posC.textOffset(), posC.textLength()))
                    .isEqualTo(COLLIDING_TEXT_C);

            // Exact duplicates of either pre-restart colliding entry still deduplicate to their matching blob
            TextPosition dupPosA = reopened.write("mem-a-dup", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            TextPosition dupPosB = reopened.write("mem-b-dup", MemoryType.EPISODIC, COLLIDING_TEXT_B);
            assertThat(dupPosA).isEqualTo(loadedPosA);
            assertThat(dupPosB).isEqualTo(loadedPosB);
        }
    }

    @Test
    @DisplayName("erasing one colliding entry zeroes its bytes without treating the other as a co-tenant")
    void erasingOneCollidingEntryLeavesTheOtherIntact() {
        assertForcedCollisionPreconditions();

        Path file = tempDir.resolve("text.dat");
        try (TextBlobMemory store = new TextBlobMemory(file)) {
            TextPosition posA = store.write("mem-a", MemoryType.SEMANTIC, COLLIDING_TEXT_A);
            TextPosition posB = store.write("mem-b", MemoryType.SEMANTIC, COLLIDING_TEXT_B);

            EraseOutcome outcome = store.eraseEntry("mem-a");
            assertThat(outcome.status()).isEqualTo(EraseOutcome.Status.ERASED);
            assertThat(outcome.bytesZeroed()).isEqualTo(COLLIDING_TEXT_A.length());

            String zeroedA = store.readTextDirect(posA.textOffset(), posA.textLength());
            assertThat(zeroedA).isNotNull();
            assertThat(zeroedA.chars().allMatch(c -> c == 0)).isTrue();

            assertThat(store.readTextDirect(posB.textOffset(), posB.textLength()))
                    .isEqualTo(COLLIDING_TEXT_B);
        }
    }
}
