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
package com.spectrayan.spector.connector.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Execution record for a connector route run.
 *
 * @param routeId       the route that ran
 * @param tenantId      tenant isolation
 * @param status        execution status
 * @param documentsProcessed number of documents ingested
 * @param errors        number of errors
 * @param duration      how long the execution took
 * @param startedAt     when the execution started
 * @param errorMessage  error details (null if successful)
 */
public record ExecutionRecord(
        String routeId,
        String tenantId,
        ExecutionStatus status,
        int documentsProcessed,
        int errors,
        Duration duration,
        Instant startedAt,
        String errorMessage
) {
    public enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    /** Creates a successful execution record. */
    public static ExecutionRecord success(String routeId, String tenantId, int docs, Duration duration) {
        return new ExecutionRecord(routeId, tenantId, ExecutionStatus.COMPLETED, docs, 0, duration, Instant.now(), null);
    }

    /** Creates a failed execution record. */
    public static ExecutionRecord failure(String routeId, String tenantId, int docs, int errors, Duration duration, String message) {
        return new ExecutionRecord(routeId, tenantId, ExecutionStatus.FAILED, docs, errors, duration, Instant.now(), message);
    }

    /** Creates a running execution record. */
    public static ExecutionRecord running(String routeId, String tenantId) {
        return new ExecutionRecord(routeId, tenantId, ExecutionStatus.RUNNING, 0, 0, Duration.ZERO, Instant.now(), null);
    }
}
