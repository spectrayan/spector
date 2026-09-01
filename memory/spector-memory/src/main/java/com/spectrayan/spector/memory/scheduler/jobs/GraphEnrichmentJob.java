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

import com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing background entity graph extraction and hypergraph relation enrichment.
 */
@DisallowConcurrentExecution
public final class GraphEnrichmentJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(GraphEnrichmentJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        GraphEnrichmentDaemon daemon = (GraphEnrichmentDaemon) dataMap.get("graphEnrichmentDaemon");

        if (daemon == null) {
            log.debug("GraphEnrichmentJob: graphEnrichmentDaemon missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("GraphEnrichmentJob: running graph enrichment for namespace [{}]", ns);
            daemon.enrichPending();
        } catch (Exception e) {
            throw new JobExecutionException("Graph enrichment failed: " + e.getMessage(), e);
        }
    }
}
