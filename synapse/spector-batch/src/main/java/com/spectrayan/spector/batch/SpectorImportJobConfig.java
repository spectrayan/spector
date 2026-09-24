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

import com.spectrayan.spector.batch.importing.SpectorMemoryImporter;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.SpectorMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Spring Batch job configuration for importing complete Spector Memory cognitive state from an SMB bundle
 * (ADR-0045, memory-portability R2.1 - R2.7, R3.3 - R3.4).
 */
@Configuration
public class SpectorImportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(SpectorImportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<SpectorMemory> memoryProvider;
    private final ObjectProvider<SpectorMemoryResolver> memoryResolverProvider;
    private volatile SpectorMemoryResolver explicitResolver;
    private final SpectorMemoryImporter memoryImporter;
    private final SpectorBundleCodec bundleCodec;

    @org.springframework.beans.factory.annotation.Autowired
    public SpectorImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<SpectorMemory> memoryProvider,
            ObjectProvider<SpectorMemoryResolver> memoryResolverProvider,
            ObjectProvider<SpectorMemoryImporter> importerProvider,
            ObjectProvider<SpectorBundleCodec> bundleCodecProvider) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.memoryProvider = memoryProvider;
        this.memoryResolverProvider = memoryResolverProvider;
        this.bundleCodec = bundleCodecProvider != null && bundleCodecProvider.getIfAvailable() != null
                ? bundleCodecProvider.getIfAvailable() : new SpectorBundleCodec();
        this.memoryImporter = importerProvider != null && importerProvider.getIfAvailable() != null
                ? importerProvider.getIfAvailable() : new SpectorMemoryImporter();
    }

    public SpectorImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<SpectorMemory> memoryProvider,
            ObjectProvider<SpectorMemoryResolver> memoryResolverProvider,
            SpectorMemoryImporter memoryImporter) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.memoryProvider = memoryProvider;
        this.memoryResolverProvider = memoryResolverProvider;
        this.bundleCodec = new SpectorBundleCodec();
        this.memoryImporter = memoryImporter != null ? memoryImporter : new SpectorMemoryImporter();
    }

    public SpectorImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        this(jobRepository, transactionManager, null, null, new SpectorMemoryImporter());
    }

    public void setMemoryResolver(SpectorMemoryResolver resolver) {
        this.explicitResolver = resolver;
    }

    @Bean
    public Job importJob() {
        return new JobBuilder("importMemoryJob", jobRepository)
                .start(unpackBundleStep())
                .next(validateManifestStep())
                .next(importMemoryNodesStep())
                .next(importGraphStep())
                .next(rebuildVectorIndexStep())
                .next(promoteImportStep())
                .next(cleanupImportStagingStep())
                .listener(new ImportJobExecutionListener())
                .build();
    }

    @Bean
    public Step unpackBundleStep() {
        return new StepBuilder("unpackBundleStep", jobRepository)
                .tasklet(unpackBundleTasklet(null, null, null, null, null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet unpackBundleTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobParameters['targetModel']}") String targetModel,
            @Value("#{jobParameters['targetDimensions']}") String targetDimensions,
            @Value("#{jobParameters['reembed']}") String reembed,
            @Value("#{jobParameters['stagingNamespace']}") String stagingNamespace) {
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

            String effStagingNs = (stagingNamespace != null && !stagingNamespace.isBlank())
                    ? stagingNamespace
                    : (targetNamespace != null ? targetNamespace + ".staging." + System.currentTimeMillis() : "staging");
            if (chunkContext != null && chunkContext.getStepContext() != null
                    && chunkContext.getStepContext().getStepExecution() != null
                    && chunkContext.getStepContext().getStepExecution().getJobExecution() != null) {
                chunkContext.getStepContext().getStepExecution().getJobExecution()
                        .getExecutionContext().putString("stagingNamespace", effStagingNs);
            }

            log.info("[ImportJob] Unpacked bundle {} to {} (stagingNamespace='{}', targetNamespace='{}')",
                    sourceBundle, stagingDir, effStagingNs, targetNamespace);
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet unpackBundleTasklet(
            String bundlePath,
            String targetNamespace,
            String targetModel,
            String targetDimensions,
            String reembed) {
        return unpackBundleTasklet(bundlePath, targetNamespace, targetModel, targetDimensions, reembed, null);
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

            // Task 3.5: Parse manifest and validate schema version (compatible with 3.x, reject anything else)
            SpectorBundleManifest manifest = SpectorBundleManifest.fromJson(
                    Files.readString(manifestPath, StandardCharsets.UTF_8)
            );
            if (manifest.schemaVersion() == null || !manifest.schemaVersion().startsWith("3.")) {
                throw new SpectorValidationException(
                        ErrorCode.FILE_FORMAT_INVALID,
                        "Incompatible bundle schema version '" + manifest.schemaVersion() + "'. Required: 3.x"
                );
            }

            // 1. Verify member checksums (Task 1.3, R3.2)
            bundleCodec.verifyChecksums(Paths.get(bundlePath), manifest);

            // 2. Verify required nodes directory exists (Task 3.1)
            Path nodesDir = stagingDir.resolve("nodes");
            if (!Files.exists(nodesDir) || !Files.isDirectory(nodesDir)) {
                throw new IllegalStateException("Invalid SMB bundle: missing nodes directory");
            }

            log.info("[ImportJob] Manifest validation PASSED: schemaVersion={}, records={}, edges={}, hyperedges={}, facts={}",
                    manifest.schemaVersion(),
                    manifest.counts().records(),
                    manifest.counts().edges(),
                    manifest.counts().hyperedges(),
                    manifest.counts().facts());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step importMemoryNodesStep() {
        return new StepBuilder("importMemoryNodesStep", jobRepository)
                .tasklet(importMemoryNodesTasklet(null, null, null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet importMemoryNodesTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobParameters['reembed']}") String reembed,
            @Value("#{jobExecutionContext['stagingNamespace']}") String stagingNamespace) {
        return (contribution, chunkContext) -> {
            Path stagingDir = getStagingDir(bundlePath);
            String effStagingNs = extractStagingNamespace(chunkContext, stagingNamespace, targetNamespace);
            SpectorMemory memory = resolveImportMemory(targetNamespace, effStagingNs);

            boolean allowReembed = Boolean.parseBoolean(reembed);
            var result = memoryImporter.importNodesAndVectors(stagingDir, memory, allowReembed);
            log.info("[ImportJob] Nodes ingestion complete: imported={}, skipped={}, dims={}",
                    result.importedRecords(), result.skippedDuplicates(), result.dimensions());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet importMemoryNodesTasklet(String bundlePath, String targetNamespace) {
        return importMemoryNodesTasklet(bundlePath, targetNamespace, "false", null);
    }

    @Bean
    public Step importGraphStep() {
        return new StepBuilder("importGraphStep", jobRepository)
                .tasklet(importGraphTasklet(null, null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet importGraphTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobExecutionContext['stagingNamespace']}") String stagingNamespace) {
        return (contribution, chunkContext) -> {
            Path stagingDir = getStagingDir(bundlePath);
            String effStagingNs = extractStagingNamespace(chunkContext, stagingNamespace, targetNamespace);
            SpectorMemory memory = resolveImportMemory(targetNamespace, effStagingNs);

            var result = memoryImporter.importGraph(stagingDir, memory);
            log.info("[ImportJob] Graph reconstruction complete: edges={}, hyperedges={}, facts={}",
                    result.importedEdges(), result.importedHyperedges(), result.importedFacts());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet importGraphTasklet(String bundlePath, String targetNamespace) {
        return importGraphTasklet(bundlePath, targetNamespace, null);
    }

    @Bean
    public Step rebuildVectorIndexStep() {
        return new StepBuilder("rebuildVectorIndexStep", jobRepository)
                .tasklet(rebuildVectorIndexTasklet(null, null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet rebuildVectorIndexTasklet(
            @Value("#{jobParameters['bundlePath']}") String bundlePath,
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobExecutionContext['stagingNamespace']}") String stagingNamespace) {
        return (contribution, chunkContext) -> {
            String effStagingNs = extractStagingNamespace(chunkContext, stagingNamespace, targetNamespace);
            SpectorMemory memory = resolveImportMemory(targetNamespace, effStagingNs);

            var report = memoryImporter.reconcileIndexes(memory);
            log.info("[ImportJob] Derived indexes reconciliation complete: {}", report);
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet rebuildVectorIndexTasklet(String bundlePath, String targetNamespace) {
        return rebuildVectorIndexTasklet(bundlePath, targetNamespace, null);
    }

    @Bean
    public Step promoteImportStep() {
        return new StepBuilder("promoteImportStep", jobRepository)
                .tasklet(promoteImportTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet promoteImportTasklet(
            @Value("#{jobParameters['targetNamespace']}") String targetNamespace,
            @Value("#{jobExecutionContext['stagingNamespace']}") String stagingNamespace) {
        return (contribution, chunkContext) -> {
            String effStagingNs = extractStagingNamespace(chunkContext, stagingNamespace, targetNamespace);
            SpectorMemoryResolver resolver = getEffectiveResolver();
            if (resolver != null) {
                resolver.promote(effStagingNs, targetNamespace);
                log.info("[ImportJob] Atomically promoted staging namespace '{}' to target '{}'",
                        effStagingNs, targetNamespace);
            }
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet promoteImportTasklet(String targetNamespace) {
        return promoteImportTasklet(targetNamespace, null);
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
            log.info("[ImportJob] Import bundle staging cleaned up: {}", stagingDir);
            return RepeatStatus.FINISHED;
        };
    }

    private String extractStagingNamespace(ChunkContext chunkContext, String stagingNamespace, String targetNamespace) {
        if (stagingNamespace != null && !stagingNamespace.isBlank()) {
            return stagingNamespace;
        }
        if (chunkContext != null && chunkContext.getStepContext() != null
                && chunkContext.getStepContext().getStepExecution() != null
                && chunkContext.getStepContext().getStepExecution().getJobExecution() != null) {
            String fromCtx = (String) chunkContext.getStepContext().getStepExecution()
                    .getJobExecution().getExecutionContext().get("stagingNamespace");
            if (fromCtx != null && !fromCtx.isBlank()) {
                return fromCtx;
            }
        }
        return targetNamespace != null ? targetNamespace + ".staging" : "staging";
    }

    private SpectorMemory resolveImportMemory(String targetNamespace, String stagingNamespace) {
        SpectorMemoryResolver resolver = getEffectiveResolver();

        if (resolver != null) {
            if (stagingNamespace != null && !stagingNamespace.isBlank()) {
                SpectorMemory stagingMem = resolver.resolve(stagingNamespace);
                if (stagingMem != null) {
                    return stagingMem;
                }
                stagingMem = resolver.createStaging(stagingNamespace, targetNamespace);
                if (stagingMem != null) {
                    return stagingMem;
                }
            }
            SpectorMemory targetMem = resolver.resolve(targetNamespace);
            if (targetMem != null) {
                return targetMem;
            }
        }

        SpectorMemory directMem = memoryProvider != null ? memoryProvider.getIfAvailable() : null;
        if (directMem != null) {
            return directMem;
        }

        throw new IllegalStateException("Cannot execute import job: no SpectorMemory or SpectorMemoryResolver available for target namespace: " + targetNamespace);
    }

    private SpectorMemoryResolver getEffectiveResolver() {
        if (explicitResolver != null) {
            return explicitResolver;
        }
        return memoryResolverProvider != null ? memoryResolverProvider.getIfAvailable() : null;
    }

    private Path getStagingDir(String bundlePath) {
        return Paths.get(bundlePath + ".tmp_import_staging");
    }

    private void deleteStagingDir(Path stagingDir) {
        if (!Files.exists(stagingDir)) {
            return;
        }
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

    private class ImportJobExecutionListener implements JobExecutionListener {
        @Override
        public void afterJob(JobExecution jobExecution) {
            if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
                String targetNamespace = jobExecution.getJobParameters().getString("targetNamespace");
                String stagingNamespace = jobExecution.getExecutionContext().getString("stagingNamespace");
                String bundlePath = jobExecution.getJobParameters().getString("bundlePath");

                log.warn("[ImportJob] Import job ended with status: {}. Discarding staging namespace.",
                        jobExecution.getStatus());
                SpectorMemoryResolver resolver = getEffectiveResolver();
                if (resolver != null && stagingNamespace != null) {
                    try {
                        resolver.discard(stagingNamespace);
                        log.info("[ImportJob] Staging namespace '{}' discarded on import failure", stagingNamespace);
                    } catch (Exception e) {
                        log.warn("[ImportJob] Error discarding staging namespace '{}': {}", stagingNamespace, e.getMessage());
                    }
                }
                if (bundlePath != null) {
                    deleteStagingDir(getStagingDir(bundlePath));
                }
            }
        }
    }
}
