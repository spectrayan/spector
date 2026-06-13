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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LocalAssetStore}.
 */
@DisplayName("LocalAssetStore")
class LocalAssetStoreTest {

    @TempDir
    Path tempDir;

    private LocalAssetStore store;
    private Path sourceFile;

    @BeforeEach
    void setUp() throws IOException {
        store = new LocalAssetStore(tempDir.resolve("assets"));
        sourceFile = tempDir.resolve("test_image.jpg");
        Files.writeString(sourceFile, "fake-image-content");
    }

    // ══════════════════════════════════════════════════════════════
    // STORE
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Store")
    class StoreTests {

        @Test
        @DisplayName("Stores file and returns URI")
        void storesFileAndReturnsUri() throws IOException {
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");

            assertNotNull(uri);
            assertTrue(store.exists(uri));
        }

        @Test
        @DisplayName("Stored file has correct content")
        void storedFileHasCorrectContent() throws IOException {
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");

            try (InputStream is = store.retrieve(uri)) {
                String content = new String(is.readAllBytes());
                assertEquals("fake-image-content", content);
            }
        }

        @Test
        @DisplayName("Creates memory ID subdirectory")
        void createsMemoryIdSubdir() throws IOException {
            store.store(sourceFile, "mem-42", "image/jpeg");

            Path expectedDir = tempDir.resolve("assets").resolve("mem-42");
            assertTrue(Files.isDirectory(expectedDir));
        }

        @Test
        @DisplayName("Sanitizes memory ID with special characters")
        void sanitizesMemoryId() throws IOException {
            store.store(sourceFile, "mem/42:special<>chars", "image/jpeg");

            // Should store under sanitized directory name
            Path expectedDir = tempDir.resolve("assets").resolve("mem_42_special__chars");
            assertTrue(Files.isDirectory(expectedDir));
        }

        @Test
        @DisplayName("Overwrites existing file")
        void overwritesExisting() throws IOException {
            store.store(sourceFile, "mem-42", "image/jpeg");

            // Write different content
            Files.writeString(sourceFile, "updated-content");
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");

            try (InputStream is = store.retrieve(uri)) {
                assertEquals("updated-content", new String(is.readAllBytes()));
            }
        }

        @Test
        @DisplayName("Throws on null source")
        void throwsOnNullSource() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.store(null, "mem-42", "image/jpeg"));
        }

        @Test
        @DisplayName("Throws on null memoryId")
        void throwsOnNullMemoryId() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.store(sourceFile, null, "image/jpeg"));
        }

        @Test
        @DisplayName("Throws on blank memoryId")
        void throwsOnBlankMemoryId() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.store(sourceFile, "", "image/jpeg"));
        }

        @Test
        @DisplayName("Throws on non-existent source")
        void throwsOnNonExistentSource() {
            Path missing = tempDir.resolve("missing.jpg");
            assertThrows(IOException.class,
                    () -> store.store(missing, "mem-42", "image/jpeg"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RETRIEVE
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("Throws on null URI")
        void throwsOnNullUri() {
            assertThrows(IllegalArgumentException.class,
                    () -> store.retrieve(null));
        }

        @Test
        @DisplayName("Throws on non-existent asset")
        void throwsOnNonExistentAsset() {
            URI fakeUri = tempDir.resolve("nonexistent.jpg").toUri();
            assertThrows(IOException.class, () -> store.retrieve(fakeUri));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EXISTS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exists")
    class ExistsTests {

        @Test
        @DisplayName("Returns false for null URI")
        void falseForNull() {
            assertFalse(store.exists(null));
        }

        @Test
        @DisplayName("Returns false for non-existent asset")
        void falseForNonExistent() {
            URI fakeUri = tempDir.resolve("nonexistent.jpg").toUri();
            assertFalse(store.exists(fakeUri));
        }

        @Test
        @DisplayName("Returns true for stored asset")
        void trueForStored() throws IOException {
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");
            assertTrue(store.exists(uri));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("Deletes stored asset")
        void deletesStoredAsset() throws IOException {
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");
            assertTrue(store.exists(uri));

            store.delete(uri);
            assertFalse(store.exists(uri));
        }

        @Test
        @DisplayName("Delete is idempotent")
        void deleteIsIdempotent() throws IOException {
            URI uri = store.store(sourceFile, "mem-42", "image/jpeg");
            store.delete(uri);
            assertDoesNotThrow(() -> store.delete(uri));
        }

        @Test
        @DisplayName("Delete null URI is no-op")
        void deleteNullIsNoop() {
            assertDoesNotThrow(() -> store.delete(null));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MULTIPLE ASSETS PER MEMORY
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Stores multiple assets for the same memory ID")
    void multipleAssetsPerMemory() throws IOException {
        Path file1 = tempDir.resolve("photo1.jpg");
        Path file2 = tempDir.resolve("photo2.png");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        URI uri1 = store.store(file1, "mem-42", "image/jpeg");
        URI uri2 = store.store(file2, "mem-42", "image/png");

        assertTrue(store.exists(uri1));
        assertTrue(store.exists(uri2));

        try (InputStream is1 = store.retrieve(uri1)) {
            assertEquals("content1", new String(is1.readAllBytes()));
        }
        try (InputStream is2 = store.retrieve(uri2)) {
            assertEquals("content2", new String(is2.readAllBytes()));
        }
    }
}
