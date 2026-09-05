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

import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;
import com.spectrayan.spector.memory.pathway.reflect.relay.EpisodicLogConsolidationRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spring Batch ItemReader that reads candidate unconsolidated episodic sessions as SessionWorkItems.
 *
 * @since 1.5.0
 */
@Component
@StepScope
public class SessionWorkItemReader implements ItemReader<SessionWorkItem> {

    private static final Logger log = LoggerFactory.getLogger(SessionWorkItemReader.class);

    private final String sweepId;
    private Iterator<SessionWorkItem> iterator;
    private boolean initialized = false;

    public SessionWorkItemReader(@Value("#{jobParameters['sweepId']}") String sweepId) {
        this.sweepId = sweepId;
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ReflectJobContext ctx = ReflectJobRegistry.get(sweepId);
        if (ctx == null) {
            log.warn("No ReflectJobContext registered for sweepId: {}", sweepId);
            this.iterator = List.<SessionWorkItem>of().iterator();
            return;
        }

        if (ctx.memory() == null || ctx.memory().admin() == null || ctx.memory().admin().cognitiveRouter() == null) {
            log.warn("Memory router not available for sweepId: {}", sweepId);
            this.iterator = List.<SessionWorkItem>of().iterator();
            return;
        }

        EpisodicMemory logStore = ctx.memory().admin().cognitiveRouter().episodic();
        if (logStore == null) {
            log.warn("No EpisodicMemory log store available for sweepId: {}", sweepId);
            this.iterator = List.<SessionWorkItem>of().iterator();
            return;
        }

        ReflectCheckpoint checkpoint = null;
        if (ctx.checkpointStore() != null) {
            checkpoint = ctx.checkpointStore().load(sweepId).orElse(null);
        }

        EpisodicLogConsolidationRelay relay = new EpisodicLogConsolidationRelay();
        List<SessionWorkItem> items = relay.listEligibleSessions(
                logStore,
                ctx.spec(),
                checkpoint,
                null,
                0
        );

        if (ctx.spec() != null && ctx.spec().sessionLimit() > 0 && items.size() > ctx.spec().sessionLimit()) {
            items = items.subList(0, ctx.spec().sessionLimit());
        }

        log.info("SessionWorkItemReader discovered {} candidate sessions for sweepId: {}", items.size(), sweepId);
        this.iterator = new ArrayList<>(items).iterator();
    }

    @Override
    public SessionWorkItem read() {
        initialize();

        ReflectJobContext ctx = ReflectJobRegistry.get(sweepId);
        if (ctx != null && ctx.isTimeBudgetExpired()) {
            log.info("Time budget expired for sweepId: {}, stopping SessionWorkItemReader", sweepId);
            return null;
        }

        if (iterator != null && iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }
}
