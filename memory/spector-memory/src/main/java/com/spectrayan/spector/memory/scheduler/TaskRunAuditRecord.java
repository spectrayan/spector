/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.scheduler;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

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
