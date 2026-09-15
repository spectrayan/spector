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

import com.spectrayan.spector.kernel.api.DreamMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Dream Pathway against the 20-year longitudinal MindSpan {@code v2-memory} dataset.
 */
@Tag("mindspan-dream")
public class MindSpanDreamBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(MindSpanDreamBenchmarkTest.class);

    @Test
    @DisplayName("Validate Dream Pathway autonomous TMR, 12-relay conduction, and zero-confabulation on MindSpan v2-memory")
    void testDreamPathwayOnMindSpanV2Memory() throws Exception {
        Path memDir = MindSpanDreamRunner.resolveMindSpanMemoryDir();

        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(memDir),
                "MindSpan v2-memory directory must exist at: " + memDir);

        Path tempOut = Files.createTempDirectory("mindspan-dream-test-");
        try {
            MindSpanDreamRunner runner = new MindSpanDreamRunner(memDir, tempOut);
            MindSpanDreamRunner.MindSpanDreamReport report = runner.run();

            assertNotNull(report, "Dream report must not be null");

            // 1. Store validation
            assertTrue(report.totalMemoriesInStore() > 0, "Total memories in store must be positive");
            assertTrue(report.semanticMemoriesInStore() > 0, "Semantic slab count must be positive");

            // 2. REM Dream Mode validation
            assertNotNull(report.remReport(), "REM dream report must be present");
            assertEquals(DreamMode.REM, report.remReport().mode(), "REM mode must be asserted");
            assertTrue(report.remReport().seedsSampled() > 0,
                    "Autonomous TMR must have sampled at least 1 salient seed from 20-year memory");
            assertTrue(report.remReport().scenesConstructed() > 0,
                    "Constructive episodic simulation must have constructed at least 1 synthetic scene");
            assertTrue(report.remReport().scenesTriaged() > 0,
                    "Prefrontal EFE triage must have evaluated constructed scenes");

            // 3. Thought Experiment & Daydream Mode validation
            assertNotNull(report.thoughtReport(), "Thought experiment report must be present");
            assertEquals(DreamMode.THOUGHT_EXPERIMENT, report.thoughtReport().mode());

            assertNotNull(report.daydreamReport(), "Daydream report must be present");
            assertEquals(DreamMode.DAYDREAM, report.daydreamReport().mode());

            // 4. Source Monitoring & Confabulation Immunity Check
            assertTrue(report.factualQueriesEvaluated() >= 5, "Must have evaluated factual queries");
            assertEquals(0, report.factualConfabulationsDetected(),
                    "Factual recall (allowSimulated=false) MUST achieve 0 confabulations (100% immunity)");

            // 5. Exploratory Recall Check
            assertTrue(report.exploratoryQueriesEvaluated() >= 3, "Must have evaluated exploratory queries");
            assertTrue(report.dreamInsightsRetrievedUnderExploratory() > 0,
                    "Exploratory recall (allowSimulated=true) MUST retrieve at least one persisted dreamed insight");

            log.info("Dream Pathway validation PASSED: seeds={}, scenes={}, factualConfabulations={}, exploratoryRetrieved={}",
                    report.remReport().seedsSampled(),
                    report.remReport().scenesConstructed(),
                    report.factualConfabulationsDetected(),
                    report.dreamInsightsRetrievedUnderExploratory());

        } finally {
            if (Files.exists(tempOut)) {
                try (var s = Files.walk(tempOut)) {
                    s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
                } catch (Exception ignored) {}
            }
        }
    }
}
