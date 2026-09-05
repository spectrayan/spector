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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.memory.pathway.reflect.SessionSweepResult;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Context holder maintaining execution state for a reflection sweep orchestrated by Spring Batch.
 *
 * @since 1.5.0
 */
public class ReflectJobContext {

    private final SpectorMemory memory;
    private final ReflectSweepSpec spec;
    private final ReflectCheckpointStore checkpointStore;
    private final Instant startTime;
    private final Instant deadline;

    private final AtomicInteger sessionsCompleted = new AtomicInteger();
    private final AtomicInteger factsIngested = new AtomicInteger();
    private final AtomicInteger turnsMarked = new AtomicInteger();
    private final AtomicLong lastCompletedSessionId = new AtomicLong();

    public ReflectJobContext(SpectorMemory memory, ReflectSweepSpec spec, ReflectCheckpointStore checkpointStore) {
        this.memory = memory;
        this.spec = spec;
        this.checkpointStore = checkpointStore;
        this.startTime = Instant.now();
        this.deadline = (spec != null && spec.timeBudget() != null) ? startTime.plus(spec.timeBudget()) : null;
    }

    public SpectorMemory memory() {
        return memory;
    }

    public ReflectSweepSpec spec() {
        return spec;
    }

    public ReflectCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    public Instant startTime() {
        return startTime;
    }

    public boolean isTimeBudgetExpired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }

    public void recordResult(SessionSweepResult result) {
        if (result != null) {
            sessionsCompleted.incrementAndGet();
            factsIngested.addAndGet(result.factsConsolidated());
            turnsMarked.addAndGet(result.turnsMarked());
            lastCompletedSessionId.set(result.sessionId());
        }
    }

    public int sessionsCompleted() {
        return sessionsCompleted.get();
    }

    public int factsIngested() {
        return factsIngested.get();
    }

    public int turnsMarked() {
        return turnsMarked.get();
    }

    public long lastCompletedSessionId() {
        return lastCompletedSessionId.get();
    }
}
