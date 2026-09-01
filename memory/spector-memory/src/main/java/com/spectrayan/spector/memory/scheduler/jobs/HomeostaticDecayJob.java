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

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing periodic homeostatic synaptic decay and energetic stabilization.
 */
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
