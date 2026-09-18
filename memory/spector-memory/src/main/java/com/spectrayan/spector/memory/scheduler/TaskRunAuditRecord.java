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
package com.spectrayan.spector.memory.scheduler;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Immutable audit record capturing a single execution of a scheduled memory task.
 *
 * @param runId        unique execution run identifier (from Quartz fire instance ID or TSID)
 * @param taskId       task name (e.g. "sleep-consolidation", "rem-dreaming", "checkpoint")
 * @param namespaceId  namespace / tenant scope
 * @param startTime    execution start instant
 * @param endTime      execution completion instant
 * @param duration     duration of execution
 * @param status       completion status ("SUCCESS", "FAILED", "VETOED")
 * @param result       domain execution report (e.g. {@code DreamReport}, {@code ReflectionReport}), or null
 * @param errorMessage error description if failed, or null
 */
public record TaskRunAuditRecord(
        String runId,
        String taskId,
        String namespaceId,
        Instant startTime,
        Instant endTime,
        Duration duration,
        String status,
        Object result,
        String errorMessage
) implements Serializable {

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
