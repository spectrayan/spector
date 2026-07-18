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
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
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
public class PdfReaderTool implements AgentTool {

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
    public ToolCategory category() {
        return ToolCategory.DATA;
    }

    @Override
    public boolean isWriteTool() {
        return false;
    }

    @Override
    public Map<String, Object> parameterSchema() {
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
    public String execute(Map<String, Object> args) {
        var filePath = (String) args.get("file_path");
        if (filePath == null || filePath.isBlank()) {
            return "Error: Missing required argument: file_path";
        }

        Path path = Path.of(filePath).toAbsolutePath().normalize();
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
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            log.warn("[PdfReader] Encrypted PDF: {}", path);
            return "Error: PDF is encrypted or password-protected: " + filePath;
        } catch (Exception e) {
            // PDFBox surfaces encryption errors as various exception types
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

        try (var doc = PDDocument.load(path.toFile())) {
            int totalPages = doc.getNumberOfPages();
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

            var stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            String textContent = stripper.getText(doc).trim();

            var output = new StringBuilder();
            output.append("# Document: ").append(path.getFileName()).append("\n\n");

            // Metadata section
            output.append("## Metadata\n");
            PDDocumentInformation info = doc.getDocumentInformation();
            if (info != null) {
                String title = info.getTitle();
                if (title != null && !title.isBlank()) {
                    output.append("- **Title:** ").append(title).append("\n");
                }
                String author = info.getAuthor();
                if (author != null && !author.isBlank()) {
                    output.append("- **Author:** ").append(author).append("\n");
                }
            }
            if ("all".equalsIgnoreCase(pagesArg)) {
                output.append("- **Pages:** ").append(totalPages).append("\n");
            } else {
                output.append("- **Pages:** ").append(totalPages)
                        .append(" (showing ").append(startPage)
                        .append("-").append(endPage).append(")\n");
            }
            if (info != null && info.getCreationDate() != null) {
                String created = DateTimeFormatter.ISO_INSTANT.format(info.getCreationDate().getTime().toInstant());
                output.append("- **Created:** ").append(created).append("\n");
            }
            output.append("\n");

            // Content section
            output.append("## Content\n\n");
            if (textContent.isEmpty()) {
                output.append("_(No extractable text content)_\n");
            } else {
                output.append(textContent).append("\n");
            }

            // Table extraction
            if (extractTables) {
                appendTables(output, doc, startPage, endPage);
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
    }

    // Table extraction via Tabula

    private static void appendTables(StringBuilder output, PDDocument doc,
            int startPage, int endPage) {
        try (var extractor = new ObjectExtractor(doc)) {
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
}
