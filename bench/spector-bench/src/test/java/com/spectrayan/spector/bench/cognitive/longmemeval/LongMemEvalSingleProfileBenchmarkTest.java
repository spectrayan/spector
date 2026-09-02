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

import com.spectrayan.spector.bench.cognitive.CognitiveBenchmarkHarness;
import com.spectrayan.spector.bench.cognitive.model.BenchmarkExitCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bench")
public class LongMemEvalSingleProfileBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalSingleProfileBenchmarkTest.class);
    private static final ObjectMapper jsonMapper = JsonMapper.builder().build();

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

    private static Path resolveDatasetDir() {
        String specificEnv = System.getenv("DATASET_DIR");
        if (specificEnv != null && !specificEnv.isBlank()) {
            return Paths.get(specificEnv);
        }
        String sysProp = System.getProperty("datasetDir");
        if (sysProp != null && !sysProp.isBlank()) {
            return Paths.get(sysProp);
        }
        Path base = resolveBaseDir();
        return base.resolve("longmemeval-single-profile");
    }

    @Test
    void runSingleProfileBenchmark() throws IOException {
        Path datasetDir = resolveDatasetDir();

        if (!Files.exists(datasetDir)) {
            log.warn("Skipping single profile benchmark: dataset directory not found at {}", datasetDir);
            return;
        }

        Path outputDir = Paths.get(System.getProperty(
                "outputDir",
                datasetDir.resolve("results").toString()
        ));

        log.info("Starting Single Profile LongMemEval Benchmark Execution:");
        log.info("  Dataset: {}", datasetDir.toAbsolutePath());
        log.info("  Output: {}", outputDir.toAbsolutePath());

        CognitiveBenchmarkHarness harness = new CognitiveBenchmarkHarness(
                datasetDir,
                outputDir,
                0.0,
                10,
                null
        );

        BenchmarkExitCode exitCode = harness.run();
        log.info("Single Profile LongMemEval Benchmark Exit Code: {}", exitCode);
        assertEquals(BenchmarkExitCode.SUCCESS, exitCode, "Benchmark must exit with SUCCESS");

        Path summaryJson = outputDir.resolve("summary.json");
        assertTrue(Files.exists(summaryJson), "summary.json must be generated");

        Path detailCsv = outputDir.resolve("detail.csv");
        assertTrue(Files.exists(detailCsv), "detail.csv must be generated");

        // Verify quality pins matching published empirical metrics
        JsonNode root = jsonMapper.readTree(Files.readString(summaryJson));
        double cogNdcg = root.path("cognitive_metrics").path("ndcg_at_10").asDouble(0.0);
        double cogMrr = root.path("cognitive_metrics").path("mrr_at_10").asDouble(0.0);
        double cogRecall = root.path("cognitive_metrics").path("recall_at_10").asDouble(0.0);
        int losses = root.path("win_tie_loss").path("losses").asInt(99);
        int wins = root.path("win_tie_loss").path("wins").asInt(0);

        log.info("Verified Metrics: nDCG@10={}, MRR@10={}, Recall@10={}, Wins={}, Losses={}",
                cogNdcg, cogMrr, cogRecall, wins, losses);

        assertTrue(cogNdcg >= 0.75, "Cognitive nDCG@10 must be >= 0.75 (got " + cogNdcg + ")");
        assertTrue(cogMrr >= 0.80, "Cognitive MRR@10 must be >= 0.80 (got " + cogMrr + ")");
        assertTrue(cogRecall >= 0.80, "Cognitive Recall@10 must be >= 0.80 (got " + cogRecall + ")");
        assertEquals(0, losses, "Cognitive pipeline must have 0 losses vs vector baseline");
        assertTrue(wins >= 5, "Cognitive pipeline must have >= 5 wins vs vector baseline");
    }
}
