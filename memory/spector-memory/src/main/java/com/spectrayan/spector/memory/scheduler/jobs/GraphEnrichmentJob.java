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
import com.spectrayan.spector.memory.graph.GraphEnrichmentEngine;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz Job for executing background entity graph extraction and hypergraph relation enrichment.
 */
@OnPlane(value = ThreadPlane.VIRTUAL, pool = "quartz")
@DisallowConcurrentExecution
public final class GraphEnrichmentJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(GraphEnrichmentJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getMergedJobDataMap();
        Object engineObj = dataMap.get("graphEnrichmentEngine");
        if (engineObj == null) {
            engineObj = dataMap.get("graphEnrichmentDaemon");
        }
        GraphEnrichmentEngine engine = (GraphEnrichmentEngine) engineObj;

        if (engine == null) {
            log.debug("GraphEnrichmentJob: graphEnrichmentEngine missing from JobDataMap — skipping");
            return;
        }

        try {
            String ns = dataMap.getString("namespaceId");
            log.debug("GraphEnrichmentJob: running graph enrichment for namespace [{}]", ns);
            engine.enrichPending();
        } catch (Exception e) {
            throw new JobExecutionException("Graph enrichment failed: " + e.getMessage(), e);
        }
    }
}
