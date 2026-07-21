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
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;



import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * PDF reader tool — extracts text, metadata, and tables from PDF files.
 *
 * <p>
 * Uses Apache Tika for text/metadata extraction and Tabula-java for
 * structured table extraction. Supports page range selection to avoid
 * token explosion on large documents.
 * </p>
 */
@Component
public class PdfReaderTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(PdfReaderTool.class);

    /** Maximum output size to prevent token explosion in LLM context windows. */
    private static final int MAX_OUTPUT_CHARS = 100_000;

    /** Pattern for validating page range arguments (e.g., "3", "2-8", "all"). */
    private static final Pattern PAGE_RANGE_PATTERN = Pattern.compile(
            "^(?i:all|\\d+|\\d+-\\d+)$");

    @Override
    public String name() {
        return "pdf_reader";
    }

    @Override
    public String description() {
        return "Extract text, metadata, and tables from a PDF file. "
                + "Supports page ranges and optional table extraction.";
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.DATA;
    }

    @Override
    public boolean isWriteTool() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file_path", Map.of("type", "string",
                                "description", "Absolute path to the PDF file"),
                        "pages", Map.of("type", "string",
                                "description", "Page range: 'all', single page (e.g., '3'), "
                                         + "or range (e.g., '2-8'). Default: 'all'"),
                        "extract_tables", Map.of("type", "boolean",
                                "description", "Extract tables as Markdown (default: false)")),
                "required", List.of("file_path"));
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> args) throws Exception {
        var filePath = (String) args.get("file_path");
        if (filePath == null || filePath.isBlank()) {
            return "Error: Missing required argument: file_path";
        }

        Path path;
        try {
            path = PathSafety.validatePath(filePath);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
        if (!Files.exists(path)) {
            return "Error: File not found: " + filePath;
        }
        if (!filePath.toLowerCase().endsWith(".pdf")) {
            return "Error: Not a PDF file: " + filePath;
        }

        String pagesArg = args.get("pages") != null
                ? args.get("pages").toString().trim()
                : "all";
        boolean extractTables = Boolean.parseBoolean(
                String.valueOf(args.getOrDefault("extract_tables", "false")));

        // Validate page range format before opening the file
        if (!PAGE_RANGE_PATTERN.matcher(pagesArg).matches()) {
            return "Error: Invalid page range: " + pagesArg;
        }

        try {
            return extractPdf(path, pagesArg, extractTables);
        } catch (EncryptedDocumentException e) {
            log.warn("[PdfReader] Encrypted PDF: {}", path);
            return "Error: PDF is encrypted or password-protected: " + filePath;
        } catch (Exception e) {
            // PDFBox and Tika surface encryption errors as various exception types
            if (isEncryptionError(e)) {
                log.warn("[PdfReader] Encrypted PDF: {}", path);
                return "Error: PDF is encrypted or password-protected: " + filePath;
            }
            log.warn("[PdfReader] Failed to read {}: {}", path, e.getMessage());
            return "Error reading PDF: " + e.getMessage();
        }
    }

    /**
     * Checks whether an exception indicates an encrypted or password-protected PDF.
     *
     * <p>PDFBox and Tika surface encryption errors as various exception types
     * with different messages — this centralizes the detection heuristic.</p>
     */
    private static boolean isEncryptionError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("decrypt") || msg.contains("password")
                || msg.contains("encrypt"))) {
            return true;
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            return causeMsg != null && (causeMsg.contains("decrypt")
                    || causeMsg.contains("password") || causeMsg.contains("encrypt"));
        }
        return false;
    }

    // ── Core extraction ───────────────────────────────────────────

    private String extractPdf(Path path, String pagesArg, boolean extractTables)
            throws Exception {

        var metadata = new Metadata();
        var handler = new BodyContentHandler(MAX_OUTPUT_CHARS);
        var parser = new AutoDetectParser();
        var context = new ParseContext();

        int totalPages = countPages(path);
        int startPage = 1;
        int endPage = totalPages;

        if (!"all".equalsIgnoreCase(pagesArg)) {
            int[] range = parsePageRange(pagesArg);
            startPage = range[0];
            endPage = range[1];

            if (startPage > totalPages || endPage > totalPages) {
                return "Error: Page range %s exceeds document length (%d pages)"
                        .formatted(pagesArg, totalPages);
            }
        }

        try (InputStream stream = Files.newInputStream(path)) {
            parser.parse(stream, handler, metadata, context);
        }

        String textContent = handler.toString().trim();

        // If page range specified, extract only the relevant portion
        if (!"all".equalsIgnoreCase(pagesArg) && totalPages > 1) {
            textContent = extractPageRange(textContent, startPage, endPage, totalPages);
        }

        var output = new StringBuilder();
        output.append("# Document: ").append(path.getFileName()).append("\n\n");

        // Metadata section
        appendMetadata(output, metadata, totalPages, startPage, endPage, pagesArg);

        // Content section
        output.append("## Content\n\n");
        if (textContent.isEmpty()) {
            output.append("_(No extractable text content)_\n");
        } else {
            output.append(textContent).append("\n");
        }

        // Table extraction
        if (extractTables) {
            appendTables(output, path, startPage, endPage);
        }

        String result = output.toString();
        if (result.length() > MAX_OUTPUT_CHARS) {
            result = result.substring(0, MAX_OUTPUT_CHARS)
                    + "\n\n_(Output truncated at " + MAX_OUTPUT_CHARS + " characters)_";
        }

        log.debug("[PdfReader] Extracted {} chars from {} (pages {}-{} of {})",
                result.length(), path.getFileName(), startPage, endPage, totalPages);
        return result;
    }

    // ── Metadata formatting ───────────────────────────────────────

    private static void appendMetadata(StringBuilder output, Metadata metadata,
            int totalPages, int startPage, int endPage,
            String pagesArg) {
        output.append("## Metadata\n");
        String title = metadata.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) {
            output.append("- **Title:** ").append(title).append("\n");
        }
        String author = metadata.get(TikaCoreProperties.CREATOR);
        if (author != null && !author.isBlank()) {
            output.append("- **Author:** ").append(author).append("\n");
        }
        if ("all".equalsIgnoreCase(pagesArg)) {
            output.append("- **Pages:** ").append(totalPages).append("\n");
        } else {
            output.append("- **Pages:** ").append(totalPages)
                    .append(" (showing ").append(startPage)
                    .append("-").append(endPage).append(")\n");
        }
        String created = metadata.get(TikaCoreProperties.CREATED);
        if (created != null && !created.isBlank()) {
            output.append("- **Created:** ").append(created).append("\n");
        }
        output.append("\n");
    }

    // Table extraction via Tabula

    private static void appendTables(StringBuilder output, Path path,
            int startPage, int endPage) {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(path.toFile());
             var extractor = new ObjectExtractor(doc)) {
            var algorithm = new SpreadsheetExtractionAlgorithm();
            boolean hasTables = false;
            int tableCount = 0;

            for (int pageNum = startPage; pageNum <= endPage; pageNum++) {
                Page page = extractor.extract(pageNum);
                List<Table> tables = algorithm.extract(page);

                for (Table table : tables) {
                    if (table.getRowCount() == 0)
                        continue;
                    if (!hasTables) {
                        output.append("\n## Tables\n");
                        hasTables = true;
                    }
                    tableCount++;
                    output.append("\n### Table ").append(tableCount)
                            .append(" (Page ").append(pageNum).append(")\n\n");
                    appendMarkdownTable(output, table);
                }
            }
        } catch (Exception e) {
            output.append("\n_(Table extraction failed: ").append(e.getMessage()).append(")_\n");
        }
    }

    private static void appendMarkdownTable(StringBuilder output, Table table) {
        List<? extends List<RectangularTextContainer>> rows = table.getRows();
        if (rows.isEmpty())
            return;

        // Header row
        var header = rows.getFirst();
        output.append("|");
        for (var cell : header) {
            output.append(" ").append(cell.getText().replace("|", "\\|")).append(" |");
        }
        output.append("\n|");
        for (int i = 0; i < header.size(); i++) {
            output.append("---|");
        }
        output.append("\n");

        // Data rows
        for (int r = 1; r < rows.size(); r++) {
            output.append("|");
            for (var cell : rows.get(r)) {
                output.append(" ").append(cell.getText().replace("|", "\\|")).append(" |");
            }
            output.append("\n");
        }
    }

    // ── Page counting and range parsing ───────────────────────────

    private static int countPages(Path path) throws Exception {
        try (var doc = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Parses a page range string into a [start, end] pair.
     *
     * @param pagesArg validated page range string (e.g., "3", "2-8")
     * @return two-element array [startPage, endPage], 1-indexed
     */
    static int[] parsePageRange(String pagesArg) {
        if (pagesArg.contains("-")) {
            String[] parts = pagesArg.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            if (start < 1 || end < start) {
                throw new IllegalArgumentException(
                        "Invalid range: start=%d, end=%d".formatted(start, end));
            }
            return new int[] { start, end };
        }
        int page = Integer.parseInt(pagesArg);
        if (page < 1) {
            throw new IllegalArgumentException("Page must be >= 1: " + page);
        }
        return new int[] { page, page };
    }

    /**
     * Extracts an approximate page range from the full extracted text.
     *
     * <p>
     * Tika extracts all text at once; this method splits by estimated
     * page boundaries using form-feed characters or proportional splitting.
     * </p>
     */
    private static String extractPageRange(String fullText, int startPage,
            int endPage, int totalPages) {
        // Tika inserts form-feed characters between pages when available
        String[] pages = fullText.split("\f");
        if (pages.length >= totalPages) {
            // Form-feed based splitting — accurate
            var sb = new StringBuilder();
            for (int i = startPage - 1; i < Math.min(endPage, pages.length); i++) {
                if (!sb.isEmpty())
                    sb.append("\n\n");
                sb.append(pages[i].trim());
            }
            return sb.toString();
        }

        // Fallback: proportional splitting for PDFs without form-feeds
        int totalLen = fullText.length();
        int charsPerPage = totalLen / totalPages;
        int startIdx = (startPage - 1) * charsPerPage;
        int endIdx = Math.min(endPage * charsPerPage, totalLen);
        return fullText.substring(startIdx, endIdx).trim();
    }
}
