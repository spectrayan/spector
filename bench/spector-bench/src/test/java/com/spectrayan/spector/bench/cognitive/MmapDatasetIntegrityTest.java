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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("benchmark-suite")
class MmapDatasetIntegrityTest {

    private static final Logger log = LoggerFactory.getLogger(MmapDatasetIntegrityTest.class);

    private static Path resolveDatasetDir(String datasetName) {
        String sysProp = System.getProperty("datasets.base.dir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp).resolve(datasetName).resolve("data");
        }
        String envVar = System.getenv("DATASETS_BASE_DIR");
        if (envVar != null && !envVar.isBlank()) {
            return Paths.get(envVar).resolve(datasetName).resolve("data");
        }
        Path current = Paths.get("").toAbsolutePath();
        for (Path dir = current; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("spector-datasets").resolve(datasetName).resolve("data");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path siblingCandidate = dir.getParent() != null ? dir.getParent().resolve("spector-datasets").resolve(datasetName).resolve("data") : null;
            if (siblingCandidate != null && Files.exists(siblingCandidate)) {
                return siblingCandidate;
            }
        }
        return Paths.get("..", "spector-datasets", datasetName, "data");
    }

    @Test
    void validateLocomoMmapIntegrity() {
        Path locomoDir = resolveDatasetDir("locomo");
        if (!Files.exists(locomoDir) || !Files.exists(locomoDir.resolve("corpus.jsonl"))) {
            log.warn("LoCoMo dataset not found at {}, skipping MMAP integrity test in CI", locomoDir);
            return;
        }

        MmapDatasetIntegrityValidator validator = new MmapDatasetIntegrityValidator();
        MmapDatasetIntegrityValidator.ValidationReport report = validator.validate(locomoDir);

        log.info("\n{}", report.markdownSummary());
        assertTrue(report.isHealthy(), "MMAP dataset integrity check failed with " + report.failedChecks() + " violations: " + report.violations());
    }
}
