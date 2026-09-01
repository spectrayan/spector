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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.model.ReflectReport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Quartz Job for executing periodic hippocampal sleep consolidation (episodic-to-semantic promotion and Hebbian decay).
 * <p>
 * Decoupled from the {@code SpectorMemory} god-object by taking a functional {@code reflectAction} supplier.
 */
@DisallowConcurrentExecution
public final class SleepConsolidationJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(SleepConsolidationJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Object action = context.getMergedJobDataMap().get("reflectAction");
        if (action == null) {
            log.warn("SleepConsolidationJob: reflectAction missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = context.getMergedJobDataMap().getString("namespaceId");
            log.debug("SleepConsolidationJob: running sleep consolidation for namespace [{}]", ns);

            if (action instanceof Supplier<?> supplier) {
                Object result = supplier.get();
                if (result instanceof ReflectReport report) {
                    context.setResult(report);
                }
            } else if (action instanceof Runnable runnable) {
                runnable.run();
            }
        } catch (Exception e) {
            throw new JobExecutionException("Sleep consolidation failed: " + e.getMessage(), e);
        }
    }
}
