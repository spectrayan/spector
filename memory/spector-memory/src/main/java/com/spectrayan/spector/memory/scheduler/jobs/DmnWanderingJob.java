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
package com.spectrayan.spector.memory.scheduler.jobs;

import com.spectrayan.spector.commons.concurrent.OnPlane;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing Default Mode Network (DMN) spontaneous mind-wandering and prospective associations.
 */
@OnPlane(value = ThreadPlane.PLATFORM_SHARED, pool = "quartz-shared")
@DisallowConcurrentExecution
public final class DmnWanderingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DmnWanderingJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        Runnable dmnDaemon = (Runnable) dataMap.get("dmnDaemon");

        if (dmnDaemon == null) {
            log.debug("DmnWanderingJob: dmnDaemon missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("DmnWanderingJob: running DMN wandering cycle for namespace [{}]", ns);
            dmnDaemon.run();
        } catch (Exception e) {
            throw new JobExecutionException("DMN wandering cycle failed: " + e.getMessage(), e);
        }
    }
}
