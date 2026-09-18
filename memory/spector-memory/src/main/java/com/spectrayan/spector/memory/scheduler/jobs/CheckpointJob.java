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
import com.spectrayan.spector.memory.sync.CheckpointEngine;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing background WAL checkpointing and index syncing in disk mode.
 */
@OnPlane(value = ThreadPlane.PLATFORM_WRITER, pool = "quartz-writer")
@DisallowConcurrentExecution
public final class CheckpointJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CheckpointJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        Object engineObj = dataMap.get("checkpointEngine");
        if (engineObj == null) {
            engineObj = dataMap.get("checkpointDaemon");
        }
        CheckpointEngine engine = (CheckpointEngine) engineObj;

        if (engine == null) {
            log.debug("CheckpointJob: checkpointEngine missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("CheckpointJob: running storage checkpoint for namespace [{}]", ns);
            engine.checkpoint();
        } catch (Exception e) {
            throw new JobExecutionException("Storage checkpoint failed: " + e.getMessage(), e);
        }
    }
}
