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

@Tag("benchmark-suite")
class ContextExportTest {

    private static final Logger log = LoggerFactory.getLogger(ContextExportTest.class);

    private static Path resolveBaseDir() {
        String sysProp = System.getProperty("datasets.base.dir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }
        String envVar = System.getenv("DATASETS_BASE_DIR");
        if (envVar != null && !envVar.isBlank()) {
            return Paths.get(envVar);
        }
        Path current = Paths.get("").toAbsolutePath();
        for (Path dir = current; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("spector-datasets");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path siblingCandidate = dir.getParent() != null ? dir.getParent().resolve("spector-datasets") : null;
            if (siblingCandidate != null && Files.exists(siblingCandidate)) {
                return siblingCandidate;
            }
        }
        return Paths.get("..", "spector-datasets");
    }

    @Test
    void exportRetrievalContext() {
        String datasetName = System.getProperty("dataset", "locomo");
        int topK = Integer.parseInt(System.getProperty("topK", "10"));
        int limit = Integer.parseInt(System.getProperty("limit", "0"));

        Path baseDir = resolveBaseDir();
        Path datasetDir = baseDir.resolve(datasetName).resolve("data");
        Path defaultOutputFile = baseDir.resolve(datasetName).resolve("results").resolve("retrieved_candidates.jsonl");

        if (!Files.exists(datasetDir) || !Files.exists(datasetDir.resolve("corpus.jsonl"))) {
            log.warn("Dataset directory or corpus not found: {}, skipping context export in CI", datasetDir);
            return;
        }

        String outProp = System.getProperty("outputFile");
        Path outputFile = (outProp != null && !outProp.isBlank()) ? Paths.get(outProp) : defaultOutputFile;

        log.info("Starting context export for dataset [{}] to [{}] (TopK={}, Limit={})",
                datasetName, outputFile, topK, limit);

        new ContextExportRunner(datasetDir, outputFile, topK, limit).run();
    }
}
