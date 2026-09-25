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
package com.spectrayan.spector.batch.reembed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Command line entry point for the standalone Re-embed batch job.
 */
@Component
public class ReembedCommand implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReembedCommand.class);

    private final JobLauncher jobLauncher;
    private final Job reembedJob;

    public ReembedCommand(JobLauncher jobLauncher, Job reembedJob) {
        this.jobLauncher = jobLauncher;
        this.reembedJob = reembedJob;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0 || !Arrays.asList(args).contains("reembed")) {
            return; // Not our command
        }

        String namespace = extractArg(args, "--namespace");
        if (namespace == null) {
            log.error("Missing required parameter: --namespace");
            System.exit(1);
            return;
        }

        String batchSizeStr = extractArg(args, "--batch-size");
        long batchSize = batchSizeStr != null ? Long.parseLong(batchSizeStr) : 100L;

        boolean dryRun = Arrays.asList(args).contains("--dry-run");

        log.info("Launching standalone Re-embed Job for namespace '{}' (batchSize={}, dryRun={})",
                namespace, batchSize, dryRun);

        JobParametersBuilder params = new JobParametersBuilder()
                .addString("namespace", namespace)
                .addLong("batchSize", batchSize)
                .addString("dryRun", String.valueOf(dryRun))
                .addLong("time", System.currentTimeMillis());

        JobExecution execution = jobLauncher.run(reembedJob, params.toJobParameters());
        
        log.info("Re-embed Job completed with status: {}", execution.getStatus());
        if (execution.getStatus().isUnsuccessful()) {
            System.exit(1);
        }
    }

    private String extractArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix + "=")) {
                return arg.substring(prefix.length() + 1);
            }
        }
        return null;
    }
}
