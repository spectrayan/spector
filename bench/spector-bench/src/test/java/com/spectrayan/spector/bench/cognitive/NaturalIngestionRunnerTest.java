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
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("bench")
public class NaturalIngestionRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(NaturalIngestionRunnerTest.class);

    @Test
    void runNaturalIngestion() {
        String apiKey = System.getProperty("geminiApiKey", System.getenv().getOrDefault("GEMINI_API_KEY", ""));
        String datasetDir = System.getProperty("datasetDir", "data/locomo");
        if (apiKey.isBlank() || !Files.exists(Paths.get(datasetDir))) {
            log.warn("Skipping NaturalIngestionRunnerTest: GEMINI_API_KEY or dataset not present");
            return;
        }
        NaturalIngestionRunner.main(new String[0]);
    }
}
