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
package com.spectrayan.spector.synapse.cluster.mover;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable status record tracking the progress of an in-flight or completed namespace relocation (Req R5.4, R5.6).
 *
 * @param namespaceId  relocated namespace
 * @param sourceNodeId source owner node identifier
 * @param targetNodeId target owner node identifier
 * @param phase        current execution phase
 * @param targetEpoch  epoch assigned to the target owner
 * @param targetFence  fence token minted for the target owner
 * @param startedAt    timestamp when move initiated
 * @param completedAt  timestamp when move completed or terminated
 */
public record MoveStatus(
        String namespaceId,
        String sourceNodeId,
        String targetNodeId,
        MovePhase phase,
        long targetEpoch,
        String targetFence,
        Instant startedAt,
        Instant completedAt) {

    public MoveStatus {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
    }

    public static MoveStatus init(String namespaceId, String sourceNodeId, String targetNodeId, Instant now) {
        return new MoveStatus(namespaceId, sourceNodeId, targetNodeId, MovePhase.STOP_SOURCE_WRITES, 0L, null, now, null);
    }

    public MoveStatus withPhase(MovePhase newPhase, long epoch, String fence, Instant now) {
        Instant completion = (newPhase == MovePhase.COMPLETED || newPhase == MovePhase.FAILED) ? now : null;
        return new MoveStatus(namespaceId, sourceNodeId, targetNodeId, newPhase, epoch, fence, startedAt, completion);
    }
}
