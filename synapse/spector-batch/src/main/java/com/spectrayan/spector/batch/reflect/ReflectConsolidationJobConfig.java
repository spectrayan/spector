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
package com.spectrayan.spector.batch.reflect;

import com.spectrayan.spector.memory.pathway.reflect.SessionSweepResult;
import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration defining the reflect consolidation job and chunk=1 step.
 *
 * @since 1.5.0
 */
@Configuration
public class ReflectConsolidationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SessionWorkItemReader reader;
    private final SessionConsolidationProcessor processor;
    private final SessionSweepResultWriter writer;

    public ReflectConsolidationJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SessionWorkItemReader reader,
            SessionConsolidationProcessor processor,
            SessionSweepResultWriter writer) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
    }

    @Bean
    public Job reflectConsolidationJob() {
        return new JobBuilder("reflectConsolidationJob", jobRepository)
                .start(reflectConsolidationStep())
                .build();
    }

    @Bean
    public Step reflectConsolidationStep() {
        return new StepBuilder("reflectConsolidationStep", jobRepository)
                .<SessionWorkItem, SessionSweepResult>chunk(1, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(Exception.class)
                .build();
    }
}
