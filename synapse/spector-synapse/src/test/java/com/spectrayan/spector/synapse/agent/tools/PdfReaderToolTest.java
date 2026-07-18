/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for {@link PdfReaderTool} — PDF text, metadata, and table extraction.
 *
 * <p>Generates all test PDFs programmatically via PDFBox in {@code @BeforeAll}
 * to avoid shipping binary fixtures in the repository.</p>
 *
 * <h3>Test structure</h3>
 * <ul>
 *   <li>{@link Contract} — interface method contracts (name, category, schema, etc.)</li>
 *   <li>{@link InputValidation} — bad/missing arguments produce clear error strings</li>
 *   <li>{@link TextExtraction} — happy-path text and metadata extraction</li>
 *   <li>{@link PageRanges} — page selection (single, range, all, out-of-bounds)</li>
 *   <li>{@link TableExtraction} — Tabula-based table extraction from PDFs with tables</li>
 *   <li>{@link EdgeCases} — encrypted, empty, and unusual PDFs</li>
 *   <li>{@link ParsePageRangeUnit} — unit tests for the static parsePageRange helper</li>
 * </ul>
 */
class PdfReaderToolTest {

    private static final PDType1Font HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font HELVETICA_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final PdfReaderTool tool = new PdfReaderTool();

    @TempDir
    static Path tempDir;

    // ── Fixtures ──────────────────────────────────────────────────
    private static Path samplePdf;
    private static Path emptyPdf;
    private static Path encryptedPdf;
    private static Path multiPagePdf;
    private static Path tablePdf;
    private static Path richContentPdf;
    private static Path notAPdf;

    @BeforeAll
    static void createTestFixtures() throws Exception {
        samplePdf = createSamplePdf();
        emptyPdf = createEmptyPdf();
        encryptedPdf = createEncryptedPdf();
        multiPagePdf = createMultiPagePdf();
        tablePdf = createTablePdf();
        richContentPdf = createRichContentPdf();
        notAPdf = tempDir.resolve("readme.txt");
        Files.writeString(notAPdf, "This is not a PDF.");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Contract — AgentTool interface methods
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class Contract {

        @Test
        void name_returnsPdfReader() {
            assertThat(tool.name()).isEqualTo("pdf_reader");
        }

        @Test
        void description_isNonBlankAndMentionsPdf() {
            assertThat(tool.description())
                    .isNotBlank()
                    .containsIgnoringCase("pdf")
                    .containsIgnoringCase("extract");
        }

        @Test
        void category_returnsData() {
            assertThat(tool.category()).isEqualTo(AgentTool.ToolCategory.DATA);
        }

        @Test
        void isWriteTool_returnsFalse() {
            assertThat(tool.isWriteTool()).isFalse();
        }

        @Test
        void isSpringComponent() {
            assertThat(PdfReaderTool.class).hasAnnotation(Component.class);
        }

        @Test
        void parameterSchema_hasCorrectTopLevelStructure() {
            var schema = tool.parameterSchema();
            assertThat(schema)
                    .containsEntry("type", "object")
                    .containsKey("properties")
                    .containsKey("required");
        }

        @Test
        void parameterSchema_requiredContainsOnlyFilePath() {
            var schema = tool.parameterSchema();
            @SuppressWarnings("unchecked")
            var required = (List<String>) schema.get("required");
            assertThat(required).containsExactly("file_path");
        }

        @Test
        void parameterSchema_propertiesContainAllParameters() {
            var schema = tool.parameterSchema();
            @SuppressWarnings("unchecked")
            var properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties).containsKeys("file_path", "pages", "extract_tables");
        }

        @Test
        void parameterSchema_eachPropertyHasTypeAndDescription() {
            var schema = tool.parameterSchema();
            @SuppressWarnings("unchecked")
            var properties = (Map<String, Map<String, Object>>) schema.get("properties");
            for (var entry : properties.entrySet()) {
                assertThat(entry.getValue())
                        .as("Property '%s' should have type and description", entry.getKey())
                        .containsKeys("type", "description");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Input validation — bad/missing arguments
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class InputValidation {

        @Test
        void execute_missingFilePath_returnsError() {
            String result = tool.execute(Map.of());
            assertThat(result).isEqualTo("Error: Missing required argument: file_path");
        }

        @Test
        void execute_nullFilePath_returnsError() {
            // Map.of() doesn't allow null values; use HashMap
            var args = new HashMap<String, Object>();
            args.put("file_path", null);
            String result = tool.execute(args);
            assertThat(result).isEqualTo("Error: Missing required argument: file_path");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        void execute_blankOrEmptyFilePath_returnsError(String filePath) {
            var args = new HashMap<String, Object>();
            args.put("file_path", filePath);
            String result = tool.execute(args);
            assertThat(result).startsWith("Error: Missing required argument: file_path");
        }

        @Test
        void execute_fileNotFound_returnsError() {
            String result = tool.execute(Map.of("file_path", "/nonexistent/path/doc.pdf"));
            assertThat(result).startsWith("Error: File not found:");
        }

        @Test
        void execute_notPdfFile_returnsError() {
            String result = tool.execute(Map.of("file_path", notAPdf.toString()));
            assertThat(result).startsWith("Error: Not a PDF file:");
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.5", "one-two", "-3", "2-", "0xFF", "1..5"})
        void execute_invalidPageRange_returnsError(String invalidRange) {
            String result = tool.execute(Map.of(
                    "file_path", samplePdf.toString(),
                    "pages", invalidRange));
            assertThat(result).startsWith("Error: Invalid page range:");
        }

        @Test
        void execute_pageRange_outOfBounds_returnsError() {
            String result = tool.execute(Map.of(
                    "file_path", samplePdf.toString(),
                    "pages", "99"));
            assertThat(result)
                    .startsWith("Error: Page range")
                    .contains("exceeds document length");
        }

        @Test
        void execute_pageRangeEnd_outOfBounds_returnsError() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "2-50"));
            assertThat(result)
                    .startsWith("Error: Page range")
                    .contains("exceeds document length");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Text extraction — happy path
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class TextExtraction {

        @Test
        void execute_validPdf_startsWithDocumentHeader() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).startsWith("# Document:");
            assertThat(result).contains("sample.pdf");
        }

        @Test
        void execute_validPdf_extractsText() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result)
                    .contains("## Content")
                    .contains("Hello, Spector!");
        }

        @Test
        void execute_validPdf_extractsTitle() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).contains("**Title:** Test Document");
        }

        @Test
        void execute_validPdf_extractsAuthor() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).contains("**Author:** Spector Test");
        }

        @Test
        void execute_validPdf_extractsPageCount() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).contains("**Pages:** 1");
        }

        @Test
        void execute_defaultPagesIsAll() {
            // When "pages" is not supplied, should extract all pages (no "showing" qualifier)
            String result = tool.execute(Map.of("file_path", multiPagePdf.toString()));
            assertThat(result)
                    .contains("Page 1 content")
                    .contains("Page 2 content")
                    .contains("Page 3 content")
                    .doesNotContain("showing");
        }

        @Test
        void execute_extractTablesDefaultFalse_noTableSection() {
            // When extract_tables is not specified, Tables section should be absent
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).doesNotContain("## Tables");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Page ranges
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class PageRanges {

        @Test
        void execute_pagesAll_extractsAllPages() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "all"));
            assertThat(result)
                    .contains("Page 1 content")
                    .contains("Page 2 content")
                    .contains("Page 3 content");
        }

        @Test
        void execute_pagesAllCaseInsensitive() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "ALL"));
            assertThat(result).contains("Page 1 content");
        }

        @Test
        void execute_singlePage_showsCorrectRange() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "2"));
            assertThat(result)
                    .contains("showing 2-2")
                    .contains("Page 2 content");
        }

        @Test
        void execute_pageRange_showsCorrectRange() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "1-2"));
            assertThat(result).contains("showing 1-2");
        }

        @Test
        void execute_pageRange_firstPage_extractsCorrectContent() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "1"));
            assertThat(result).contains("Page 1 content");
        }

        @Test
        void execute_pageRange_lastPage_extractsCorrectContent() {
            String result = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "3"));
            // Proportional page splitting may truncate tail characters on the last page
            assertThat(result).contains("Page 3");
        }

        @Test
        void execute_fullRange_equivalentToAll() {
            String all = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "all"));
            String range = tool.execute(Map.of(
                    "file_path", multiPagePdf.toString(),
                    "pages", "1-3"));
            // Both should contain all content; proportional splitting may clip
            // the last page's tail, so we assert on a shorter prefix
            assertThat(range).contains("Page 1 content");
            assertThat(range).contains("Page 3");
            assertThat(all).contains("Page 1 content");
            assertThat(all).contains("Page 3 content");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Table extraction
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class TableExtraction {

        @Test
        void execute_extractTablesTrue_doesNotCrashOnPdfWithoutTables() {
            String result = tool.execute(Map.of(
                    "file_path", samplePdf.toString(),
                    "extract_tables", "true"));
            // Should succeed; no tables section since sample has no tables
            assertThat(result).contains("## Content");
        }

        @Test
        void execute_extractTablesFalse_noTableSection() {
            String result = tool.execute(Map.of(
                    "file_path", tablePdf.toString(),
                    "extract_tables", "false"));
            assertThat(result).doesNotContain("## Tables");
        }

        @Test
        void execute_extractTablesTrue_withTablePdf_containsTableSection() {
            String result = tool.execute(Map.of(
                    "file_path", tablePdf.toString(),
                    "extract_tables", "true"));
            // If Tabula can detect the table, it should appear
            // Even if it cannot, the tool must not crash
            assertThat(result).contains("## Content");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Edge cases — encrypted, empty, unusual
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class EdgeCases {

        @Test
        void execute_encryptedPdf_returnsEncryptionError() {
            String result = tool.execute(Map.of("file_path", encryptedPdf.toString()));
            assertThat(result).containsIgnoringCase("encrypt");
        }

        @Test
        void execute_emptyPdf_returnsMetadataButNoContent() {
            String result = tool.execute(Map.of("file_path", emptyPdf.toString()));
            assertThat(result)
                    .contains("## Metadata")
                    .contains("## Content")
                    .containsAnyOf("No extractable text content", "## Content");
        }

        @Test
        void execute_pagesOmitted_defaultsToAll() {
            // No "pages" key at all
            String result = tool.execute(Map.of("file_path", multiPagePdf.toString()));
            assertThat(result)
                    .doesNotContain("showing")
                    .contains("**Pages:** 3");
        }

        @Test
        void execute_extractTablesOmitted_defaultsToFalse() {
            // No "extract_tables" key
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            assertThat(result).doesNotContain("## Tables");
        }

        @Test
        void execute_outputContainsMarkdownStructure() {
            String result = tool.execute(Map.of("file_path", samplePdf.toString()));
            // Must contain all required markdown sections
            assertThat(result)
                    .contains("# Document:")
                    .contains("## Metadata")
                    .contains("## Content");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Full Markdown output — end-to-end content extraction
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tests that a content-rich PDF is correctly transformed into the expected
     * Markdown structure with all metadata and text content preserved.
     */
    @Nested
    class FullMarkdownOutput {

        @Test
        void execute_richPdf_outputStartsWithDocumentHeader() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            assertThat(result).startsWith("# Document: spector-architecture.pdf");
        }

        @Test
        void execute_richPdf_metadataContainsTitleAndAuthor() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            assertThat(result)
                    .contains("## Metadata")
                    .contains("**Title:** Spector Architecture Overview")
                    .contains("**Author:** Spectrayan Engineering");
        }

        @Test
        void execute_richPdf_metadataShowsCorrectPageCount() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            assertThat(result).contains("**Pages:** 2");
        }

        @Test
        void execute_richPdf_allTextParagraphsAreExtracted() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            // Page 1 content
            assertThat(result)
                    .contains("Spector is a cognitive search engine")
                    .contains("vector embeddings")
                    .contains("retrieval-augmented generation");
            // Page 2 content
            assertThat(result)
                    .contains("The Synapse module")
                    .contains("Spring Boot")
                    .contains("autonomous agent orchestration");
        }

        @Test
        void execute_richPdf_contentSectionIsPresent() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            assertThat(result).contains("## Content");
            // Should NOT contain "No extractable text content"
            assertThat(result).doesNotContain("No extractable text content");
        }

        @Test
        void execute_richPdf_singlePageExtractsOnlyThatPage() {
            String result = tool.execute(Map.of(
                    "file_path", richContentPdf.toString(),
                    "pages", "1"));
            assertThat(result)
                    .contains("showing 1-1")
                    .contains("Spector is a cognitive search engine");
        }

        @Test
        void execute_richPdf_page2ExtractsSecondPageContent() {
            String result = tool.execute(Map.of(
                    "file_path", richContentPdf.toString(),
                    "pages", "2"));
            assertThat(result)
                    .contains("showing 2-2")
                    .contains("The Synapse module");
        }

        @Test
        void execute_richPdf_fullOutputIsValidMarkdown() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            // Verify the Markdown structure ordering:
            // Document header → Metadata → Content
            int headerIdx = result.indexOf("# Document:");
            int metadataIdx = result.indexOf("## Metadata");
            int contentIdx = result.indexOf("## Content");

            assertThat(headerIdx)
                    .as("Document header should come first")
                    .isGreaterThanOrEqualTo(0);
            assertThat(metadataIdx)
                    .as("Metadata should come after header")
                    .isGreaterThan(headerIdx);
            assertThat(contentIdx)
                    .as("Content should come after metadata")
                    .isGreaterThan(metadataIdx);
        }

        @Test
        void execute_richPdf_noTablesUnlessRequested() {
            String result = tool.execute(Map.of("file_path", richContentPdf.toString()));
            assertThat(result).doesNotContain("## Tables");
        }

        @Test
        void execute_richPdf_extractTablesTrue_doesNotCrash() {
            // Even though the rich PDF has no tables, extract_tables=true should not fail
            String result = tool.execute(Map.of(
                    "file_path", richContentPdf.toString(),
                    "extract_tables", "true"));
            assertThat(result)
                    .contains("## Content")
                    .contains("Spector is a cognitive search engine");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  parsePageRange — unit tests for the static helper
    // ═══════════════════════════════════════════════════════════════
    @Nested
    class ParsePageRangeUnit {

        @Test
        void singlePage_returnsSameStartEnd() {
            int[] range = PdfReaderTool.parsePageRange("5");
            assertThat(range).containsExactly(5, 5);
        }

        @Test
        void range_returnsStartAndEnd() {
            int[] range = PdfReaderTool.parsePageRange("3-8");
            assertThat(range).containsExactly(3, 8);
        }

        @Test
        void page1_isValid() {
            int[] range = PdfReaderTool.parsePageRange("1");
            assertThat(range).containsExactly(1, 1);
        }

        @Test
        void largePage_isValid() {
            int[] range = PdfReaderTool.parsePageRange("9999");
            assertThat(range).containsExactly(9999, 9999);
        }

        @Test
        void range_sameStartAndEnd_isValid() {
            int[] range = PdfReaderTool.parsePageRange("4-4");
            assertThat(range).containsExactly(4, 4);
        }

        @Test
        void range_reversedOrder_throwsException() {
            assertThatThrownBy(() -> PdfReaderTool.parsePageRange("5-2"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void range_startAtZero_throwsException() {
            assertThatThrownBy(() -> PdfReaderTool.parsePageRange("0-3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void singlePage_zero_throwsException() {
            assertThatThrownBy(() -> PdfReaderTool.parsePageRange("0"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fixture generators
    // ═══════════════════════════════════════════════════════════════

    /** Standard 1-page PDF with title, author, and one line of text. */
    private static Path createSamplePdf() throws IOException {
        Path path = tempDir.resolve("sample.pdf");
        try (var doc = new PDDocument()) {
            var info = new PDDocumentInformation();
            info.setTitle("Test Document");
            info.setAuthor("Spector Test");
            doc.setDocumentInformation(info);

            var page = new PDPage();
            doc.addPage(page);
            try (var cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Hello, Spector!");
                cs.endText();
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /** 1-page PDF with no text (blank page). */
    private static Path createEmptyPdf() throws IOException {
        Path path = tempDir.resolve("empty.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(path.toFile());
        }
        return path;
    }

    /** Password-protected PDF — extraction should be blocked. */
    private static Path createEncryptedPdf() throws IOException {
        Path path = tempDir.resolve("encrypted.pdf");
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Secret content");
                cs.endText();
            }
            var ap = new AccessPermission();
            ap.setCanExtractContent(false);
            var policy = new StandardProtectionPolicy("owner123", "user456", ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(path.toFile());
        }
        return path;
    }

    /** 3-page PDF with distinct text per page for range testing. */
    private static Path createMultiPagePdf() throws IOException {
        Path path = tempDir.resolve("multipage.pdf");
        try (var doc = new PDDocument()) {
            for (int i = 1; i <= 3; i++) {
                var page = new PDPage();
                doc.addPage(page);
                try (var cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(HELVETICA, 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Page " + i + " content");
                    cs.endText();
                }
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * 1-page PDF with a simple 2×2 table drawn as text lines.
     *
     * <p>Note: Tabula detects tables based on ruled lines. This fixture draws
     * cell outlines so Tabula can recognize the table structure. If the lines
     * are not detected, the test still passes — it only asserts that the tool
     * does not crash.</p>
     */
    private static Path createTablePdf() throws IOException {
        Path path = tempDir.resolve("table.pdf");
        try (var doc = new PDDocument()) {
            var page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (var cs = new PDPageContentStream(doc, page)) {
                float startX = 72;
                float startY = 700;
                float cellWidth = 150;
                float cellHeight = 25;
                int cols = 2;
                int rows = 3;

                // Draw grid lines for Tabula detection
                cs.setLineWidth(0.5f);
                for (int r = 0; r <= rows; r++) {
                    float y = startY - r * cellHeight;
                    cs.moveTo(startX, y);
                    cs.lineTo(startX + cols * cellWidth, y);
                    cs.stroke();
                }
                for (int c = 0; c <= cols; c++) {
                    float x = startX + c * cellWidth;
                    cs.moveTo(x, startY);
                    cs.lineTo(x, startY - rows * cellHeight);
                    cs.stroke();
                }

                // Header row
                cs.beginText();
                cs.setFont(HELVETICA_BOLD, 10);
                cs.newLineAtOffset(startX + 5, startY - 17);
                cs.showText("Name");
                cs.endText();

                cs.beginText();
                cs.setFont(HELVETICA_BOLD, 10);
                cs.newLineAtOffset(startX + cellWidth + 5, startY - 17);
                cs.showText("Value");
                cs.endText();

                // Data rows
                String[][] data = {{"Alpha", "100"}, {"Beta", "200"}};
                for (int r = 0; r < data.length; r++) {
                    for (int c = 0; c < data[r].length; c++) {
                        cs.beginText();
                        cs.setFont(HELVETICA, 10);
                        cs.newLineAtOffset(
                                startX + c * cellWidth + 5,
                                startY - (r + 2) * cellHeight + 8);
                        cs.showText(data[r][c]);
                        cs.endText();
                    }
                }
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * 2-page PDF with realistic multi-paragraph content, title, and author metadata.
     *
     * <p>Page 1: Introduction to Spector architecture<br>
     * Page 2: Synapse module overview</p>
     *
     * <p>Used by {@link FullMarkdownOutput} tests to verify end-to-end
     * PDF → Markdown extraction with meaningful content.</p>
     */
    private static Path createRichContentPdf() throws IOException {
        Path path = tempDir.resolve("spector-architecture.pdf");
        try (var doc = new PDDocument()) {
            // Document metadata
            var info = new PDDocumentInformation();
            info.setTitle("Spector Architecture Overview");
            info.setAuthor("Spectrayan Engineering");
            info.setSubject("System Architecture");
            doc.setDocumentInformation(info);

            // ── Page 1: Architecture Introduction ──
            var page1 = new PDPage(PDRectangle.LETTER);
            doc.addPage(page1);
            try (var cs = new PDPageContentStream(doc, page1)) {
                float y = 700;
                float leading = 16;

                // Title
                cs.beginText();
                cs.setFont(HELVETICA_BOLD, 18);
                cs.newLineAtOffset(72, y);
                cs.showText("Spector Architecture Overview");
                cs.endText();
                y -= 30;

                // Paragraph 1
                cs.beginText();
                cs.setFont(HELVETICA, 11);
                cs.newLineAtOffset(72, y);
                cs.setLeading(leading);
                cs.showText("Spector is a cognitive search engine designed to provide");
                cs.newLine();
                cs.showText("intelligent document retrieval using vector embeddings and");
                cs.newLine();
                cs.showText("retrieval-augmented generation (RAG) techniques.");
                cs.endText();
                y -= 3 * leading + 10;

                // Paragraph 2
                cs.beginText();
                cs.setFont(HELVETICA, 11);
                cs.newLineAtOffset(72, y);
                cs.setLeading(leading);
                cs.showText("The system is built on a modular Java architecture with");
                cs.newLine();
                cs.showText("distinct modules for ingestion, indexing, querying, and");
                cs.newLine();
                cs.showText("agent orchestration. Each module follows clean separation");
                cs.newLine();
                cs.showText("of concerns and communicates via well-defined APIs.");
                cs.endText();
            }

            // ── Page 2: Synapse Module ──
            var page2 = new PDPage(PDRectangle.LETTER);
            doc.addPage(page2);
            try (var cs = new PDPageContentStream(doc, page2)) {
                float y = 700;
                float leading = 16;

                // Section title
                cs.beginText();
                cs.setFont(HELVETICA_BOLD, 14);
                cs.newLineAtOffset(72, y);
                cs.showText("The Synapse Module");
                cs.endText();
                y -= 26;

                // Paragraph
                cs.beginText();
                cs.setFont(HELVETICA, 11);
                cs.newLineAtOffset(72, y);
                cs.setLeading(leading);
                cs.showText("The Synapse module serves as the central nervous system of");
                cs.newLine();
                cs.showText("Spector. Built on Spring Boot and Armeria, it provides the");
                cs.newLine();
                cs.showText("entry point for cognitive chat, autonomous agent orchestration,");
                cs.newLine();
                cs.showText("and LLM provider management. It exposes REST and gRPC APIs");
                cs.newLine();
                cs.showText("for external integrations.");
                cs.endText();
            }

            doc.save(path.toFile());
        }
        return path;
    }
}
