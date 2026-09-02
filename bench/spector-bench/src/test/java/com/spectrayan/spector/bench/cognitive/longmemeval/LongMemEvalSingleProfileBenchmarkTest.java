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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bench")
public class LongMemEvalSingleProfileBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalSingleProfileBenchmarkTest.class);

    @Test
    void runSingleProfileBenchmark() throws IOException {
        String defaultDataDir = "D:/git/spector-datasets/longmemeval-single-profile";
        if (!Files.exists(Paths.get(defaultDataDir))) {
            defaultDataDir = "../spector-datasets/longmemeval-single-profile";
        }

        String datasetDirStr = System.getProperty("datasetDir", defaultDataDir);
        Path datasetDir = Paths.get(datasetDirStr);

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
    }
}
