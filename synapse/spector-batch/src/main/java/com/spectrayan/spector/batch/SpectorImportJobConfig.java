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
                .tasklet(unpackBundleTasklet(null, null, null, null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet unpackBundleTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobParameters['targetModel']}") String targetModel,
            @Value("#{jobParameters['targetDimensions']}") String targetDimensions,
            @Value("#{jobParameters['reembed']}") String reembed) {
        return (contribution, chunkContext) -> {
            Path sourceBundle = Paths.get(bundlePath);

            // Manifest-first read path (R3.3, V6): parse manifest and decide compatibility BEFORE unpacking
            SpectorBundleManifest manifest = bundleCodec.readManifest(sourceBundle);
            int dims = 0;
            if (targetDimensions != null && !targetDimensions.isBlank()) {
                try {
                    dims = Integer.parseInt(targetDimensions.trim());
                } catch (NumberFormatException ignored) {}
            }
            boolean allowReembed = Boolean.parseBoolean(reembed);
            manifest.validateCompatibility(targetModel, dims, allowReembed);

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
            Path stagingDir = getStagingDir(bundlePath);
            Path manifestPath = stagingDir.resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                throw new IllegalStateException("Invalid SMB bundle: missing manifest.json");
            }

            // NOTE: this step verifies member *presence* only. The manifest is deliberately not parsed
            // here, and there is consequently NO schema-version, embedding-model or dimensionality
            // compatibility check. Until issue #981 the manifest was read into a local variable and then
            // discarded, which gave the misleading appearance of validation; that read has been removed
            // rather than left in place. Manifest parsing and refusal-before-write are owned by
            // memory-portability R2.5/R3.3 (see SpectorBatchUnimplemented.OWNING_SPEC).

            // 1. Verify nodes component
            Path nodesDir = stagingDir.resolve("nodes");
            if (!Files.exists(nodesDir) || !Files.isDirectory(nodesDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing nodes directory");
            }
            long nodeCount = 0;
            try (var stream = Files.list(nodesDir)) {
                for (Path file : stream.filter(p -> p.toString().endsWith(".jsonl")).toList()) {
                    nodeCount += Files.lines(file).filter(line -> !line.isBlank()).count();
                }
            }

            // 2. Verify vectors component
            Path vectorsDir = stagingDir.resolve("vectors");
            if (!Files.exists(vectorsDir) || !Files.isDirectory(vectorsDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing vectors directory");
            }

            // 3. Verify graph component
            Path graphDir = stagingDir.resolve("graph");
            if (!Files.exists(graphDir) || !Files.isDirectory(graphDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing graph directory");
            }

            // 4. Verify subsystems component
            Path subDir = stagingDir.resolve("subsystems");
            if (!Files.exists(subDir) || !Files.isDirectory(subDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing subsystems directory");
            }

            // 5. Verify security component
            Path secDir = stagingDir.resolve("security");
            if (!Files.exists(secDir) || !Files.isDirectory(secDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing security directory");
            }

            log.info("[ImportJob] Bundle validation PASSED: verified manifest, nodesCount={}, vectors, graph, subsystems, security", nodeCount);
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
            throw SpectorBatchUnimplemented.step("importMemoryNodes",
                    "It checked that nodes/chunk-00001.jsonl existed and logged that it had imported "
                            + "the nodes. Nothing was parsed and nothing was written to memory; no memory "
                            + "id was ever read, so import was neither idempotent nor non-idempotent — it "
                            + "was undefined. It also hardcoded a single chunk name, silently ignoring "
                            + "every chunk beyond the first.");
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
            throw SpectorBatchUnimplemented.step("importGraph",
                    "It checked that graph/edges.jsonl existed and logged that it had imported "
                            + "hypergraph edges and Hebbian weights. No edge was created. Silent edge loss "
                            + "is the hardest defect to notice here, because recall keeps working and only "
                            + "degrades.");
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
            throw SpectorBatchUnimplemented.step("rebuildVectorIndex",
                    "It checked that a file named vectors-dim1536.bin existed and logged that it had "
                            + "rebuilt the vector indices. No index was rebuilt or reconciled.");
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
