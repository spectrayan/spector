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
package com.spectrayan.spector.batch.reflect;

import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectFilter;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.SessionSweepResult;
import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Spring Batch ItemProcessor that executes consolidation on a single episodic session work item.
 *
 * @since 1.5.0
 */
@Component
@StepScope
public class SessionConsolidationProcessor implements ItemProcessor<SessionWorkItem, SessionSweepResult> {

    private static final Logger log = LoggerFactory.getLogger(SessionConsolidationProcessor.class);

    private final String sweepId;

    public SessionConsolidationProcessor(@Value("#{jobParameters['sweepId']}") String sweepId) {
        this.sweepId = sweepId;
    }

    @Override
    public SessionSweepResult process(SessionWorkItem item) throws Exception {
        if (item == null) {
            return null;
        }

        ReflectJobContext ctx = ReflectJobRegistry.get(sweepId);
        if (ctx == null) {
            throw new IllegalStateException("Missing ReflectJobContext for sweepId: " + sweepId);
        }

        if (ctx.isTimeBudgetExpired()) {
            log.info("Time budget expired for sweepId: {}, skipping session {}", sweepId, item.sessionId());
            return null;
        }

        ReflectSweepSpec spec = ctx.spec();

        // 1. Check backpressure permit
        if (spec != null && spec.backpressure() != null) {
            if (spec.backpressure().shouldAbortSweep()) {
                log.warn("Backpressure policy requested sweep abort for sweepId: {}", sweepId);
                return null;
            }
            spec.backpressure().beforeSession(item);
        }

        // 2. Build single-session reflection spec
        ReflectFilter singleSessionFilter = ReflectFilter.builder()
                .sessionIds(Set.of(item.sessionId()))
                .build();

        ReflectSweepSpec singleSessionSpec = ReflectSweepSpec.builder()
                .sweepId(spec != null ? spec.sweepId() : sweepId)
                .filter(singleSessionFilter)
                .maxTurnsPerSession(spec != null ? spec.maxTurnsPerSession() : ReflectSweepSpec.DEFAULT_MAX_TURNS_PER_SESSION)
                .runCompanionRelays(false) // Spring Batch executes session-by-session; companion relays run at end
                .build();

        try {
            ReflectReport report = ctx.memory().admin().reflectKernel(singleSessionSpec);
            return SessionSweepResult.success(
                    item.sessionId(),
                    report.consolidatedCount(),
                    report.logTurnsConsolidated()
            );
        } catch (Exception ex) {
            log.error("Error consolidating session {} for sweepId {}: {}", item.sessionId(), sweepId, ex.getMessage(), ex);
            if (spec != null && spec.backpressure() != null) {
                spec.backpressure().onProviderFailure(ex);
            }
            throw ex;
        }
    }
}
