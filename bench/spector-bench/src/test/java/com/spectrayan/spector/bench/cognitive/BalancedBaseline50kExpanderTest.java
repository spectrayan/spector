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

import com.spectrayan.spector.bench.cognitive.generator.BalancedBaseline50kExpander;

@Tag("expansion")
class BalancedBaseline50kExpanderTest {

    @Test
    void run50kDatasetExpansion() {
        String datasetDirStr = System.getProperty("datasetDir", "d:\\git\\spector-datasets\\balanced-baseline\\data");
        String model = System.getProperty("embeddingModel", "nomic-embed-text");
        Path datasetDir = Paths.get(datasetDirStr);

        BalancedBaseline50kExpander expander = new BalancedBaseline50kExpander(datasetDir, model);
        expander.execute();
    }
}