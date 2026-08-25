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
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.bench.cognitive.AismeRelayMatrixRunner.ConditionResult;

@Tag("benchmark-suite")
class MultiDatasetBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(MultiDatasetBenchmarkTest.class);

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

    private static Path resolveDatasetDir(String datasetName) {
        String specificEnv = System.getenv("DATASET_DIR");
        if (specificEnv != null && !specificEnv.isBlank() && datasetName.isEmpty()) {
            return Paths.get(specificEnv);
        }
        Path base = resolveBaseDir();
        return base.resolve(datasetName).resolve("data");
    }

    private static Path resolveOutputDir(String datasetName) {
        String specificEnv = System.getenv("OUTPUT_DIR");
        if (specificEnv != null && !specificEnv.isBlank() && datasetName.isEmpty()) {
            return Paths.get(specificEnv);
        }
        Path base = resolveBaseDir();
        return base.resolve(datasetName).resolve("results");
    }

    @Test
    void benchmarkBalancedBaseline() {
        runDatasetBenchmark("balanced-baseline");
    }

    @Test
    void benchmarkLocomo() {
        runDatasetBenchmark("locomo");
    }

    @Test
    void benchmarkLongMemEval() {
        runDatasetBenchmark("longmemeval");
    }

    private void runDatasetBenchmark(String datasetName) {
        Path datasetDir = resolveDatasetDir(datasetName);
        Path outputDir = resolveOutputDir(datasetName);

        if (!Files.exists(datasetDir)) {
            log.warn("Dataset directory not found: {}, skipping benchmark", datasetDir);
            return;
        }

        log.info("╔════════════════════════════════════════════════════════════════════╗");
        log.info("║  EVALUATING DATASET: {}                                             ", datasetName.toUpperCase(Locale.ROOT));
        log.info("║  Directory: {}                                                      ", datasetDir);
        log.info("╚════════════════════════════════════════════════════════════════════╝");

        AismeRelayMatrixRunner runner = new AismeRelayMatrixRunner(datasetDir, outputDir, 10);
        List<ConditionResult> results = runner.run();

        log.info("\n=== RESULTS FOR DATASET: {} ===", datasetName);
        log.info(String.format(Locale.ROOT, "%-35s | %-8s | %-8s | %-8s | %-9s | %-9s | %-9s | %-9s | %-8s | %-9s",
                "Condition", "nDCG@10", "MRR@10", "Recall", "Avg (ms)", "p50 (ms)", "p95 (ms)", "p99 (ms)", "QPS", "Cohen's d"));
        log.info("----------------------------------------------------------------------------------------------------------------------------------");

        for (ConditionResult res : results) {
            log.info(String.format(Locale.ROOT, "%-35s | %-8.4f | %-8.4f | %-8.4f | %-9.2f | %-9.2f | %-9.2f | %-9.2f | %-8.1f | %-+9.3f",
                    res.condition().name(), res.meanNdcg(), res.meanMrr(), res.meanRecall(),
                    res.avgLatencyMs(), res.p50LatencyMs(), res.p95LatencyMs(), res.p99LatencyMs(),
                    res.qps(), res.cohensD()));
        }
        log.info("===============================================================================================\n");
    }
}