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
import org.quartz.SchedulerConfigException;
import org.quartz.SchedulerException;
import org.quartz.core.JobRunShell;
import org.quartz.core.JobRunShellFactory;
import org.quartz.spi.TriggerFiredBundle;

/**
 * Quartz {@link JobRunShellFactory} SPI implementation that instantiates {@link SpectorJobRunShell}
 * for non-reflective plane routing with {@link SpectorQuartzThreadPool}.
 */
public final class SpectorJobRunShellFactory implements JobRunShellFactory {

    private Scheduler scheduler;

    @Override
    public void initialize(Scheduler scheduler) throws SchedulerConfigException {
        this.scheduler = scheduler;
    }

    @Override
    public JobRunShell createJobRunShell(TriggerFiredBundle bundle) throws SchedulerException {
        return new SpectorJobRunShell(scheduler, bundle);
    }
}