/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.scheduler.jobs;

import com.spectrayan.spector.memory.pathway.DreamPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamReport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing periodic offline REM and creative dreaming cycles.
 */
@DisallowConcurrentExecution
public final class RemDreamJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RemDreamJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        DreamPathway dreamPathway = (DreamPathway) dataMap.get("dreamPathway");
        PartitionManager partitionManager = (PartitionManager) dataMap.get("partitionManager");
        AismeConfig aismeConfig = (AismeConfig) dataMap.get("aismeConfig");

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
