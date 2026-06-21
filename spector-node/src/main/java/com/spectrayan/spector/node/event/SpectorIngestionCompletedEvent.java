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
package com.spectrayan.spector.node.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.spectrayan.spector.events.NotificationScope;
import java.time.Instant;

/**
 * SSE event: {@code ingestion.completed} — emitted when an async ingestion task finishes.
 *
 * <p>Carries the final summary: total chunks stored, failures, duration,
 * and overall success/failure status. The UI uses this to show a
 * notification bell alert.</p>
 *
 * <p>User-scoped: when {@code userId} is set, only the owning user's SSE
 * connection receives this event via {@link NotificationScope.User}.</p>
 */
public record SpectorIngestionCompletedEvent(
        String nodeId, Instant timestamp,
        String taskId, String description,
        int chunksStored, int failures,
        long durationMs, boolean success,
        @JsonIgnore String userId
) implements SpectorNodeEvent {
    /** Backward-compatible constructor — no user scoping (broadcast). */
    public SpectorIngestionCompletedEvent(String nodeId, Instant timestamp,
            String taskId, String description,
            int chunksStored, int failures,
            long durationMs, boolean success) {
        this(nodeId, timestamp, taskId, description,
                chunksStored, failures, durationMs, success, null);
    }
    @Override public String eventType() { return "ingestion.completed"; }
    @Override public NotificationScope scope() {
        return userId != null ? NotificationScope.user(userId) : NotificationScope.BROADCAST;
    }
}
