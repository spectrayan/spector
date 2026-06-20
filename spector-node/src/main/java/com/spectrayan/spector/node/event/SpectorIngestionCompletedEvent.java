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
) implements SpectorEvent {
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
