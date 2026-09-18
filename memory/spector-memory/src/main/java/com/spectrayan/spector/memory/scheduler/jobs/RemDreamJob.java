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

import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;
import com.spectrayan.spector.commons.concurrent.OnPlane;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing periodic offline REM and creative dreaming cycles.
 */
@OnPlane(value = ThreadPlane.VIRTUAL, pool = "quartz-io")
@DisallowConcurrentExecution
public final class RemDreamJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RemDreamJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        DreamPathway dreamPathway = (DreamPathway) dataMap.get("dreamPathway");
        PartitionManager partitionManager = (PartitionManager) dataMap.get("partitionManager");
        AismeProperties aismeConfig = (AismeProperties) dataMap.get("aismeConfig");

        if (dreamPathway == null) {
            log.debug("RemDreamJob: dreamPathway missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("RemDreamJob: running offline REM dream cycle for namespace [{}]", ns);
            DreamReport report = dreamPathway.dream(DreamMode.REM, partitionManager, aismeConfig);
            context.setResult(report);
        } catch (Exception e) {
            throw new JobExecutionException("REM dream cycle failed: " + e.getMessage(), e);
        }
    }
}
