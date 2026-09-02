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
package com.spectrayan.spector.bench.conformance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.conformance.model.MfAssertion;
import com.spectrayan.spector.bench.conformance.model.MfCorpusRecord;
import com.spectrayan.spector.bench.conformance.model.MfExpected;
import com.spectrayan.spector.bench.conformance.model.MfQuery;
import com.spectrayan.spector.bench.conformance.model.MfReport;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test running the MF-001 Conformance Test Suite (MF-T01, MF-T03, MF-T10).
 */
public class MfConformanceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MfConformanceIntegrationTest.class);

    private static Path fixturesRoot;
    private static MfConformanceHarness harness;

    @BeforeAll
    public static void setup() {
        harness = new MfConformanceHarness();

        // In-tree test resources directory (portable across OS, GitHub Actions, and fresh clones)
        Path inTree = Paths.get("src/test/resources/mf-conformance");
        if (Files.exists(inTree.resolve("MF-T01-truncation-trap"))) {
            fixturesRoot = inTree;
        } else {
            var resource = MfConformanceIntegrationTest.class.getClassLoader().getResource("mf-conformance/MF-T01-truncation-trap");
            if (resource != null) {
                try {
                    fixturesRoot = Paths.get(resource.toURI()).getParent();
                } catch (Exception ignored) {}
            }
        }

        if (fixturesRoot == null) {
            throw new IllegalStateException("Could not locate vendored mf-conformance fixtures in src/test/resources/mf-conformance");
        }

        log.info("Resolved MF Conformance fixtures at: {}", fixturesRoot);
    }

    @Test
    @DisplayName("MF-T01 Truncation Trap (Fused: PASS key assertions, Negative Controls: FAIL)")
    public void testMfT01TruncationTrap() throws IOException {
        Path t01Dir = fixturesRoot.resolve("MF-T01-truncation-trap");

        // 1. Fused condition: MUST PASS core cognitive assertions
        MfReport fusedReport = harness.runFixture(t01Dir, MfConformanceHarness.CONDITION_FUSED);
        log.info("MF-T01 Fused Result: passed={}, failed={}", fusedReport.passed(), fusedReport.failed());
        assertTrue(fusedReport.passed().contains("T01-A1"), "Must pass T01-A1 (constraint retrieved in top 5)");
        assertTrue(fusedReport.passed().contains("T01-A2"), "Must pass T01-A2 (constraint outranks joke)");
        assertTrue(fusedReport.passed().contains("T01-A3"), "Must pass T01-A3 (simulated absent via NF7)");
        assertTrue(fusedReport.passed().contains("T01-A4"), "Must pass T01-A4 (critical profile minImportance=5.0)");
        assertTrue(fusedReport.passed().contains("T01-A6"), "Must pass T01-A6 (distilled fare card absent from top 3)");

        // 2. Negative Control 1: Cosine Top-K then Rerank (Expected to FAIL overall due to truncation)
        MfReport cosineReport = harness.runFixture(t01Dir, MfConformanceHarness.CONDITION_COSINE_TOPK_RERANK);
        log.info("MF-T01 Cosine Negative Control: passed={}, failed={}", cosineReport.passed(), cosineReport.failed());
        assertFalse(cosineReport.isAllPassed(), "Cosine top-K negative control must fail overall due to Stage 1 truncation");

        // 3. Negative Control 2: Hybrid Flat Importance (Expected to FAIL T01-A2: joke outranks constraint)
        MfReport flatReport = harness.runFixture(t01Dir, MfConformanceHarness.CONDITION_HYBRID_FLAT_IMPORTANCE);
        log.info("MF-T01 Hybrid Flat I=1: passed={}, failed={}", flatReport.passed(), flatReport.failed());
        boolean failedA2 = flatReport.failed().stream()
                .anyMatch(f -> "T01-A2".equals(f.id()));
        assertTrue(failedA2, "Hybrid flat importance negative control must fail T01-A2 (joke outranks constraint when beta=0)");
    }

    @Test
    @DisplayName("MF-T03 Valence Window (Fused: PASS valence gating & outranking)")
    public void testMfT03ValenceWindow() throws IOException {
        Path t03Dir = fixturesRoot.resolve("MF-T03-valence-window");

        // 1. Fused condition: MUST PASS valence gating and autobiographical recall
        MfReport fusedReport = harness.runFixture(t03Dir, MfConformanceHarness.CONDITION_FUSED);
        log.info("MF-T03 Fused Result: passed={}, failed={}", fusedReport.passed(), fusedReport.failed());
        assertTrue(fusedReport.passed().contains("T03-A1"), "Must pass T03-A1 (positive traces hard-gated from negative query)");
        assertTrue(fusedReport.passed().contains("T03-A2"), "Must pass T03-A2 (negative autobiographical retrieved in top 5)");
        assertTrue(fusedReport.passed().contains("T03-A4"), "Must pass T03-A4 (negative traces hard-gated from positive query)");
        assertTrue(fusedReport.passed().contains("T03-A5"), "Must pass T03-A5 (edgewater joy retrieved in top 3)");
        assertTrue(fusedReport.passed().contains("T03-A6"), "Must pass T03-A6 (asylum wait outranks ear-tube study)");
        assertTrue(fusedReport.passed().contains("T03-A7"), "Must pass T03-A7 (open valence allows essay back)");
    }

    @Test
    @DisplayName("MF-T10 Isolation (Fused: PASS 100%)")
    public void testMfT10Isolation() throws IOException {
        Path t10Dir = fixturesRoot.resolve("MF-T10-isolation");

        MfReport fusedReport = harness.runFixture(t10Dir, MfConformanceHarness.CONDITION_FUSED);
        log.info("MF-T10 Fused Result: passed={}, failed={}", fusedReport.passed(), fusedReport.failed());
        assertTrue(fusedReport.isAllPassed(), "MF-T10 must pass 100% of assertions under fused condition. Failed: " + fusedReport.failed());
        assertTrue(fusedReport.passed().contains("T10-A1"), "Must pass T10-A1 (A retrieves A)");
        assertTrue(fusedReport.passed().contains("T10-A2"), "Must pass T10-A2 (B absent from A)");
        assertTrue(fusedReport.passed().contains("T10-A3"), "Must pass T10-A3 (collision cue never scans other rememberers)");
        assertTrue(fusedReport.passed().contains("T10-A4"), "Must pass T10-A4 (B retrieves B)");
        assertTrue(fusedReport.passed().contains("T10-A5"), "Must pass T10-A5 (A absent from B)");
        assertTrue(fusedReport.passed().contains("T10-A6"), "Must pass T10-A6 (B asked asylum question abstains)");
        assertTrue(fusedReport.passed().contains("T10-A7"), "Must pass T10-A7 (engine-property: non-unioning partitions)");
    }
}
