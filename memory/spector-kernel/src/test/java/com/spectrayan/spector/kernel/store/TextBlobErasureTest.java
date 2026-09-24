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
import com.spectrayan.spector.kernel.store.TextBlobMemory.EraseOutcome;
import com.spectrayan.spector.kernel.store.TextBlobMemory.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests physical erasure of stored text, including the deduplication hazard that makes it conditional.
 *
 * <p>{@code TextBlobMemory.write} deduplicates by content hash, so two records with identical text share one
 * region of bytes. Erasing "one record's" text is therefore not always a local operation, and the method has
 * to be able to say so.</p>
 */
@DisplayName("TextBlobMemory erasure")
class TextBlobErasureTest {

    @TempDir
    Path tempDir;

    private TextBlobMemory open() {
        return new TextBlobMemory(tempDir.resolve("text.dat"));
    }

    @Nested
    @DisplayName("Unshared text")
    class Unshared {

        @Test
        @DisplayName("erase overwrites the text bytes with zeros, verified by raw off-heap read")
        void eraseZeroesTheBytes() {
            try (TextBlobMemory store = open()) {
                TextPosition pos = store.write("m-1", MemoryType.SEMANTIC, "the quick brown fox");
                store.write("m-2", MemoryType.SEMANTIC, "an entirely different string");

                assertThat(store.readTextDirect(pos.textOffset(), pos.textLength()))
                        .isEqualTo("the quick brown fox");

                EraseOutcome outcome = store.eraseEntry("m-1");

                assertThat(outcome.status()).isEqualTo(EraseOutcome.Status.ERASED);
                assertThat(outcome.bytesZeroed()).isEqualTo("the quick brown fox".length());
                // Read the original byte range back directly: nothing but zeros should remain.
                String raw = store.readTextDirect(pos.textOffset(), pos.textLength());
                assertThat(raw).isNotNull();
                assertThat(raw.chars().allMatch(c -> c == 0)).isTrue();
                // The untouched neighbour is unaffected.
                TextPosition other = store.textPositions().get("m-2");
                assertThat(store.readTextDirect(other.textOffset(), other.textLength()))
                        .isEqualTo("an entirely different string");
            }
        }

        @Test
        @DisplayName("an erased entry reloads as empty text, not as a NUL-filled string")
        void erasedEntryReloadsEmpty() {
            Path file = tempDir.resolve("text.dat");
            try (TextBlobMemory store = new TextBlobMemory(file)) {
                store.write("keep", MemoryType.SEMANTIC, "surviving content");
                store.write("gone", MemoryType.SEMANTIC, "content to destroy");
                store.eraseEntry("gone");
                store.flush();
            }
            try (TextBlobMemory reopened = new TextBlobMemory(file)) {
                var entries = reopened.readAll();
                // Without collapsing the frame's text-length field this would come back as 18 NUL
                // characters — content-shaped garbage that looks like a decoding fault rather than
                // a deliberate erasure.
                assertThat(entries.get("gone").text()).isEmpty();
                assertThat(entries.get("keep").text()).isEqualTo("surviving content");
            }
        }

        @Test
        @DisplayName("erasing an unknown id reports NOT_FOUND rather than claiming success")
        void unknownIdIsNotFound() {
            try (TextBlobMemory store = open()) {
                store.write("m-1", MemoryType.SEMANTIC, "something");
                assertThat(store.eraseEntry("no-such-id").status())
                        .isEqualTo(EraseOutcome.Status.NOT_FOUND);
            }
        }
    }

    @Nested
    @DisplayName("Deduplicated text shared between records")
    class Shared {

        @Test
        @DisplayName("identical text is stored once — two ids resolve to the same byte range")
        void identicalTextIsDeduplicated() {
            try (TextBlobMemory store = open()) {
                TextPosition a = store.write("m-a", MemoryType.SEMANTIC, "exactly the same text");
                TextPosition b = store.write("m-b", MemoryType.SEMANTIC, "exactly the same text");
                assertThat(b).isEqualTo(a);
            }
        }

        @Test
        @DisplayName("erasing one of two co-tenants keeps the bytes and reports SHARED")
        void sharedTextIsNotErased() {
            try (TextBlobMemory store = open()) {
                store.write("m-a", MemoryType.SEMANTIC, "exactly the same text");
                store.write("m-b", MemoryType.SEMANTIC, "exactly the same text");

                EraseOutcome outcome = store.eraseEntry("m-a");

                // Zeroing here would have silently destroyed m-b's text while leaving m-b's position
                // mapping pointing at the zeroed bytes. Reporting ERASED would be a false claim.
                assertThat(outcome.status()).isEqualTo(EraseOutcome.Status.SHARED);
                assertThat(outcome.sharedWith()).isEqualTo(1);
                assertThat(outcome.bytesZeroed()).isZero();
                assertThat(outcome.erased()).isFalse();

                TextPosition kept = store.textPositions().get("m-b");
                assertThat(store.readTextDirect(kept.textOffset(), kept.textLength()))
                        .isEqualTo("exactly the same text");
                // m-a no longer resolves to anything at all.
                assertThat(store.textPositions()).doesNotContainKey("m-a");
            }
        }

        @Test
        @DisplayName("erasing the last co-tenant does destroy the bytes")
        void lastCoTenantErasesForReal() {
            try (TextBlobMemory store = open()) {
                TextPosition pos = store.write("m-a", MemoryType.SEMANTIC, "shared then orphaned");
                store.write("m-b", MemoryType.SEMANTIC, "shared then orphaned");

                assertThat(store.eraseEntry("m-a").status()).isEqualTo(EraseOutcome.Status.SHARED);
                assertThat(store.eraseEntry("m-b").status()).isEqualTo(EraseOutcome.Status.ERASED);

                String raw = store.readTextDirect(pos.textOffset(), pos.textLength());
                assertThat(raw).isNotNull();
                assertThat(raw.chars().allMatch(c -> c == 0)).isTrue();
            }
        }
    }
}
