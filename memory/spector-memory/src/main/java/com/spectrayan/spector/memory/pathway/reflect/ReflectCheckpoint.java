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
package com.spectrayan.spector.memory.pathway.reflect;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable execution checkpoint tracking progress of a reflection sweep.
 *
 * @param sweepId                  unique identifier for the sweep execution or schedule
 * @param partitionSeq             sequence ID of the active memory partition
 * @param lastCompletedSessionId   watermark for the last successfully consolidated session ID (0 if none)
 * @param lastCompletedTurnOffset  byte offset of the last consolidated turn in the episodic mmap region
 * @param sessionsCompleted        total sessions successfully processed in this sweep
 * @param factsIngested            total semantic facts synthesized and ingested into memory
 * @param turnsMarked              total episodic turns stamped as consolidated
 * @param backlogRemaining         estimated count of unconsolidated sessions remaining
 * @param updatedAt                timestamp when this checkpoint was updated
 * @param status                   current lifecycle status of the sweep
 * @since 1.5.0
 */
public record ReflectCheckpoint(
        String sweepId,
        int partitionSeq,
        long lastCompletedSessionId,
        long lastCompletedTurnOffset,
        int sessionsCompleted,
        int factsIngested,
        int turnsMarked,
        int backlogRemaining,
        Instant updatedAt,
        ReflectSweepStatus status
) {
    public ReflectCheckpoint {
        Objects.requireNonNull(sweepId, "sweepId cannot be null");
        updatedAt = (updatedAt != null) ? updatedAt : Instant.now();
        status = (status != null) ? status : ReflectSweepStatus.IDLE;
    }

    public static ReflectCheckpoint initial(String sweepId) {
        return new ReflectCheckpoint(
                sweepId,
                0,
                0L,
                0L,
                0,
                0,
                0,
                0,
                Instant.now(),
                ReflectSweepStatus.IDLE
        );
    }
}
