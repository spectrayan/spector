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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.bench.cognitive.generator.OllamaDatasetSynthesizerRunner;

@Tag("synthesis")
class OllamaDatasetSynthesizerRunnerTest {

    @Test
    void runOllamaSynthesis() {
        String datasetDirStr = System.getProperty("datasetDir", "d:\\git\\spector-datasets\\balanced-baseline\\data");
        String chatModel = System.getProperty("chatModel", "llama3.1:latest");
        String embedModel = System.getProperty("embedModel", "nomic-embed-text:latest");
        String ollamaUrl = System.getProperty("ollamaUrl", "http://localhost:11434");
        int targetRecords = Integer.parseInt(System.getProperty("targetRecords", "50000"));
        int totalDays = Integer.parseInt(System.getProperty("totalDays", "1050"));

        Path datasetDir = Paths.get(datasetDirStr);

        OllamaDatasetSynthesizerRunner runner = new OllamaDatasetSynthesizerRunner(
                datasetDir, chatModel, embedModel, ollamaUrl, targetRecords, totalDays);
        runner.run();
    }
}