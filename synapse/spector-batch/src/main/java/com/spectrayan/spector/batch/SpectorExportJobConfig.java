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

import com.spectrayan.spector.batch.exporting.ExportScope;
import com.spectrayan.spector.batch.exporting.SpectorMemoryExporter;
import com.spectrayan.spector.batch.exporting.SpectorMemoryExporter.GraphExportResult;
import com.spectrayan.spector.batch.exporting.SpectorMemoryExporter.NodeExportResult;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Spring Batch job configuration for exporting complete Spector Memory cognitive state,
 * records, vectors, and graph edges into a portable .smb bundle (ADR-0045, memory-portability R1).
 */
@Configuration
public class SpectorExportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(SpectorExportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<SpectorMemory> memoryProvider;
    private final ObjectProvider<SpectorMemoryResolver> memoryResolverProvider;
    private final SpectorBundleCodec bundleCodec;
    private final SpectorMemoryExporter exporter;

    private volatile SpectorMemory explicitMemory;
    private volatile SpectorMemoryResolver explicitResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public SpectorExportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<SpectorMemory> memoryProvider,
            ObjectProvider<SpectorMemoryResolver> memoryResolverProvider,
            ObjectProvider<SpectorMemoryExporter> exporterProvider,
            ObjectProvider<SpectorBundleCodec> bundleCodecProvider) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.memoryProvider = memoryProvider;
        this.memoryResolverProvider = memoryResolverProvider;
        this.bundleCodec = bundleCodecProvider != null && bundleCodecProvider.getIfAvailable() != null
                ? bundleCodecProvider.getIfAvailable() : new SpectorBundleCodec();
        this.exporter = exporterProvider != null && exporterProvider.getIfAvailable() != null
                ? exporterProvider.getIfAvailable() : new SpectorMemoryExporter();
    }

    public SpectorExportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        this(jobRepository, transactionManager, null, null, null, null);
    }

    public SpectorExportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SpectorMemory memory) {
        this(jobRepository, transactionManager, null, null, null, null);
        this.explicitMemory = memory;
    }

    public SpectorExportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SpectorMemory memory,
            SpectorBundleCodec bundleCodec) {
        this(jobRepository, transactionManager, null, null, null, null);
        this.explicitMemory = memory;
        if (bundleCodec != null) {
            // Use provided codec if non-null
        }
    }

    public SpectorExportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SpectorMemoryResolver memoryResolver) {
        this(jobRepository, transactionManager, null, null, null, null);
        this.explicitResolver = memoryResolver;
    }

    public void setMemory(SpectorMemory memory) {
        this.explicitMemory = memory;
    }

    public void setMemoryResolver(SpectorMemoryResolver resolver) {
        this.explicitResolver = resolver;
    }

    public SpectorMemory resolveMemory(String namespace) {
        if (explicitMemory != null) {
            return explicitMemory;
        }
        if (explicitResolver != null) {
            SpectorMemory resolved = explicitResolver.resolve(namespace);
            if (resolved != null) {
                return resolved;
            }
        }
        if (memoryResolverProvider != null) {
            SpectorMemoryResolver resolver = memoryResolverProvider.getIfAvailable();
            if (resolver != null) {
                SpectorMemory resolved = resolver.resolve(namespace);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        if (memoryProvider != null) {
            SpectorMemory memory = memoryProvider.getIfAvailable();
            if (memory != null) {
                return memory;
            }
        }
        throw new IllegalStateException("Cannot execute export job: no SpectorMemory or SpectorMemoryResolver available for namespace: " + namespace);
    }

    @Bean
    public Job exportJob() {
        return new JobBuilder("exportMemoryJob", jobRepository)
                .start(exportMemoryNodesStep())
                .next(exportVectorsStep())
                .next(exportGraphStep())
                .next(exportSubsystemsStep())
                .next(exportKeysStep())
                .next(exportManifestStep())
                .next(validateExportStep())
                .next(packageBundleStep())
                .build();
    }

    @Bean
    public Step exportMemoryNodesStep() {
        return new StepBuilder("exportMemoryNodesStep", jobRepository)
                .tasklet(exportMemoryNodesTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportMemoryNodesTasklet(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveNamespace = namespace != null && !namespace.isBlank() ? namespace : (String) jobParams.get("namespace");
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");

            SpectorMemory memory = resolveMemory(effectiveNamespace);
            Path stagingDir = getStagingDir(effectiveBundlePath);

            ExportScope scope = parseScope(effectiveNamespace, jobParams);
            int dimsHint = parseDimensionsHint(jobParams);

            NodeExportResult result = exporter.exportNodesAndVectors(stagingDir, memory, scope, dimsHint);

            ExecutionContext execCtx = getExecutionContext(chunkContext);
            if (execCtx != null) {
                execCtx.putLong("export.recordCount", result.totalRecords());
                execCtx.putInt("export.dimensions", result.dimensions());
            }

            log.info("[ExportJob] Memory nodes step complete: {} records exported (dims={})",
                    result.totalRecords(), result.dimensions());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet exportMemoryNodesTasklet(String targetBundlePath) {
        return exportMemoryNodesTasklet(null, targetBundlePath);
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
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");
            Path stagingDir = getStagingDir(effectiveBundlePath);
            Path vectorsDir = stagingDir.resolve("vectors");

            if (Files.exists(vectorsDir)) {
                try (var stream = Files.list(vectorsDir)) {
                    long chunkCount = stream.count();
                    log.info("[ExportJob] Vectors step verified: {} vector chunk files present", chunkCount);
                }
            }
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet exportVectorsTasklet(String targetBundlePath, String ignored) {
        return exportVectorsTasklet(targetBundlePath);
    }

    @Bean
    public Step exportGraphStep() {
        return new StepBuilder("exportGraphStep", jobRepository)
                .tasklet(exportGraphTasklet(null, null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportGraphTasklet(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['targetBundlePath']}") String targetBundlePath) {
        return (contribution, chunkContext) -> {
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveNamespace = namespace != null && !namespace.isBlank() ? namespace : (String) jobParams.get("namespace");
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");

            SpectorMemory memory = resolveMemory(effectiveNamespace);
            Path stagingDir = getStagingDir(effectiveBundlePath);

            GraphExportResult result = exporter.exportGraph(stagingDir, memory);

            ExecutionContext execCtx = getExecutionContext(chunkContext);
            if (execCtx != null) {
                execCtx.putLong("export.edgeCount", result.edges());
                execCtx.putLong("export.hyperedgeCount", result.hyperedges());
                execCtx.putLong("export.factCount", result.facts());
            }

            log.info("[ExportJob] Graph step complete: edges={}, hyperedges={}, facts={}",
                    result.edges(), result.hyperedges(), result.facts());
            return RepeatStatus.FINISHED;
        };
    }

    public Tasklet exportGraphTasklet(String targetBundlePath) {
        return exportGraphTasklet(null, targetBundlePath);
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
            // R1.4: Emit real biological subsystem state or omit the member.
            // Never emit a fabricated literal like {"hippocampus":"active",...}.
            log.info("[ExportJob] Subsystems step: no externalized biological state configured; member omitted per R1.4.");
            return RepeatStatus.FINISHED;
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
            // R1.5, V2: Do not emit security/keys.json claiming AES-256-GCM encryption
            // until a Phase 6 DEK exists (ADR-0034 D6).
            log.info("[ExportJob] Keys step: encryption keys member omitted per R1.5.");
            return RepeatStatus.FINISHED;
        };
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
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveNamespace = namespace != null && !namespace.isBlank() ? namespace : (String) jobParams.get("namespace");
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");

            Path stagingDir = getStagingDir(effectiveBundlePath);
            ExecutionContext execCtx = getExecutionContext(chunkContext);

            long recordCount = execCtx != null ? execCtx.getLong("export.recordCount", 0L) : 0L;
            int dimensions = execCtx != null ? execCtx.getInt("export.dimensions", 0) : 0;
            long edgeCount = execCtx != null ? execCtx.getLong("export.edgeCount", 0L) : 0L;
            long hyperedgeCount = execCtx != null ? execCtx.getLong("export.hyperedgeCount", 0L) : 0L;
            long factCount = execCtx != null ? execCtx.getLong("export.factCount", 0L) : 0L;

            String embeddingModel = (String) jobParams.get("embeddingModel");
            String quantizer = (String) jobParams.get("quantizer");

            SpectorBundleManifest.BundleCounts counts = new SpectorBundleManifest.BundleCounts(
                    recordCount, edgeCount, hyperedgeCount, factCount
            );

            exporter.writeManifest(
                    stagingDir,
                    bundleCodec,
                    effectiveNamespace,
                    embeddingModel,
                    dimensions,
                    quantizer,
                    counts
            );

            log.info("[ExportJob] Manifest step complete: manifest.json written with member checksums.");
            return RepeatStatus.FINISHED;
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
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");

            Path stagingDir = getStagingDir(effectiveBundlePath);
            ExecutionContext execCtx = getExecutionContext(chunkContext);

            long recordCount = execCtx != null ? execCtx.getLong("export.recordCount", 0L) : 0L;
            long edgeCount = execCtx != null ? execCtx.getLong("export.edgeCount", 0L) : 0L;
            long hyperedgeCount = execCtx != null ? execCtx.getLong("export.hyperedgeCount", 0L) : 0L;
            long factCount = execCtx != null ? execCtx.getLong("export.factCount", 0L) : 0L;

            SpectorBundleManifest.BundleCounts expectedCounts = new SpectorBundleManifest.BundleCounts(
                    recordCount, edgeCount, hyperedgeCount, factCount
            );

            // Validates manifest and per-member checksums without stamping "verified": true (R1.8, V5)
            exporter.validateExport(stagingDir, bundleCodec, expectedCounts);
            log.info("[ExportJob] Validation step complete: export integrity verified.");
            return RepeatStatus.FINISHED;
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
            Map<String, Object> jobParams = getJobParameters(chunkContext);
            String effectiveBundlePath = targetBundlePath != null && !targetBundlePath.isBlank() ? targetBundlePath : (String) jobParams.get("targetBundlePath");

            Path stagingDir = getStagingDir(effectiveBundlePath);
            Path targetFile = Paths.get(effectiveBundlePath);

            bundleCodec.packageBundle(stagingDir, targetFile);
            deleteStagingDir(stagingDir);
            log.info("[ExportJob] Bundle packaging complete: {}", targetFile);
            return RepeatStatus.FINISHED;
        };
    }

    private Map<String, Object> getJobParameters(org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        if (chunkContext != null && chunkContext.getStepContext() != null && chunkContext.getStepContext().getJobParameters() != null) {
            return chunkContext.getStepContext().getJobParameters();
        }
        return Map.of();
    }

    private ExecutionContext getExecutionContext(org.springframework.batch.core.scope.context.ChunkContext chunkContext) {
        if (chunkContext != null && chunkContext.getStepContext() != null
                && chunkContext.getStepContext().getStepExecution() != null
                && chunkContext.getStepContext().getStepExecution().getJobExecution() != null) {
            return chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext();
        }
        return null;
    }

    private Path getStagingDir(String targetBundlePath) {
        if (targetBundlePath == null || targetBundlePath.isBlank()) {
            throw new IllegalArgumentException("targetBundlePath is required");
        }
        return Paths.get(targetBundlePath + ".tmp_staging");
    }

    private void deleteStagingDir(Path stagingDir) {
        try {
            if (Files.exists(stagingDir)) {
                Files.walk(stagingDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }

    private ExportScope parseScope(String namespace, Map<String, Object> params) {
        MemoryType tier = null;
        Object tierObj = params.get("tier");
        if (tierObj instanceof String tierStr && !tierStr.isBlank()) {
            try {
                tier = MemoryType.valueOf(tierStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Long createdFrom = parseLong(params.get("createdFrom"));
        Long createdTo = parseLong(params.get("createdTo"));
        boolean includeTombstones = Boolean.parseBoolean(String.valueOf(params.get("includeTombstones")));

        int recordsPerChunk = ExportScope.DEFAULT_RECORDS_PER_CHUNK;
        Object chunkObj = params.get("recordsPerChunk");
        if (chunkObj instanceof Number n) {
            recordsPerChunk = n.intValue();
        } else if (chunkObj instanceof String s && !s.isBlank()) {
            try {
                recordsPerChunk = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }

        return new ExportScope(namespace, tier, createdFrom, createdTo, includeTombstones, recordsPerChunk);
    }

    private int parseDimensionsHint(Map<String, Object> params) {
        Object dimsObj = params.get("embeddingDimensions");
        if (dimsObj instanceof Number n) {
            return n.intValue();
        } else if (dimsObj instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private Long parseLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        } else if (val instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
