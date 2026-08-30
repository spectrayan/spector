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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("bench")
public class LongMemEvalNaturalRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(LongMemEvalNaturalRunnerTest.class);

    @Test
    void runNaturalEpisodicIngestionAndReflection() {
        String datasetDirStr = System.getProperty("datasetDir", "data/longmemeval");
        String outputDirStr = System.getProperty("outputDir", "target/longmemeval/natural_results");
        String apiKey = System.getProperty("geminiApiKey", System.getenv().getOrDefault("GEMINI_API_KEY", ""));
        String model = System.getProperty("geminiModel", "gemini-3.1-flash-lite");
        int topK = Integer.parseInt(System.getProperty("topK", "50"));
        int sessionBatchSize = Integer.parseInt(System.getProperty("sessionBatchSize", "10"));
        boolean smokeTest = Boolean.parseBoolean(System.getProperty("smokeTest", "true"));
        int smokeTestLimit = Integer.parseInt(System.getProperty("smokeTestLimit", "20"));

        Path datasetDir = Paths.get(datasetDirStr);
        Path outputDir = Paths.get(outputDirStr);

        if (apiKey.isBlank() || !Files.exists(datasetDir)) {
            log.warn("Skipping LongMemEvalNaturalRunnerTest: Gemini API key or dataset not present");
            return;
        }

        log.info("Executing LongMemEval Natural Runner Test:");
        log.info("  Dataset: {}", datasetDir);
        log.info("  Output: {}", outputDir);
        log.info("  Model: {}", model);
        log.info("  TopK: {}", topK);
        log.info("  SessionBatchSize: {}", sessionBatchSize);
        log.info("  SmokeTest: {} (Limit: {})", smokeTest, smokeTestLimit);

        LongMemEvalNaturalRunner runner = new LongMemEvalNaturalRunner(
                datasetDir,
                outputDir,
                apiKey,
                model,
                topK,
                sessionBatchSize,
                smokeTest,
                smokeTestLimit
        );

        runner.run();
    }
}
