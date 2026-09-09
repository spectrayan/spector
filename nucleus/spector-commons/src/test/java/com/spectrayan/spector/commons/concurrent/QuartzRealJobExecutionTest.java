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
import org.quartz.simpl.RAMJobStore;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuartzRealJobExecutionTest")
class QuartzRealJobExecutionTest {

    private Scheduler scheduler;
    private final CountDownLatch writerLatch = new CountDownLatch(1);
    private final CountDownLatch virtualLatch = new CountDownLatch(1);
    private final CountDownLatch dataMapLatch = new CountDownLatch(1);
    private final AtomicBoolean writerIsVirtual = new AtomicBoolean(true);
    private final AtomicReference<String> writerThreadName = new AtomicReference<>();
    private final AtomicBoolean virtualIsVirtual = new AtomicBoolean(false);
    private final AtomicReference<String> virtualThreadName = new AtomicReference<>();
    private final AtomicBoolean dataMapIsVirtual = new AtomicBoolean(true);
    private final AtomicReference<String> dataMapThreadName = new AtomicReference<>();

    @OnPlane(value = ThreadPlane.PLATFORM_WRITER, pool = "quartz-writer")
    @DisallowConcurrentExecution
    public static class RealWriterJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                QuartzRealJobExecutionTest test = (QuartzRealJobExecutionTest) context.getScheduler().getContext().get("testInstance");
                test.writerIsVirtual.set(Thread.currentThread().isVirtual());
                test.writerThreadName.set(Thread.currentThread().getName());
                test.writerLatch.countDown();
            } catch (Exception e) {
                throw new JobExecutionException(e);
            }
        }
    }

    public static class RealVirtualJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                QuartzRealJobExecutionTest test = (QuartzRealJobExecutionTest) context.getScheduler().getContext().get("testInstance");
                test.virtualIsVirtual.set(Thread.currentThread().isVirtual());
                test.virtualThreadName.set(Thread.currentThread().getName());
                test.virtualLatch.countDown();
            } catch (Exception e) {
                throw new JobExecutionException(e);
            }
        }
    }

    public static class FallbackDataMapJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                QuartzRealJobExecutionTest test = (QuartzRealJobExecutionTest) context.getScheduler().getContext().get("testInstance");
                test.dataMapIsVirtual.set(Thread.currentThread().isVirtual());
                test.dataMapThreadName.set(Thread.currentThread().getName());
                test.dataMapLatch.countDown();
            } catch (Exception e) {
                throw new JobExecutionException(e);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        scheduler = SpectorSchedulerFactory.createScheduler(
                "RealTestScheduler-" + System.nanoTime(),
                "test-instance-1",
                new SpectorQuartzThreadPool(),
                new RAMJobStore()
        );
        scheduler.getContext().put("testInstance", this);
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

    @Test
    @DisplayName("Executes unannotated real Quartz Job with JOB_DATA_PLANE fallback on platform writer")
    void testRealQuartzJobDispatchesViaJobDataMapFallback() throws Exception {
        JobDetail dataMapJob = JobBuilder.newJob(FallbackDataMapJob.class)
                .withIdentity("fallbackJob", "test-tenant")
                .usingJobData(SpectorQuartzThreadPool.JOB_DATA_PLANE, ThreadPlane.PLATFORM_WRITER.name())
                .build();

        Trigger dataMapTrigger = TriggerBuilder.newTrigger()
                .withIdentity("fallbackTrigger", "test-tenant")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        scheduler.scheduleJob(dataMapJob, dataMapTrigger);

        boolean done = dataMapLatch.await(5, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(dataMapIsVirtual.get()).isFalse();
        assertThat(dataMapThreadName.get()).contains("spector-pool-writer-quartz-writer-test-tenant");
    }

    @Test
    @DisplayName("Executes dummy non-JobRunShell PlaneAwareRunnable directly via threadPool.runInThread")
    void testDummyPlaneAwareRunnableDispatchesDirectly() throws Exception {
        SpectorQuartzThreadPool pool = new SpectorQuartzThreadPool();
        pool.initialize();
        CountDownLatch dummyLatch = new CountDownLatch(1);
        AtomicBoolean dummyIsVirtual = new AtomicBoolean(true);
        AtomicReference<String> dummyThreadName = new AtomicReference<>();

        PlaneAwareRunnable runnable = new PlaneAwareRunnable(
                ThreadPlane.PLATFORM_WRITER,
                "dummy-pool",
                () -> {
                    dummyIsVirtual.set(Thread.currentThread().isVirtual());
                    dummyThreadName.set(Thread.currentThread().getName());
                    dummyLatch.countDown();
                }
        );

        boolean accepted = pool.runInThread(runnable);
        assertThat(accepted).isTrue();
        assertThat(dummyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dummyIsVirtual.get()).isFalse();
        assertThat(dummyThreadName.get()).contains("spector-pool-writer-dummy-pool");
        pool.shutdown(true);
    }
}
