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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.FileReflectCheckpointStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * High-priority {@link ReflectSweepExecutor} that orchestrates reflection sweeps via Spring Batch.
 *
 * @since 1.5.0
 */
@Component
public class SpringBatchReflectSweepExecutor implements ReflectSweepExecutor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SpringBatchReflectSweepExecutor.class);
    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static void setStaticContext(ApplicationContext ctx) {
        context = ctx;
    }

    private static ReflectCheckpointStore defaultStore() {
        return new FileReflectCheckpointStore(Path.of(System.getProperty("java.io.tmpdir"), "spector-checkpoints"));
    }

    @Override
    public String name() {
        return "spring-batch";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean available() {
        if (context == null) {
            return false;
        }
        try {
            return context.getBeanNamesForType(JobLauncher.class).length > 0
                    && context.containsBean("reflectConsolidationJob");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ReflectReport execute(SpectorMemory memory, ReflectSweepSpec spec) {
        Objects.requireNonNull(memory, "memory cannot be null");
        Objects.requireNonNull(spec, "spec cannot be null");

        if (!available()) {
            throw new IllegalStateException("SpringBatchReflectSweepExecutor is not available in current ApplicationContext");
        }

        JobLauncher jobLauncher = context.getBean(JobLauncher.class);
        Job reflectJob = context.getBean("reflectConsolidationJob", Job.class);

        ReflectCheckpointStore checkpointStore = defaultStore();
        ReflectJobContext jobCtx = new ReflectJobContext(memory, spec, checkpointStore);
        ReflectJobRegistry.register(spec.sweepId(), jobCtx);

        Instant start = Instant.now();
        JobExecution jobExecution;
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("sweepId", spec.sweepId())
                    .addLong("sessionLimit", (long) spec.sessionLimit())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobExecution = jobLauncher.run(reflectJob, params);
        } catch (Exception e) {
            log.error("Failed launching reflectConsolidationJob for sweepId {}: {}", spec.sweepId(), e.getMessage(), e);
            throw new RuntimeException("Failed executing Spring Batch reflect sweep: " + e.getMessage(), e);
        } finally {
            ReflectJobRegistry.remove(spec.sweepId());
        }

        Duration duration = Duration.between(start, Instant.now());
        boolean success = jobExecution.getStatus() == BatchStatus.COMPLETED;

        // Final checkpoint save
        ReflectCheckpoint finalCheckpoint = new ReflectCheckpoint(
                spec.sweepId(),
                0,
                jobCtx.lastCompletedSessionId(),
                0L,
                jobCtx.sessionsCompleted(),
                jobCtx.factsIngested(),
                jobCtx.turnsMarked(),
                0,
                Instant.now(),
                success ? ReflectSweepStatus.COMPLETE : ReflectSweepStatus.FAILED
        );
        checkpointStore.save(finalCheckpoint);

        // If companion relays requested, conduct companion cycle via memory admin
        if (spec.runCompanionRelays() && memory.admin().reflectPathway() != null) {
            ReflectSweepSpec companionSpec = ReflectSweepSpec.builder()
                    .sweepId(spec.sweepId() + "-companion")
                    .sessionLimit(0)
                    .runCompanionRelays(true)
                    .build();
            memory.admin().reflectKernel(companionSpec);
        }

        return new ReflectReport(
                jobCtx.factsIngested(),
                0,
                0,
                0,
                duration,
                null,
                0,
                0,
                0.0f,
                jobCtx.turnsMarked()
        );
    }

    @Override
    public ReflectSweepProgress progress(String sweepId) {
        if (sweepId == null) {
            return null;
        }

        ReflectJobContext activeCtx = ReflectJobRegistry.get(sweepId);
        ReflectCheckpointStore checkpointStore = defaultStore();
        ReflectCheckpoint checkpoint = checkpointStore.load(sweepId).orElse(null);

        if (checkpoint != null) {
            Instant start = (activeCtx != null) ? activeCtx.startTime() : checkpoint.updatedAt();
            return ReflectSweepProgress.from(checkpoint, start);
        }

        if (activeCtx != null) {
            return new ReflectSweepProgress(
                    sweepId,
                    activeCtx.sessionsCompleted(),
                    activeCtx.factsIngested(),
                    activeCtx.turnsMarked(),
                    0,
                    ReflectSweepStatus.RUNNING,
                    activeCtx.startTime(),
                    Instant.now()
            );
        }

        return null;
    }
}
