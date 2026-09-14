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
package com.spectrayan.spector.bench.cognitive.mindspan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Active Inference Self-Model Engine (AISME) pathway against the MindSpan benchmark dataset.
 */
@Tag("mindspan-aisme")
public class MindSpanAismeRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(MindSpanAismeRunnerTest.class);

    @Test
    @DisplayName("Validate AISME pathway vs baseline recall on MindSpan biographical milestone sample")
    void testAismePathwayVsBaselineOnMindSpanSample() throws Exception {
        Path datasetDir = MindSpanAismeRunner.resolveMindSpanDataDir();
        Path bioCorpus = datasetDir.resolve("corpus-biographical.jsonl");

        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(bioCorpus),
                "MindSpan biographical corpus must exist at: " + bioCorpus);

        Path tempOut = Files.createTempDirectory("mindspan-aisme-test-");
        try {
            int queryLimit = 5;
            MindSpanAismeRunner runner = new MindSpanAismeRunner(datasetDir, tempOut, queryLimit);
            MindSpanAismeRunner.MindSpanAismeReport report = runner.run();

            assertNotNull(report, "Report must not be null");
            assertEquals(40, report.totalMemoriesIngested(), "Must have ingested 40 biographical milestone records");
            assertEquals(queryLimit, report.queriesEvaluated(), "Must have evaluated exactly " + queryLimit + " queries");

            // Verify Global Workspace conscious capacity bounding (K <= 7)
            assertTrue(report.workspaceGatedQueries() >= queryLimit,
                    "All queries must be bounded by the Global Workspace conscious bottleneck");

            for (MindSpanAismeRunner.ComparisonResult cmp : report.comparisons()) {
                assertTrue(cmp.workspaceBounded(), "Comparison for " + cmp.queryId() + " must be workspace bounded");
                assertTrue(cmp.aismeCandidateCount() <= 7,
                        "AISME candidates count must not exceed Global Workspace capacity 7");
                assertNotNull(cmp.aismeTopId(), "AISME top candidate ID must be present");
                assertTrue(cmp.aismeTopScore() > 0.0f, "AISME score must be positive");
            }

            // Check Query 1: Arthur Thompson Elgin watch milestone
            MindSpanAismeRunner.ComparisonResult q1 = report.comparisons().get(0);
            log.info("Q1 [{}] AismeTop=[{}] BaseTop=[{}]", q1.queryId(), q1.aismeTopId(), q1.baselineTopId());
            assertTrue(q1.aismeTopId().startsWith("bio-"),
                    "Q1 top result should identify a biographical record");

        } finally {
            if (Files.exists(tempOut)) {
                try (var s = Files.walk(tempOut)) {
                    s.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
                } catch (Exception ignored) {}
            }
        }
    }
}
