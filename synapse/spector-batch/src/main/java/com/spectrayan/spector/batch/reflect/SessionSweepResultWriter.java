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

import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepStatus;
import com.spectrayan.spector.memory.pathway.reflect.SessionSweepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Spring Batch ItemWriter that records consolidated session metrics and persists durable checkpoints.
 *
 * @since 1.5.0
 */
@Component
@StepScope
public class SessionSweepResultWriter implements ItemWriter<SessionSweepResult> {

    private static final Logger log = LoggerFactory.getLogger(SessionSweepResultWriter.class);

    private final String sweepId;

    public SessionSweepResultWriter(@Value("#{jobParameters['sweepId']}") String sweepId) {
        this.sweepId = sweepId;
    }

    @Override
    public void write(Chunk<? extends SessionSweepResult> chunk) {
        ReflectJobContext ctx = ReflectJobRegistry.get(sweepId);
        if (ctx == null) {
            log.warn("Missing ReflectJobContext in writer for sweepId: {}", sweepId);
            return;
        }

        for (SessionSweepResult result : chunk) {
            if (result != null) {
                ctx.recordResult(result);
            }
        }

        if (ctx.checkpointStore() != null) {
            ReflectCheckpoint checkpoint = new ReflectCheckpoint(
                    sweepId,
                    0,
                    ctx.lastCompletedSessionId(),
                    0L,
                    ctx.sessionsCompleted(),
                    ctx.factsIngested(),
                    ctx.turnsMarked(),
                    0,
                    Instant.now(),
                    ReflectSweepStatus.RUNNING
            );
            ctx.checkpointStore().save(checkpoint);
        }
    }
}
