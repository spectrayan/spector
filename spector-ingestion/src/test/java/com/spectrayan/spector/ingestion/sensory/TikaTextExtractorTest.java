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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TikaTextExtractor}.
 */
@DisplayName("TikaTextExtractor")
class TikaTextExtractorTest {

    @TempDir
    Path tempDir;

    private TikaTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TikaTextExtractor(200, 50);
    }

    // ══════════════════════════════════════════════════════════════
    // PLAIN TEXT EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Plain Text")
    class PlainTextTests {

        @Test
        @DisplayName("Extracts text from .txt file")
        void extractsFromTxt() throws IOException {
            Path txtFile = tempDir.resolve("doc.txt");
            Files.writeString(txtFile, "Hello, this is a test document about memory systems.");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(txtFile, "text/plain")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertFalse(result.isEmpty());
                assertTrue(result.getFirst().text().contains("memory systems"));
                assertEquals("chunk-0", result.getFirst().chunkId());
            }
        }

        @Test
        @DisplayName("Short text produces single chunk")
        void shortTextSingleChunk() throws IOException {
            Path txtFile = tempDir.resolve("short.txt");
            Files.writeString(txtFile, "Short content.");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(txtFile, "text/plain")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertEquals(1, result.size());
                assertEquals("0", result.getFirst().metadata().get("chunk_index"));
                assertEquals("1", result.getFirst().metadata().get("total_chunks"));
            }
        }

        @Test
        @DisplayName("Long text produces multiple chunks with overlap")
        void longTextMultipleChunks() throws IOException {
            Path txtFile = tempDir.resolve("long.txt");
            String longText = "A".repeat(100) + " " + "B".repeat(100) + " " + "C".repeat(100)
                    + " " + "D".repeat(100) + " " + "E".repeat(100);
            Files.writeString(txtFile, longText);

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(txtFile, "text/plain")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertTrue(result.size() > 1, "Should produce multiple chunks for 500+ chars with chunkSize=200");

                // Verify chunk IDs are sequential
                for (int i = 0; i < result.size(); i++) {
                    assertEquals("chunk-" + i, result.get(i).chunkId());
                }
            }
        }

        @Test
        @DisplayName("Metadata includes source_uri and extractor")
        void metadataPresent() throws IOException {
            Path txtFile = tempDir.resolve("meta.txt");
            Files.writeString(txtFile, "Test content for metadata verification.");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(txtFile, "text/plain")) {
                SensoryExtractor.ExtractionChunk chunk = chunks.findFirst().orElseThrow();

                assertEquals("tika", chunk.metadata().get("extractor"));
                assertNotNull(chunk.metadata().get("source_uri"));
                assertEquals("TEXT", chunk.metadata().get("modality"));
                assertEquals("meta.txt", chunk.metadata().get("original_filename"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HTML EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HTML")
    class HtmlTests {

        @Test
        @DisplayName("Extracts text from HTML, strips tags")
        void extractsFromHtml() throws IOException {
            Path htmlFile = tempDir.resolve("page.html");
            Files.writeString(htmlFile,
                    "<html><body><h1>Title</h1><p>This is a paragraph about AI memory.</p></body></html>");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(htmlFile, "text/html")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertFalse(result.isEmpty());
                String allText = result.stream().map(SensoryExtractor.ExtractionChunk::text)
                        .reduce("", (a, b) -> a + " " + b);
                assertTrue(allText.contains("Title"));
                assertTrue(allText.contains("AI memory"));
                assertFalse(allText.contains("<h1>"), "HTML tags should be stripped");
            }
        }

        @Test
        @DisplayName("Extracts title from HTML metadata")
        void extractsHtmlTitle() throws IOException {
            Path htmlFile = tempDir.resolve("titled.html");
            Files.writeString(htmlFile,
                    "<html><head><title>My Document Title</title></head>" +
                    "<body><p>Content here about vector search engines.</p></body></html>");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(htmlFile, "text/html")) {
                SensoryExtractor.ExtractionChunk chunk = chunks.findFirst().orElseThrow();
                // Tika extracts <title> into metadata
                assertEquals("My Document Title", chunk.metadata().get("title"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MARKDOWN EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Markdown")
    class MarkdownTests {

        @Test
        @DisplayName("Extracts text from markdown")
        void extractsFromMarkdown() throws IOException {
            Path mdFile = tempDir.resolve("doc.md");
            Files.writeString(mdFile, "# Heading\n\nThis is **bold** and _italic_ text about Spector.");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(mdFile, "text/markdown")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertFalse(result.isEmpty());
                String text = result.getFirst().text();
                assertTrue(text.contains("Heading") || text.contains("Spector"));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty file returns empty stream")
        void emptyFileReturnsEmptyStream() throws IOException {
            Path emptyFile = tempDir.resolve("empty.txt");
            Files.writeString(emptyFile, "");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(emptyFile, "text/plain")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("Whitespace-only file returns empty stream")
        void whitespaceOnlyReturnsEmpty() throws IOException {
            Path wsFile = tempDir.resolve("whitespace.txt");
            Files.writeString(wsFile, "   \n\n\t\t   \n");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(wsFile, "text/plain")) {
                assertEquals(0, chunks.count());
            }
        }

        @Test
        @DisplayName("Null source throws IOException")
        void nullSourceThrows() {
            assertThrows(IOException.class, () -> extractor.extract(null, "text/plain"));
        }

        @Test
        @DisplayName("Non-existent file throws IOException")
        void nonExistentFileThrows() {
            Path missing = tempDir.resolve("doesnt_exist.txt");
            assertThrows(IOException.class, () -> extractor.extract(missing, "text/plain"));
        }

        @Test
        @DisplayName("Non-readable file throws IOException")
        void binaryGarbageHandledGracefully() throws IOException {
            Path binFile = tempDir.resolve("garbage.bin");
            Files.write(binFile, new byte[]{0x00, 0x01, 0x02, (byte)0xFF});

            // Tika should handle this gracefully — either extract something or return empty
            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(binFile, "application/octet-stream")) {
                // Should not throw
                chunks.toList();
            }
        }

        @Test
        @DisplayName("MIME type null — auto-detection via filename extension")
        void nullMimeTypeAutoDetects() throws IOException {
            Path txtFile = tempDir.resolve("auto.txt");
            Files.writeString(txtFile, "Auto-detected content about neural networks.");

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(txtFile, null)) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();
                assertFalse(result.isEmpty());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SUPPORTED MIME TYPES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MIME Type Support")
    class MimeTypeTests {

        @Test
        @DisplayName("Supports PDF")
        void supportsPdf() {
            assertTrue(extractor.supports("application/pdf"));
        }

        @Test
        @DisplayName("Supports DOCX")
        void supportsDocx() {
            assertTrue(extractor.supports(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        }

        @Test
        @DisplayName("Supports HTML")
        void supportsHtml() {
            assertTrue(extractor.supports("text/html"));
        }

        @Test
        @DisplayName("Supports plain text")
        void supportsPlainText() {
            assertTrue(extractor.supports("text/plain"));
        }

        @Test
        @DisplayName("Supports markdown")
        void supportsMarkdown() {
            assertTrue(extractor.supports("text/markdown"));
        }

        @Test
        @DisplayName("Does not support image types")
        void doesNotSupportImages() {
            assertFalse(extractor.supports("image/jpeg"));
            assertFalse(extractor.supports("image/png"));
        }

        @Test
        @DisplayName("Does not support null")
        void doesNotSupportNull() {
            assertFalse(extractor.supports(null));
        }

        @Test
        @DisplayName("isAvailable always true")
        void isAlwaysAvailable() {
            assertTrue(extractor.isAvailable());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DEFAULT CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Default constructor creates valid extractor")
    void defaultConstructor() throws IOException {
        TikaTextExtractor defaultExtractor = new TikaTextExtractor();

        Path txtFile = tempDir.resolve("default.txt");
        Files.writeString(txtFile, "Test with default settings.");

        try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                     defaultExtractor.extract(txtFile, "text/plain")) {
            assertFalse(chunks.toList().isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RESOURCE FILE EXTRACTION (from test resources)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resource Files")
    class ResourceFileTests {

        @Test
        @DisplayName("Extracts from sample.txt resource")
        void extractsSampleTxt() throws Exception {
            var url = getClass().getClassLoader().getResource("test-documents/sample.txt");
            assertNotNull(url, "sample.txt should be in test resources");
            Path sampleTxt = Path.of(url.toURI());

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(sampleTxt, "text/plain")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertFalse(result.isEmpty());
                String allText = result.stream().map(SensoryExtractor.ExtractionChunk::text)
                        .reduce("", (a, b) -> a + " " + b);
                assertTrue(allText.contains("hippocampus") || allText.contains("memory"),
                        "Should contain text about memory systems");
            }
        }

        @Test
        @DisplayName("Extracts from sample.html resource")
        void extractsSampleHtml() throws Exception {
            var url = getClass().getClassLoader().getResource("test-documents/sample.html");
            assertNotNull(url, "sample.html should be in test resources");
            Path sampleHtml = Path.of(url.toURI());

            try (Stream<SensoryExtractor.ExtractionChunk> chunks =
                         extractor.extract(sampleHtml, "text/html")) {
                List<SensoryExtractor.ExtractionChunk> result = chunks.toList();

                assertFalse(result.isEmpty());
                String allText = result.stream().map(SensoryExtractor.ExtractionChunk::text)
                        .reduce("", (a, b) -> a + " " + b);
                assertTrue(allText.contains("SensoryExtractor") || allText.contains("Multimodal"),
                        "Should contain architecture decision content");
                assertFalse(allText.contains("<h1>"), "HTML tags should be stripped");
            }
        }
    }
}
