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
package com.spectrayan.spector.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SpectorBatchService}.
 *
 * <p><b>These tests previously asserted the opposite of what they now assert.</b> They were named
 * <i>"Should run complete export job and create SMB archive"</i> and <i>"Should run complete import job
 * from SMB archive"</i>, and they passed — against a pipeline whose steps wrote hardcoded literals. The
 * export job did complete, and it did create an archive; that archive contained two sample sentences and
 * four literal vector bytes rather than any of the namespace's data. The tests were green for a pipeline
 * that would have silently destroyed a migration.</p>
 *
 * <p>Both jobs now refuse (issue #981). Until {@code memory-portability} implements them, the correct
 * assertion is that they fail, and that the failure tells the operator where to look.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/981">spectrayan/spector#981</a>
 */
@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {TestBatchConfig.class, SpectorBatchAutoConfiguration.class})
@DisplayName("SpectorBatchService Integration Tests")
class SpectorBatchServiceTest {

    @Autowired
    private SpectorBatchService batchService;

    @Test
    @DisplayName("Export job fails rather than producing a bundle of fabricated data")
    void exportJobRefusesAndProducesNoBundle(@TempDir Path tempDir) throws Exception {
        Path targetBundle = tempDir.resolve("export-test.smb");

        JobExecution execution = batchService.runExportJob("default", targetBundle);

        assertThat(execution).isNotNull();
        assertThat(execution.getStatus())
                .as("the job must not report COMPLETED while its steps cannot read memory")
                .isEqualTo(BatchStatus.FAILED);

        assertThat(Files.exists(targetBundle))
                .as("no .smb file may be left behind — a bundle on disk implies usable data")
                .isFalse();

        assertThat(execution.getAllFailureExceptions())
                .as("the failure must be attributable")
                .isNotEmpty();
        assertThat(execution.getAllFailureExceptions().getFirst())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(SpectorBatchUnimplemented.OWNING_SPEC);
    }

    @Test
    @DisplayName("Import job fails before writing anything into the target namespace")
    void importJobRefusesBeforeWriting(@TempDir Path tempDir) throws Exception {
        // Build a structurally valid bundle directly through the codec. The export job can no longer
        // produce one, and using it here would make this test fail at the wrong step — it would prove
        // only that export refuses, which exportJobRefusesAndProducesNoBundle already covers.
        // Every member directory needs at least one file: the codec archives regular files only, so an
        // empty directory does not survive the round trip and the bundle would be rejected as malformed
        // during manifest validation rather than at the step under test. See
        // SpectorBundleArchiveFidelityTest#emptyDirectoriesAreNotPreserved.
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("nodes"));
        Files.writeString(staging.resolve("manifest.json"), "{\"schemaVersion\":\"2.0.0\"}");
        Files.writeString(staging.resolve("nodes").resolve("chunk-00001.jsonl"), "{\"id\":\"a\"}\n");
        for (String member : new String[]{"vectors", "graph", "subsystems", "security"}) {
            Files.createDirectories(staging.resolve(member));
            Files.writeString(staging.resolve(member).resolve(".placeholder"), "");
        }

        Path bundlePath = tempDir.resolve("import-test.smb");
        new SpectorBundleCodec().packageBundle(staging, bundlePath);

        JobExecution execution = batchService.runImportJob(bundlePath, "migrated_ns");

        assertThat(execution).isNotNull();
        assertThat(execution.getStatus())
                .as("the job must not report COMPLETED while its steps write nothing")
                .isEqualTo(BatchStatus.FAILED);

        assertThat(execution.getAllFailureExceptions()).isNotEmpty();
        assertThat(execution.getAllFailureExceptions().getFirst())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("importMemoryNodes")
                .hasMessageContaining(SpectorBatchUnimplemented.OWNING_SPEC);

        // Unpack and manifest-presence validation are retained and run ahead of the first writing step,
        // so the refusal lands before the target namespace is touched.
        assertThat(execution.getStepExecutions())
                .as("refusal occurs at the first step that would write, not before validation")
                .anySatisfy(step -> assertThat(step.getStepName()).isEqualTo("importMemoryNodesStep"));
    }
}
