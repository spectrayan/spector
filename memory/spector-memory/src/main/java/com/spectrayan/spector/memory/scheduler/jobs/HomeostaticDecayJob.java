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
 * Quartz Job for executing periodic homeostatic synaptic decay and energetic stabilization.
 */
@OnPlane(value = ThreadPlane.PLATFORM_WRITER, pool = "quartz-writer")
@DisallowConcurrentExecution
public final class HomeostaticDecayJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(HomeostaticDecayJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        Runnable decayDaemon = (Runnable) dataMap.get("decayDaemon");

        if (decayDaemon == null) {
            log.debug("HomeostaticDecayJob: decayDaemon missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("HomeostaticDecayJob: running homeostatic decay for namespace [{}]", ns);
            decayDaemon.run();
        } catch (Exception e) {
            throw new JobExecutionException("Homeostatic decay failed: " + e.getMessage(), e);
        }
    }
}
