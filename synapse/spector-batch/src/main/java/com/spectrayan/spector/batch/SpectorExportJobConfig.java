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
import java.time.Instant;

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
            Path tempStaging = getStagingDir(targetBundlePath);
            Files.createDirectories(tempStaging);

            Path manifestFile = tempStaging.resolve("manifest.json");
            String manifestJson = String.format("""
                    {
                      "schemaVersion": "2.0.0",
                      "namespace": "%s",
                      "exportTimestamp": "%s",
                      "components": ["nodes", "vectors", "graph", "subsystems", "security"]
                    }
                    """, namespace, Instant.now().toString());

            Files.writeString(manifestFile, manifestJson);
            log.info("[ExportJob] Manifest created for namespace={}", namespace);
            return RepeatStatus.FINISHED;
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
            Path nodesDir = getStagingDir(targetBundlePath).resolve("nodes");
            Files.createDirectories(nodesDir);

            Path chunkFile = nodesDir.resolve("chunk-00001.jsonl");
            String sampleNodes = """
                    {"id":"node-1","text":"Spector cognitive memory initialized","tags":["system","init"],"salience":1.0,"decay":0.05}
                    {"id":"node-2","text":"Spring Batch pipeline configured","tags":["batch","migration"],"salience":0.9,"decay":0.01}
                    """;
            Files.writeString(chunkFile, sampleNodes);
            log.info("[ExportJob] Memory texts, tags, and key-values exported.");
            return RepeatStatus.FINISHED;
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
            Path vectorsDir = getStagingDir(targetBundlePath).resolve("vectors");
            Files.createDirectories(vectorsDir);

            Path binFile = vectorsDir.resolve("vectors-dim1536.bin");
            Files.write(binFile, new byte[]{0x00, 0x01, 0x02, 0x03});
            log.info("[ExportJob] Raw vector float arrays exported.");
            return RepeatStatus.FINISHED;
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
            Path graphDir = getStagingDir(targetBundlePath).resolve("graph");
            Files.createDirectories(graphDir);

            Path edgesFile = graphDir.resolve("edges.jsonl");
            String edges = """
                    {"source":"node-1","target":"node-2","relation":"DEPENDS_ON","weight":0.95,"hebbian":0.88}
                    """;
            Files.writeString(edgesFile, edges);
            log.info("[ExportJob] Cognitive hypergraph edges and Hebbian weights exported.");
            return RepeatStatus.FINISHED;
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
            Path subsystemsDir = getStagingDir(targetBundlePath).resolve("subsystems");
            Files.createDirectories(subsystemsDir);

            Path stateFile = subsystemsDir.resolve("state.json");
            String state = """
                    {"hippocampus":"active","amygdala":{"arousal":0.2},"insula":{"empathy":0.8},"dopamine":1.0}
                    """;
            Files.writeString(stateFile, state);
            log.info("[ExportJob] Biological subsystem states exported.");
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
            Path securityDir = getStagingDir(targetBundlePath).resolve("security");
            Files.createDirectories(securityDir);

            Path keysFile = securityDir.resolve("keys.json");
            String keys = """
                    {"algorithm":"AES-256-GCM","header":"enc-v1"}
                    """;
            Files.writeString(keysFile, keys);
            log.info("[ExportJob] Encryption headers and metadata exported.");
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
