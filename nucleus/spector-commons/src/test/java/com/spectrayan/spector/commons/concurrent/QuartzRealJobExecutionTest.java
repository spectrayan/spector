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
package com.spectrayan.spector.commons.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuartzRealJobExecutionTest")
class QuartzRealJobExecutionTest {

    private Scheduler scheduler;
    private static final CountDownLatch writerLatch = new CountDownLatch(1);
    private static final CountDownLatch virtualLatch = new CountDownLatch(1);
    private static final AtomicBoolean writerIsVirtual = new AtomicBoolean(true);
    private static final AtomicReference<String> writerThreadName = new AtomicReference<>();
    private static final AtomicBoolean virtualIsVirtual = new AtomicBoolean(false);
    private static final AtomicReference<String> virtualThreadName = new AtomicReference<>();

    @OnPlane(value = ThreadPlane.PLATFORM_WRITER, pool = "quartz-writer")
    @DisallowConcurrentExecution
    public static class RealWriterJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            writerIsVirtual.set(Thread.currentThread().isVirtual());
            writerThreadName.set(Thread.currentThread().getName());
            writerLatch.countDown();
        }
    }

    public static class RealVirtualJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            virtualIsVirtual.set(Thread.currentThread().isVirtual());
            virtualThreadName.set(Thread.currentThread().getName());
            virtualLatch.countDown();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        SpectorQuartzThreadPool threadPool = new SpectorQuartzThreadPool();
        DirectSchedulerFactory.getInstance().createScheduler(
                "RealTestScheduler",
                "test-instance-1",
                threadPool,
                new RAMJobStore()
        );
        scheduler = DirectSchedulerFactory.getInstance().getScheduler("RealTestScheduler");
        scheduler.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    @DisplayName("Executes real Quartz Job with @OnPlane(PLATFORM_WRITER) on platform writer thread")
    void testRealQuartzJobDispatchesToPlatformWriter() throws Exception {
        JobDetail writerJob = JobBuilder.newJob(RealWriterJob.class)
                .withIdentity("realWriterJob", "test-tenant")
                .build();

        Trigger writerTrigger = TriggerBuilder.newTrigger()
                .withIdentity("realWriterTrigger", "test-tenant")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        scheduler.scheduleJob(writerJob, writerTrigger);

        boolean done = writerLatch.await(5, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(writerIsVirtual.get()).isFalse();
        assertThat(writerThreadName.get()).contains("spector-pool-writer-quartz-writer-test-tenant");
    }

    @Test
    @DisplayName("Executes unannotated real Quartz Job on virtual thread")
    void testRealQuartzJobDispatchesToVirtual() throws Exception {
        JobDetail virtualJob = JobBuilder.newJob(RealVirtualJob.class)
                .withIdentity("realVirtualJob", "test-tenant")
                .build();

        Trigger virtualTrigger = TriggerBuilder.newTrigger()
                .withIdentity("realVirtualTrigger", "test-tenant")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        scheduler.scheduleJob(virtualJob, virtualTrigger);

        boolean done = virtualLatch.await(5, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(virtualIsVirtual.get()).isTrue();
        assertThat(virtualThreadName.get()).contains("spector-vt-");
    }
}