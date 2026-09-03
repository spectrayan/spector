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
package com.spectrayan.spector.bench.cognitive.mindspan;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Executes the MindSpan benchmark suite via Surefire.
 */
@Tag("mindspan")
public class MindSpanBenchmarkTest {

    @Test
    void runMindSpanBenchmark() throws Exception {
        if (Boolean.getBoolean("skipBenchTests")) {
            return;
        }
        String sysProp = System.getProperty("datasetDir");
        if (sysProp == null || !java.nio.file.Files.exists(java.nio.file.Path.of(sysProp))) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "MindSpan dataset not found; skipping benchmark execution in CI");
        }
        MindSpanBenchmarkRunner.main(new String[0]);
    }
}
