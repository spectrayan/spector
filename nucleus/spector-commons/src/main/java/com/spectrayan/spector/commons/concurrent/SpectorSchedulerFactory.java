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

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.core.QuartzScheduler;
import org.quartz.core.QuartzSchedulerResources;
import org.quartz.impl.DefaultThreadExecutor;
import org.quartz.impl.SchedulerRepository;
import org.quartz.impl.StdScheduler;
import org.quartz.simpl.CascadingClassLoadHelper;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.spi.JobStore;

/**
 * Factory for creating Quartz {@link Scheduler} instances configured with {@link SpectorJobRunShellFactory}
 * and {@link SpectorQuartzThreadPool} to guarantee 100% non-reflective, dual-plane execution.
 */
public final class SpectorSchedulerFactory {

    private SpectorSchedulerFactory() {}

    /**
     * Creates and binds a standalone {@link Scheduler} configured with {@link SpectorJobRunShellFactory}.
     *
     * @param name       the scheduler name
     * @param instanceId the scheduler instance ID
     * @param threadPool the {@link SpectorQuartzThreadPool} to execute jobs
     * @param jobStore   the {@link JobStore} implementation
     * @return the initialized and bound {@link Scheduler}
     * @throws SchedulerException if scheduler initialization fails
     */
    public static Scheduler createScheduler(
            String name,
            String instanceId,
            SpectorQuartzThreadPool threadPool,
            JobStore jobStore) throws SchedulerException {

        threadPool.setInstanceName(name);
        threadPool.initialize();

        QuartzSchedulerResources resources = new QuartzSchedulerResources();
        resources.setName(name);
        resources.setInstanceId(instanceId);
        resources.setJobRunShellFactory(new SpectorJobRunShellFactory());
        resources.setThreadPool(threadPool);
        resources.setThreadExecutor(new DefaultThreadExecutor());
        resources.setJobStore(jobStore);

        QuartzScheduler qs = new QuartzScheduler(resources, 30000L, -1);
        ClassLoadHelper cch = new CascadingClassLoadHelper();
        cch.initialize();

        jobStore.setInstanceName(name);
        jobStore.setInstanceId(instanceId);
        jobStore.initialize(cch, qs.getSchedulerSignaler());

        Scheduler scheduler = new StdScheduler(qs);
        resources.getJobRunShellFactory().initialize(scheduler);
        qs.initialize();

        SchedulerRepository.getInstance().bind(scheduler);
        return scheduler;
    }
}
