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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OllamaVisionExtractor}.
 *
 * <p>Tests extractor behavior (input validation, MIME type filtering, edge cases)
 * without requiring a running Ollama server. Integration tests that hit the real
 * server should be annotated with {@code @Tag("integration")}.</p>
 */
@DisplayName("OllamaVisionExtractor")
class OllamaVisionExtractorTest {

    private final OllamaVisionExtractor extractor = OllamaVisionExtractor.createDefault();

    @TempDir
    Path tempDir;

    // ══════════════════════════════════════════════════════════════
    // SUPPORTED MIME TYPES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MIME Type Support")
    class MimeTypeSupport {

        @Test
        @DisplayName("Supports standard image MIME types")
        void supportsImageTypes() {
            assertTrue(extractor.supports("image/jpeg"));
            assertTrue(extractor.supports("image/png"));
            assertTrue(extractor.supports("image/webp"));
            assertTrue(extractor.supports("image/gif"));
            assertTrue(extractor.supports("image/bmp"));
        }

        @Test
        @DisplayName("Does not support non-image MIME types")
        void doesNotSupportNonImage() {
            assertFalse(extractor.supports("text/plain"));
            assertFalse(extractor.supports("audio/mp3"));
            assertFalse(extractor.supports("video/mp4"));
            assertFalse(extractor.supports("application/json"));
        }

        @Test
        @DisplayName("Handles null MIME type gracefully")
        void handlesNullMimeType() {
            assertFalse(extractor.supports(null));
        }

        @Test
        @DisplayName("Returns empty stream for unsupported MIME type")
        void emptyStreamForUnsupported() throws IOException {
            Path dummyFile = tempDir.resolve("test.txt");
            Files.writeString(dummyFile, "hello");

            try (Stream<SensoryExtractor.ExtractionChunk> stream =
                         extractor.extract(dummyFile, "text/plain")) {
                assertEquals(0, stream.count());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INPUT VALIDATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("Throws on null source path")
        void throwsOnNullSource() {
            assertThrows(IllegalArgumentException.class,
                    () -> extractor.extract(null, "image/jpeg"));
        }

        @Test
        @DisplayName("Throws on non-existent file")
        void throwsOnNonExistentFile() {
            Path missing = tempDir.resolve("missing.jpg");
            assertThrows(IOException.class,
                    () -> extractor.extract(missing, "image/jpeg"));
        }

        @Test
        @DisplayName("Throws on empty file")
        void throwsOnEmptyFile() throws IOException {
            Path emptyFile = tempDir.resolve("empty.jpg");
            Files.createFile(emptyFile);

            assertThrows(IOException.class,
                    () -> extractor.extract(emptyFile, "image/jpeg"),
                    "Empty files should be rejected");
        }

        @Test
        @DisplayName("Throws on oversized file")
        void throwsOnOversizedFile() throws IOException {
            Path largeFile = tempDir.resolve("large.jpg");
            // Create a file slightly over 20MB
            byte[] data = new byte[21 * 1024 * 1024];
            Files.write(largeFile, data);

            assertThrows(IOException.class,
                    () -> extractor.extract(largeFile, "image/jpeg"),
                    "Files over 20MB should be rejected");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ExtractionChunk VALIDATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ExtractionChunk")
    class ExtractionChunkTests {

        @Test
        @DisplayName("Rejects null chunkId")
        void rejectsNullChunkId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SensoryExtractor.ExtractionChunk(null, "text", null));
        }

        @Test
        @DisplayName("Rejects blank chunkId")
        void rejectsBlankChunkId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SensoryExtractor.ExtractionChunk("", "text", null));
        }

        @Test
        @DisplayName("Rejects null text")
        void rejectsNullText() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SensoryExtractor.ExtractionChunk("id", null, null));
        }

        @Test
        @DisplayName("Null metadata defaults to empty map")
        void nullMetadataDefaults() {
            var chunk = new SensoryExtractor.ExtractionChunk("id", "text", null);
            assertNotNull(chunk.metadata());
            assertTrue(chunk.metadata().isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("createDefault uses moondream model")
        void createDefaultModel() {
            var ext = OllamaVisionExtractor.createDefault();
            assertEquals("moondream", ext.model());
        }

        @Test
        @DisplayName("create(model) uses specified model")
        void createWithModel() {
            var ext = OllamaVisionExtractor.create("llava:13b");
            assertEquals("llava:13b", ext.model());
        }

        @Test
        @DisplayName("Null model defaults to moondream")
        void nullModelDefault() {
            var ext = OllamaVisionExtractor.create(null);
            assertEquals("moondream", ext.model());
        }
    }
}
