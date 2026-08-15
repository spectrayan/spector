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
package com.spectrayan.spector.bench.cognitive;

import com.spectrayan.spector.commons.template.TemplateEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates results from all benchmark runners into a unified comparison report.
 *
 * <h3>Input Files</h3>
 * <ul>
 *   <li>{@code profile-sweep-matrix.tsv} — from CognitiveProfileSweepRunner</li>
 *   <li>{@code retrieval-stack-matrix.tsv} — from RetrievalStackMatrixRunner</li>
 *   <li>{@code ablation-results.tsv} — from SubsystemAblationRunner</li>
 *   <li>{@code scale-performance.tsv} — from ScalePerformanceRunner</li>
 *   <li>{@code benchmark-report.md} — from CognitiveBenchmarkHarness</li>
 * </ul>
 *
 * <h3>Output</h3>
 * <p>A comprehensive Markdown report with before/after comparisons, key findings,
 * and recommendations.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   java ... ComparisonReportGenerator <resultsDir> <outputFile>
 * }</pre>
 */
public final class ComparisonReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ComparisonReportGenerator.class);

    private final Path resultsDir;
    private final Path outputFile;
    private final TemplateEngine templateEngine;

    public ComparisonReportGenerator(Path resultsDir, Path outputFile) {
        this(resultsDir, outputFile, TemplateEngine.createDefault());
    }

    public ComparisonReportGenerator(Path resultsDir, Path outputFile, TemplateEngine templateEngine) {
        this.resultsDir = resultsDir;
        this.outputFile = outputFile;
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: ComparisonReportGenerator <resultsDir> <outputFile>");
            System.exit(1);
            return;
        }
        new ComparisonReportGenerator(Path.of(args[0]), Path.of(args[1])).run();
    }

    /**
     * Generates the unified comparison report.
     */
    public void run() {
        log.info("═══ Generating Comparison Report ═══");

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Object> model = new HashMap<>();
        model.put("generatedAt", now);

        // Section 2: Cognitive vs. Baseline
        model.put("cognitiveVsBaselineContent", readSectionFromFile("benchmark-report.md", true));

        // Section 3: Profile Sweep
        model.put("profileSweepDescription",
                "The table below shows nDCG per query × profile combination. The best profile for each query is marked.\n");
        model.put("profileSweepTable", readSectionFromTsv("profile-sweep-matrix.tsv"));

        // Section 4: Retrieval Stack Matrix
        model.put("retrievalStackDescription",
                "Compares all TextSearchMode values for nDCG and latency.\n");
        model.put("retrievalStackTable", readSectionFromTsv("retrieval-stack-matrix.tsv"));

        // Section 5: Ablation Study
        model.put("ablationDescription",
                "Each row shows the effect of disabling one subsystem. Negative Δ means the subsystem contributed positively.\n");
        model.put("ablationTable", readSectionFromTsv("ablation-results.tsv"));

        // Section 6: Scale Performance
        model.put("scalePerformanceDescription",
                "Latency and throughput at different corpus sizes and concurrency levels.\n");
        model.put("scalePerformanceTable", readSectionFromTsv("scale-performance.tsv"));

        String renderedReport = templateEngine.render("reports/comparison-report", model);

        try {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, renderedReport);
            log.info("Comparison report written to {}", outputFile);
        } catch (IOException e) {
            log.error("Failed to write report: {}", e.getMessage(), e);
        }
    }

    /**
     * Reads a section from a markdown file if it exists.
     */
    private String readSectionFromFile(String filename, boolean fullContent) {
        Path file = resultsDir.resolve(filename);
        if (Files.exists(file)) {
            try {
                String content = Files.readString(file);
                if (fullContent) {
                    return content + "\n";
                } else {
                    int endIdx = content.indexOf("\n## ", 10);
                    if (endIdx > 0) {
                        return content.substring(0, endIdx) + "\n";
                    } else {
                        return content + "\n";
                    }
                }
            } catch (IOException e) {
                return "*File could not be read: " + e.getMessage() + "*\n";
            }
        } else {
            return "*No data available — run CognitiveBenchmarkHarness first.*\n";
        }
    }

    /**
     * Converts a TSV file to a markdown table string.
     */
    private String readSectionFromTsv(String filename) {
        Path file = resultsDir.resolve(filename);
        if (Files.exists(file)) {
            try {
                List<String> lines = Files.readAllLines(file);
                if (lines.isEmpty()) {
                    return "*Empty results file.*\n";
                }

                var report = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) {
                    String[] cols = lines.get(i).split("\t");
                    report.append("| ");
                    for (String col : cols) {
                        report.append(col.trim()).append(" | ");
                    }
                    report.append('\n');

                    if (i == 0) {
                        report.append("| ");
                        for (int j = 0; j < cols.length; j++) {
                            report.append("--- | ");
                        }
                        report.append('\n');
                    }
                }
                return report.toString();
            } catch (IOException e) {
                return "*File could not be read: " + e.getMessage() + "*\n";
            }
        } else {
            return "*No data available — run the corresponding benchmark runner first.*\n";
        }
    }
}
