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
 * Spring Batch job configuration for exporting complete Spector Memory cognitive state.
 */
@Configuration
public class SpectorExportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(SpectorExportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SpectorBundleCodec bundleCodec = new SpectorBundleCodec();

    public SpectorExportJobConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Job exportJob() {
        return new JobBuilder("exportMemoryJob", jobRepository)
                .start(exportManifestStep())
                .next(exportMemoryNodesStep())
                .next(exportVectorsStep())
                .next(exportGraphStep())
                .next(exportSubsystemsStep())
                .next(exportKeysStep())
                .next(validateExportStep())
                .next(packageBundleStep())
                .build();
    }

    @Bean
    public Step exportManifestStep() {
        return new StepBuilder("exportManifestStep", jobRepository)
                .tasklet(exportManifestTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportManifestTasklet(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportManifest",
                    "It wrote a hardcoded schemaVersion and component list, recording neither the "
                            + "embedding model, the vector dimensionality, nor the namespace id that an "
                            + "importer needs in order to decide whether the bundle is compatible.");
        };
    }

    @Bean
    public Step exportMemoryNodesStep() {
        return new StepBuilder("exportMemoryNodesStep", jobRepository)
                .tasklet(exportMemoryNodesTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportMemoryNodesTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportMemoryNodes",
                    "It wrote two hardcoded sample rows ('Spector cognitive memory initialized' and "
                            + "'Spring Batch pipeline configured') regardless of namespace contents. This "
                            + "configuration holds no reference to a memory engine and so cannot read any "
                            + "record.");
        };
    }

    @Bean
    public Step exportVectorsStep() {
        return new StepBuilder("exportVectorsStep", jobRepository)
                .tasklet(exportVectorsTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportVectorsTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportVectors",
                    "It wrote four literal bytes {0x00,0x01,0x02,0x03} into a file whose name hardcoded "
                            + "a dimensionality of 1536 — which was also the only place any dimensionality "
                            + "was recorded.");
        };
    }

    @Bean
    public Step exportGraphStep() {
        return new StepBuilder("exportGraphStep", jobRepository)
                .tasklet(exportGraphTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportGraphTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportGraph",
                    "It wrote a single hardcoded 'DEPENDS_ON' edge — a relation the engine does not "
                            + "produce — and emitted no Hebbian, temporal-chain or entity edges, and none "
                            + "of the hyperedge types and roles that recall actually traverses.");
        };
    }

    @Bean
    public Step exportSubsystemsStep() {
        return new StepBuilder("exportSubsystemsStep", jobRepository)
                .tasklet(exportSubsystemsTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportSubsystemsTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportSubsystems",
                    "It wrote a literal JSON object of invented subsystem values (arousal 0.2, empathy "
                            + "0.8, dopamine 1.0) read from nothing. Fabricated state is worse than an "
                            + "absent member because it looks like data.");
        };
    }

    @Bean
    public Step exportKeysStep() {
        return new StepBuilder("exportKeysStep", jobRepository)
                .tasklet(exportKeysTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportKeysTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("exportKeys",
                    "It wrote a literal claim of 'AES-256-GCM' encryption. No encryption, DEK, or "
                            + "envelope-encryption code exists anywhere in the product, so this asserted a "
                            + "security property the system does not implement. This member must stay "
                            + "absent until a DEK exists; see ADR-0034 Phase 6 decision D6.");
        };
    }

    @Bean
    public Step validateExportStep() {
        return new StepBuilder("validateExportStep", jobRepository)
                .tasklet(validateExportTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet validateExportTasklet(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            throw SpectorBatchUnimplemented.step("validateExport",
                    "It asserted the presence of the members the preceding steps had just fabricated, "
                            + "then rewrote manifest.json adding counts of those fixtures and a literal "
                            + "\"verified\": true. A validator must never stamp its own input as verified; "
                            + "the real implementation validates against the source namespace.");
        };
    }

    @Bean
    public Step packageBundleStep() {
        return new StepBuilder("packageBundleStep", jobRepository)
                .tasklet(packageBundleTasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet packageBundleTasklet(@Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            Path stagingDir = getStagingDir(targetBundlePath);
            Path targetFile = Paths.get(targetBundlePath);

            bundleCodec.packageBundle(stagingDir, targetFile);
            deleteStagingDir(stagingDir);
            log.info("[ExportJob] Bundle packaging complete: {}", targetFile);
            return RepeatStatus.FINISHED;
        };
    }

    private Path getStagingDir(String targetBundlePath) {
        return Paths.get(targetBundlePath + ".tmp_staging");
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
