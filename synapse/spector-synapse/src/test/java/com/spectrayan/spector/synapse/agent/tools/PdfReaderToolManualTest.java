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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual verification test — run against any custom PDF file on disk.
 *
 * <p>Usage:
 * <pre>{@code
 * mvn test -pl spector-synapse -Psynapse \
 *     -Dtest=PdfReaderToolManualTest \
 *     -Dpdf.path="C:\path\to\your\file.pdf"
 * }</pre>
 *
 * <p>The test only runs when {@code -Dpdf.path} is provided.
 * It prints the full extracted Markdown to stdout so you can visually
 * verify the extraction quality.</p>
 */
class PdfReaderToolManualTest {

    private static final PdfReaderTool tool = new PdfReaderTool();

    /**
     * Extracts ALL pages from the custom PDF and prints the Markdown output.
     * Only runs when {@code -Dpdf.path} is set.
     */
    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    void extractFullDocument() {
        String pdfPath = System.getProperty("pdf.path");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  PdfReaderTool — Full Extraction");
        System.out.println("  Source: " + pdfPath);
        System.out.println("═══════════════════════════════════════════════════\n");

        String result = tool.execute(Map.of("file_path", pdfPath));

        System.out.println(result);
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  Output length: " + result.length() + " characters");
        System.out.println("═══════════════════════════════════════════════════");

        // Basic sanity checks — should not be an error
        assertThat(result)
                .as("Output should not be an error")
                .doesNotStartWith("Error:");
        assertThat(result)
                .as("Output should contain the document header")
                .contains("# Document:");
        assertThat(result)
                .as("Output should contain metadata section")
                .contains("## Metadata");
        assertThat(result)
                .as("Output should contain content section")
                .contains("## Content");
    }

    /**
     * Extracts a single page from the custom PDF.
     * Only runs when both {@code -Dpdf.path} and {@code -Dpdf.page} are set.
     */
    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    @EnabledIfSystemProperty(named = "pdf.page", matches = "\\d+")
    void extractSinglePage() {
        String pdfPath = System.getProperty("pdf.path");
        String page = System.getProperty("pdf.page");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  PdfReaderTool — Page " + page + " Extraction");
        System.out.println("  Source: " + pdfPath);
        System.out.println("═══════════════════════════════════════════════════\n");

        String result = tool.execute(Map.of(
                "file_path", pdfPath,
                "pages", page));

        System.out.println(result);
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  Output length: " + result.length() + " characters");
        System.out.println("═══════════════════════════════════════════════════");

        assertThat(result).doesNotStartWith("Error:");
        assertThat(result).contains("showing " + page + "-" + page);
    }

    /**
     * Extracts ALL pages + tables from the custom PDF.
     * Only runs when {@code -Dpdf.path} and {@code -Dpdf.tables=true} are set.
     */
    @Test
    @EnabledIfSystemProperty(named = "pdf.path", matches = ".+")
    @EnabledIfSystemProperty(named = "pdf.tables", matches = "true")
    void extractWithTables() {
        String pdfPath = System.getProperty("pdf.path");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  PdfReaderTool — Full Extraction + Tables");
        System.out.println("  Source: " + pdfPath);
        System.out.println("═══════════════════════════════════════════════════\n");

        String result = tool.execute(Map.of(
                "file_path", pdfPath,
                "extract_tables", "true"));

        System.out.println(result);
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  Output length: " + result.length() + " characters");
        System.out.println("  Tables section: " + (result.contains("## Tables") ? "FOUND" : "none detected"));
        System.out.println("═══════════════════════════════════════════════════");

        assertThat(result).doesNotStartWith("Error:");
    }
}
