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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Service facade for launching and tracking Spector Memory Spring Batch migration jobs.
 */
@Service
public class SpectorBatchService {

    private static final Logger log = LoggerFactory.getLogger(SpectorBatchService.class);

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job exportJob;
    private final Job importJob;

    public SpectorBatchService(
            JobLauncher jobLauncher,
            JobExplorer jobExplorer,
            @org.springframework.beans.factory.annotation.Qualifier("exportJob") Job exportJob,
            @org.springframework.beans.factory.annotation.Qualifier("importJob") Job importJob) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.exportJob = exportJob;
        this.importJob = importJob;
    }

    /**
     * Executes an export job for the given namespace into a target SMB file.
     *
     * @param namespace target namespace
     * @param targetBundlePath destination file path
     * @return JobExecution details
     * @throws Exception if job initiation fails
     */
    public JobExecution runExportJob(String namespace, Path targetBundlePath) throws Exception {
        log.info("[SpectorBatchService] Launching export job for namespace='{}' -> {}", namespace, targetBundlePath);
        JobParameters params = new JobParametersBuilder()
                .addString("namespace", namespace)
                .addString("targetBundlePath", targetBundlePath.toAbsolutePath().toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        return jobLauncher.run(exportJob, params);
    }

    /**
     * Executes an import job from a given SMB bundle path into a target namespace.
     *
     * @param bundlePath input SMB bundle path
     * @param targetNamespace destination namespace
     * @return JobExecution details
     * @throws Exception if job initiation fails
     */
    public JobExecution runImportJob(Path bundlePath, String targetNamespace) throws Exception {
        log.info("[SpectorBatchService] Launching import job from {} -> namespace='{}'", bundlePath, targetNamespace);
        JobParameters params = new JobParametersBuilder()
                .addString("bundlePath", bundlePath.toAbsolutePath().toString())
                .addString("targetNamespace", targetNamespace)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        return jobLauncher.run(importJob, params);
    }

    /**
     * Obtains status and metadata for a running or completed Spring Batch job execution.
     *
     * @param executionId job execution ID
     * @return JobExecution object or null if not found
     */
    public JobExecution getJobExecution(long executionId) {
        return jobExplorer.getJobExecution(executionId);
    }
}
