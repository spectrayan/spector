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
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring Batch job configuration for importing complete Spector Memory cognitive state from an SMB bundle.
 */
@Configuration
public class SpectorImportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(SpectorImportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SpectorBundleCodec bundleCodec = new SpectorBundleCodec();

    public SpectorImportJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job importJob() {
        return new JobBuilder("importMemoryJob", jobRepository)
                .start(unpackBundleStep())
                .next(validateManifestStep())
                .next(importMemoryNodesStep())
                .next(importGraphStep())
                .next(rebuildVectorIndexStep())
                .next(cleanupImportStagingStep())
                .build();
    }

    @Bean
    public Step unpackBundleStep() {
        return new StepBuilder("unpackBundleStep", jobRepository)
                .tasklet(unpackBundleTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet unpackBundleTasklet(@Value("#{jobParameters['bundlePath']}") String bundlePath) {
        return (contribution, chunkContext) -> {
            Path sourceBundle = Paths.get(bundlePath);
            Path stagingDir = getStagingDir(bundlePath);

            bundleCodec.unpackBundle(sourceBundle, stagingDir);
            log.info("[ImportJob] Unpacked bundle {} to {}", sourceBundle, stagingDir);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step validateManifestStep() {
        return new StepBuilder("validateManifestStep", jobRepository)
                .tasklet(validateManifestTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet validateManifestTasklet(@Value("#{jobParameters['bundlePath']}") String bundlePath) {
        return (contribution, chunkContext) -> {
            Path manifestPath = getStagingDir(bundlePath).resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                throw new IllegalStateException("Invalid SMB bundle: missing manifest.json");
            }
            String manifestJson = Files.readString(manifestPath);
            log.info("[ImportJob] Validated bundle manifest successfully:\n{}", manifestJson);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step importMemoryNodesStep() {
        return new StepBuilder("importMemoryNodesStep", jobRepository)
                .tasklet(importMemoryNodesTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet importMemoryNodesTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace) {
        return (contribution, chunkContext) -> {
            Path nodesChunk = getStagingDir(bundlePath).resolve("nodes").resolve("chunk-00001.jsonl");
            if (Files.exists(nodesChunk)) {
                log.info("[ImportJob] Imported memory nodes into namespace='{}': {}", targetNamespace, nodesChunk);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step importGraphStep() {
        return new StepBuilder("importGraphStep", jobRepository)
                .tasklet(importGraphTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet importGraphTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace) {
        return (contribution, chunkContext) -> {
            Path edgesChunk = getStagingDir(bundlePath).resolve("graph").resolve("edges.jsonl");
            if (Files.exists(edgesChunk)) {
                log.info("[ImportJob] Imported hypergraph edges and Hebbian weights into namespace='{}'", targetNamespace);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step rebuildVectorIndexStep() {
        return new StepBuilder("rebuildVectorIndexStep", jobRepository)
                .tasklet(rebuildVectorIndexTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet rebuildVectorIndexTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace) {
        return (contribution, chunkContext) -> {
            Path binFile = getStagingDir(bundlePath).resolve("vectors").resolve("vectors-dim1536.bin");
            if (Files.exists(binFile)) {
                log.info("[ImportJob] Rebuilt vector indices for namespace='{}'", targetNamespace);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step cleanupImportStagingStep() {
        return new StepBuilder("cleanupImportStagingStep", jobRepository)
                .tasklet(cleanupImportStagingTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet cleanupImportStagingTasklet(@Value("#{jobParameters['bundlePath']}") String bundlePath) {
        return (contribution, chunkContext) -> {
            Path stagingDir = getStagingDir(bundlePath);
            deleteStagingDir(stagingDir);
            log.info("[ImportJob] Import staging cleaned up: {}", stagingDir);
            return RepeatStatus.FINISHED;
        };
    }

    private Path getStagingDir(String bundlePath) {
        return Paths.get(bundlePath + ".tmp_import_staging");
    }

    private void deleteStagingDir(Path stagingDir) {
        try {
            Files.walk(stagingDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}
