package com.spectrayan.spector.node.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.spectrayan.spector.events.NotificationScope;
import java.time.Instant;

/**
 * SSE event: {@code ingestion.progress} — emitted periodically during async ingestion.
 *
 * <p>Tracks chunking/embedding/storage progress for a single ingestion task.
 * Published every N chunks (or at meaningful milestones) to keep the UI
 * progress bar updated without flooding the event bus.</p>
 *
 * <p>User-scoped: when {@code userId} is set, only the owning user's SSE
 * connection receives this event via {@link NotificationScope.User}.</p>
 */
public record SpectorIngestionProgressEvent(
        String nodeId, Instant timestamp,
        String taskId, String description,
        int chunksStored, int totalChunks,
        int failures, double progressPercent,
        @JsonIgnore String userId
) implements SpectorEvent {
    /** Backward-compatible constructor — no user scoping (broadcast). */
    public SpectorIngestionProgressEvent(String nodeId, Instant timestamp,
            String taskId, String description,
            int chunksStored, int totalChunks,
            int failures, double progressPercent) {
        this(nodeId, timestamp, taskId, description,
                chunksStored, totalChunks, failures, progressPercent, null);
    }
    @Override public String eventType() { return "ingestion.progress"; }
    @Override public NotificationScope scope() {
        return userId != null ? NotificationScope.user(userId) : NotificationScope.BROADCAST;
    }
}
