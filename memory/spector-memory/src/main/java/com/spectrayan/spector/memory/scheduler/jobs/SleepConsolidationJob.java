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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing periodic hippocampal sleep consolidation (episodic-to-semantic promotion and Hebbian decay).
 */
@DisallowConcurrentExecution
public final class SleepConsolidationJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SleepConsolidationJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        SpectorMemory memory = (SpectorMemory) context.getMergedJobDataMap().get("memoryInstance");
        if (memory == null) {
            log.warn("SleepConsolidationJob: memoryInstance missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = context.getMergedJobDataMap().getString("namespaceId");
            log.debug("SleepConsolidationJob: running sleep consolidation for namespace [{}]", ns);
            ReflectReport report = memory.reflect();
            context.setResult(report);
        } catch (Exception e) {
            throw new JobExecutionException("Sleep consolidation failed: " + e.getMessage(), e);
        }
    }
}
