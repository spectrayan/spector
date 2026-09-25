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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.spectrayan.spector.batch.SpectorMemoryResolver;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;

@Configuration
public class ReembedJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ReembedJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SpectorMemoryResolver memoryResolver;

    public ReembedJobConfig(JobRepository jobRepository,
                            PlatformTransactionManager transactionManager,
                            SpectorMemoryResolver memoryResolver) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.memoryResolver = memoryResolver;
    }

    @Bean
    public Job reembedJob() {
        return new JobBuilder("reembedJob", jobRepository)
                .start(reembedStep("default", 100L, false))
                .build();
    }

    @Bean
    @JobScope
    public Step reembedStep(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['batchSize']}") Long batchSizeParam,
            @Value("#{jobParameters['dryRun']}") Boolean dryRunParam) {

        int batchSize = batchSizeParam != null ? batchSizeParam.intValue() : 100;
        boolean dryRun = dryRunParam != null && dryRunParam;

        return new StepBuilder("reembedStep", jobRepository)
                .<CognitiveRecord, CognitiveRecord>chunk(batchSize, transactionManager)
                .reader(reembedItemReader(namespace != null ? namespace : "default"))
                .writer(reembedItemWriter(namespace != null ? namespace : "default", dryRun))
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<CognitiveRecord> reembedItemReader(
            @Value("#{jobParameters['namespace']}") String namespace) {
        String ns = (namespace != null && !namespace.isBlank()) ? namespace : "default";
        SpectorMemory memory = memoryResolver != null ? memoryResolver.resolve(ns) : null;
        if (memory == null) {
            log.warn("Unknown or unresolvable namespace: {}", ns);
            return () -> null;
        }

        List<String> ids = new ArrayList<>(memory.admin().index().orderedIds());
        List<CognitiveRecord> records = ids.stream()
                .map(memory::inspect)
                .filter(rec -> rec != null && !rec.isTombstoned())
                .collect(Collectors.toList());

        log.info("Found {} non-tombstoned records to re-embed in namespace '{}'", records.size(), namespace);
        
        return new ItemReader<CognitiveRecord>() {
            private final Iterator<CognitiveRecord> iterator = records.iterator();
            @Override
            public CognitiveRecord read() {
                return iterator.hasNext() ? iterator.next() : null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<CognitiveRecord> reembedItemWriter(
            @Value("#{jobParameters['namespace']}") String namespace,
            @Value("#{jobParameters['dryRun']}") Boolean dryRunParam) {
        
        boolean dryRun = dryRunParam != null && dryRunParam;
        
        return (Chunk<? extends CognitiveRecord> chunk) -> {
            SpectorMemory memory = memoryResolver.resolve(namespace);
            if (memory == null) {
                throw new IllegalStateException("Memory resolved to null during write for namespace: " + namespace);
            }

            for (CognitiveRecord rec : chunk.getItems()) {
                if (dryRun) {
                    log.info("[DRY RUN] Would re-embed memory '{}': {}", rec.id(), rec.text());
                } else {
                    var contextBuilder = com.spectrayan.spector.memory.model.RememberContext.builder();
                    if (rec.metadata() != null) {
                        rec.metadata().forEach((k, v) -> contextBuilder.metadata(k, String.valueOf(v)));
                    }
                    
                    memory.remember(rec.id(), rec.text(), rec.memoryType(), rec.source(), contextBuilder.build(), rec.tags());
                    log.debug("Re-embedded memory '{}'", rec.id());
                }
            }
            log.info("Processed batch of {} records (namespace='{}', dryRun={})", chunk.getItems().size(), namespace, dryRun);
        };
    }
}
