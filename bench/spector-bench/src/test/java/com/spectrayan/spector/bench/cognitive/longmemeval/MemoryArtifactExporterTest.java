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
package com.spectrayan.spector.bench.cognitive.longmemeval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bench")
public class MemoryArtifactExporterTest {

    private static final Logger log = LoggerFactory.getLogger(MemoryArtifactExporterTest.class);

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
    void exportIngestedMemoryArtifacts() throws IOException {
        Path baseDir = resolveBaseDir();
        Path datasetDir = baseDir.resolve("longmemeval-single-profile");

        if (!Files.exists(datasetDir)) {
            log.warn("Dataset directory not found: {}, skipping export", datasetDir);
            return;
        }

        Path exportDir = datasetDir.resolve("extracted");
        MemoryArtifactExporter exporter = new MemoryArtifactExporter(datasetDir, exportDir);
        exporter.exportAll();

        assertTrue(Files.exists(exportDir.resolve("extracted_memories.jsonl")), "extracted_memories.jsonl must exist");
        assertTrue(Files.exists(exportDir.resolve("entities.jsonl")), "entities.jsonl must exist");
        assertTrue(Files.exists(exportDir.resolve("temporal_chains.jsonl")), "temporal_chains.jsonl must exist");
        assertTrue(Files.exists(exportDir.resolve("hebbian_edges.jsonl")), "hebbian_edges.jsonl must exist");
    }
}
