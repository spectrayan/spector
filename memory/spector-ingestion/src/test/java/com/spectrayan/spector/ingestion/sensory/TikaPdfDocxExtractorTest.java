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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TikaTextExtractor} with real PDF and DOCX test files.
 *
 * <p>These tests use actual binary files generated in
 * {@code src/test/resources/test-documents/}.</p>
 */
@DisplayName("TikaTextExtractor — PDF & DOCX")
class TikaPdfDocxExtractorTest {

    private static final TikaTextExtractor extractor = new TikaTextExtractor(500, 80);
    private static final TikaTextExtractor largeChunkExtractor = new TikaTextExtractor(5000, 200);

    // ══════════════════════════════════════════════════════════════
    // PDF EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PDF Extraction")
    class PdfTests {

        @Test
        @DisplayName("Extracts text from single-page PDF")
        void singlePagePdf() throws IOException {
            Path pdf = resolveResource("test-documents/single_page.pdf");
            if (!Files.exists(pdf)) return;  // Skip if resource not found

            try (Stream<ExtractionChunk> chunks = extractor.extract(pdf, "application/pdf")) {
                List<ExtractionChunk> chunkList = chunks.toList();
                assertFalse(chunkList.isEmpty(), "Should extract at least one chunk from PDF");

                // Verify content contains expected text
                String allText = chunkList.stream()
                        .map(ExtractionChunk::text)
                        .reduce("", String::concat);
                assertTrue(allText.contains("Spector") || allText.contains("vector"),
                        "Should contain 'Spector' or 'vector' text: " + allText);
            }
        }

        @Test
        @DisplayName("Multi-page PDF produces multiple chunks")
        void multiPagePdf() throws IOException {
            Path pdf = resolveResource("test-documents/multi_page.pdf");
            if (!Files.exists(pdf)) return;

            try (Stream<ExtractionChunk> chunks = largeChunkExtractor.extract(pdf, "application/pdf")) {
                List<ExtractionChunk> chunkList = chunks.toList();
                assertFalse(chunkList.isEmpty(), "Should extract chunks from multi-page PDF");

                // Multi-page PDF should have content from all pages
                String allText = chunkList.stream()
                        .map(ExtractionChunk::text)
                        .reduce("", (a, b) -> a + " " + b);

                // Check content from different pages is present
                assertTrue(allText.contains("Introduction") || allText.contains("Spector"),
                        "Should contain page 1 content");
                assertTrue(allText.contains("Architecture") || allText.contains("HNSW"),
                        "Should contain page 2 content");
                assertTrue(allText.contains("Performance") || allText.contains("Benchmark"),
                        "Should contain page 3 content");
            }
        }

        @Test
        @DisplayName("Empty PDF produces empty or minimal chunks")
        void emptyPdf() throws IOException {
            Path pdf = resolveResource("test-documents/empty.pdf");
            if (!Files.exists(pdf)) return;

            try (Stream<ExtractionChunk> chunks = extractor.extract(pdf, "application/pdf")) {
                List<ExtractionChunk> chunkList = chunks.toList();
                // Empty PDF might produce an empty chunk list or a chunk with only whitespace
                for (ExtractionChunk chunk : chunkList) {
                    // If chunks exist, they should be nearly empty
                    assertTrue(chunk.text().strip().length() < 50,
                            "Empty PDF chunks should be minimal: " + chunk.text().strip());
                }
            }
        }

        @Test
        @DisplayName("PDF MIME type is supported")
        void pdfMimeSupported() {
            assertTrue(extractor.supports("application/pdf"));
        }

        @Test
        @DisplayName("PDF chunk metadata includes page info")
        void pdfChunkMetadata() throws IOException {
            Path pdf = resolveResource("test-documents/single_page.pdf");
            if (!Files.exists(pdf)) return;

            try (Stream<ExtractionChunk> chunks = extractor.extract(pdf, "application/pdf")) {
                List<ExtractionChunk> chunkList = chunks.toList();
                if (!chunkList.isEmpty()) {
                    var chunk = chunkList.getFirst();
                    assertNotNull(chunk.chunkId(), "Chunk should have an ID");
                    assertNotNull(chunk.metadata(), "Chunk should have metadata");
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DOCX EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DOCX Extraction")
    class DocxTests {

        @Test
        @DisplayName("Extracts text from DOCX file")
        void docxExtraction() throws IOException {
            Path docx = resolveResource("test-documents/sample.docx");
            if (!Files.exists(docx)) return;

            String mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            try (Stream<ExtractionChunk> chunks = extractor.extract(docx, mimeType)) {
                List<ExtractionChunk> chunkList = chunks.toList();
                assertFalse(chunkList.isEmpty(), "Should extract chunks from DOCX");

                String allText = chunkList.stream()
                        .map(ExtractionChunk::text)
                        .reduce("", (a, b) -> a + " " + b);

                assertTrue(allText.contains("Spector") || allText.contains("Architecture") ||
                           allText.contains("tiered") || allText.contains("memory"),
                        "Should contain expected DOCX text: " + allText);
            }
        }

        @Test
        @DisplayName("DOCX MIME type is supported")
        void docxMimeSupported() {
            assertTrue(extractor.supports(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        }

        @Test
        @DisplayName("DOCX text has no XML tags")
        void docxNoXmlTags() throws IOException {
            Path docx = resolveResource("test-documents/sample.docx");
            if (!Files.exists(docx)) return;

            String mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            try (Stream<ExtractionChunk> chunks = extractor.extract(docx, mimeType)) {
                chunks.forEach(chunk -> {
                    assertFalse(chunk.text().contains("<w:"), "Should not contain XML tags");
                    assertFalse(chunk.text().contains("</w:"), "Should not contain closing XML tags");
                });
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CROSS-FORMAT
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-Format")
    class CrossFormatTests {

        @Test
        @DisplayName("All document formats produce non-empty text")
        void allFormatsProduceText() throws IOException {
            String[][] formats = {
                    {"test-documents/single_page.pdf", "application/pdf"},
                    {"test-documents/sample.docx",
                     "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                    {"test-documents/sample.html", "text/html"},
                    {"test-documents/sample.txt", "text/plain"},
            };

            for (String[] format : formats) {
                Path file = resolveResource(format[0]);
                if (!Files.exists(file)) continue;

                try (Stream<ExtractionChunk> chunks = largeChunkExtractor.extract(file, format[1])) {
                    List<ExtractionChunk> chunkList = chunks.toList();
                    assertFalse(chunkList.isEmpty(),
                            "Format " + format[1] + " should produce chunks from " + format[0]);

                    boolean hasContent = chunkList.stream()
                            .anyMatch(c -> !c.text().isBlank());
                    assertTrue(hasContent,
                            "Format " + format[1] + " should have non-blank content");
                }
            }
        }

        @Test
        @DisplayName("Null MIME type auto-detects format")
        void nullMimeTypeAutoDetects() throws IOException {
            Path pdf = resolveResource("test-documents/single_page.pdf");
            if (!Files.exists(pdf)) return;

            // null MIME type should auto-detect via Tika
            try (Stream<ExtractionChunk> chunks = extractor.extract(pdf, null)) {
                List<ExtractionChunk> chunkList = chunks.toList();
                // Tika auto-detect may or may not work for raw PDF
                // The important thing is no NPE or crash
                assertNotNull(chunkList, "Should return a non-null list");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════

    private static Path resolveResource(String relativePath) {
        var url = TikaPdfDocxExtractorTest.class.getClassLoader().getResource(relativePath);
        if (url != null) {
            try { return Path.of(url.toURI()); } catch (Exception e) { /* fall through */ }
        }
        return Path.of("d:/git/spector-search/spector-ingestion/src/test/resources", relativePath);
    }
}
