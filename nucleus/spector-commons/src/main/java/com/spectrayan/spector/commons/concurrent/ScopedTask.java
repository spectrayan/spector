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
package com.spectrayan.spector.commons.concurrent;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Generic task wrapper carrying an arbitrary payload alongside Java 25 {@link MemoryScope}
 * session and namespace context, distributed trace context (W3C traceparent), submission
 * timestamp, and execution priority.
 *
 * @param <T> payload type
 */
public record ScopedTask<T>(
        String taskId,
        T payload,
        String sessionId,
        String namespaceId,
        TaskPriority priority,
        long submittedAtMs,
        String traceContext
) implements Comparable<ScopedTask<T>> {

    public ScopedTask(
            String taskId,
            T payload,
            String sessionId,
            String namespaceId,
            TaskPriority priority,
            long submittedAtMs) {
        this(taskId, payload, sessionId, namespaceId, priority, submittedAtMs, null);
    }

    public ScopedTask {
        if (taskId == null || taskId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "taskId");
        }
        if (payload == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "payload");
        }
        if (priority == null) {
            priority = TaskPriority.NORMAL;
        }
    }

    /**
     * Creates a scoped task with current thread {@link MemoryScope} context and default priority.
     */
    public static <T> ScopedTask<T> of(String taskId, T payload) {
        return new ScopedTask<>(
                taskId,
                payload,
                MemoryScope.sessionId(),
                MemoryScope.namespaceId(),
                TaskPriority.NORMAL,
                System.currentTimeMillis(),
                null
        );
    }

    /**
     * Creates a scoped task with current thread {@link MemoryScope} context and specified priority.
     */
    public static <T> ScopedTask<T> of(String taskId, T payload, TaskPriority priority) {
        return new ScopedTask<>(
                taskId,
                payload,
                MemoryScope.sessionId(),
                MemoryScope.namespaceId(),
                priority,
                System.currentTimeMillis(),
                null
        );
    }

    /**
     * Explicit constructor helper specifying session, namespace, and priority context.
     */
    public static <T> ScopedTask<T> of(String taskId, T payload, String sessionId,
                                       String namespaceId, TaskPriority priority) {
        return new ScopedTask<>(
                taskId,
                payload,
                sessionId,
                namespaceId,
                priority != null ? priority : TaskPriority.NORMAL,
                System.currentTimeMillis(),
                null
        );
    }

    /**
     * Explicit constructor helper specifying session, namespace, priority, and trace context.
     */
    public static <T> ScopedTask<T> of(String taskId, T payload, String sessionId,
                                       String namespaceId, TaskPriority priority, String traceContext) {
        return new ScopedTask<>(
                taskId,
                payload,
                sessionId,
                namespaceId,
                priority != null ? priority : TaskPriority.NORMAL,
                System.currentTimeMillis(),
                traceContext
        );
    }

    @Override
    public int compareTo(ScopedTask<T> other) {
        if (other == null) return 1;
        // Higher priority first
        int pDiff = Integer.compare(other.priority.level(), this.priority.level());
        if (pDiff != 0) return pDiff;
        // FIFO order for equal priority
        return Long.compare(this.submittedAtMs, other.submittedAtMs);
    }
}
