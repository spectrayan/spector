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

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ContextConfiguration(classes = {TestBatchConfig.class, SpectorBatchAutoConfiguration.class})
@DisplayName("SpectorBatchService Integration Tests")
class SpectorBatchServiceTest {

    @Autowired
    private SpectorBatchService batchService;

    @Test
    @DisplayName("Should run complete export job and create SMB archive")
    void testRunExportJob(@TempDir Path tempDir) throws Exception {
        Path targetBundle = tempDir.resolve("export-test.smb");

        JobExecution execution = batchService.runExportJob("default", targetBundle);

        assertThat(execution).isNotNull();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.exists(targetBundle)).isTrue();
    }

    @Test
    @DisplayName("Should run complete import job from SMB archive")
    void testRunImportJob(@TempDir Path tempDir) throws Exception {
        Path bundlePath = tempDir.resolve("export-import-test.smb");
        batchService.runExportJob("default", bundlePath);

        JobExecution importExecution = batchService.runImportJob(bundlePath, "migrated_ns");

        assertThat(importExecution).isNotNull();
        assertThat(importExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
