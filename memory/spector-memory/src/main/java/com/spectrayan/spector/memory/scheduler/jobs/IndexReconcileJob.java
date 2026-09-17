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

import com.spectrayan.spector.commons.concurrent.OnPlane;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import com.spectrayan.spector.memory.index.IndexReconcileEngine;
import com.spectrayan.spector.memory.index.IndexReconcileReport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for periodic cooperative reconciliation of derived index views (ADR-0082).
 *
 * <p>Executes on {@link ThreadPlane#PLATFORM_WRITER} within the {@code quartz-writer} pool,
 * ensuring strict isolation from low-latency user recall or ingestion threads.</p>
 *
 * @since 1.1.0
 */
@OnPlane(value = ThreadPlane.PLATFORM_WRITER, pool = "quartz-writer")
@DisallowConcurrentExecution
public final class IndexReconcileJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(IndexReconcileJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        Object engineObj = dataMap.get("indexReconcileEngine");
        IndexReconcileEngine engine = (IndexReconcileEngine) engineObj;

        if (engine == null) {
            log.debug("IndexReconcileJob: indexReconcileEngine missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("IndexReconcileJob: running derived index reconciliation for namespace [{}]", ns);
            IndexReconcileReport report = engine.reconcile();
            if (report.hasRepairs()) {
                log.info("IndexReconcileJob [{}] repaired drift: {}", ns, report);
            }
        } catch (Exception e) {
            throw new JobExecutionException("Index reconciliation failed: " + e.getMessage(), e);
        }
    }
}
