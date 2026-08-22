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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.spectrayan.spector.bench.cognitive.generator.OllamaDatasetSynthesizerRunner;

@Tag("integration")
@Tag("synthesis")
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE", matches = "true")
class OllamaDatasetSynthesizerRunnerTest {

    @Test
    void runOllamaSynthesis() {
        String datasetDirStr = System.getProperty(
                "datasetDir",
                System.getProperty("spector.bench.dataset.dir",
                        System.getenv().getOrDefault("DATASET_DIR", "../spector-datasets/balanced-baseline/data")));
        String chatModel = System.getProperty("chatModel", System.getenv().getOrDefault("CHAT_MODEL", "llama3.1:latest"));
        String embedModel = System.getProperty("embedModel",
                System.getProperty("spector.embed.model", System.getenv().getOrDefault("EMBED_MODEL", "nomic-embed-text:latest")));
        String ollamaUrl = System.getProperty("ollamaUrl", System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434"));
        int targetRecords = Integer.parseInt(System.getProperty("targetRecords", System.getenv().getOrDefault("TARGET_RECORDS", "50000")));
        int totalDays = Integer.parseInt(System.getProperty("totalDays", System.getenv().getOrDefault("TOTAL_DAYS", "1050")));

        Path datasetDir = Paths.get(datasetDirStr);

        Assumptions.assumeTrue(Files.exists(datasetDir.resolve("corpus.jsonl")),
                "Dataset corpus.jsonl not found at " + datasetDir);

        OllamaDatasetSynthesizerRunner runner = new OllamaDatasetSynthesizerRunner(
                datasetDir, chatModel, embedModel, ollamaUrl, targetRecords, totalDays);
        runner.run();
    }
}