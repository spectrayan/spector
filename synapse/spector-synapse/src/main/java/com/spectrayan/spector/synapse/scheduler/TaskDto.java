/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.scheduler;

public final class TaskDto {

    private TaskDto() {}

    public record TaskActionResponse(
            String status,
            String taskId,
            String message
    ) {}

    public record RescheduleIntervalRequest(
            long intervalSeconds
    ) {}

    public record RescheduleCronRequest(
            String cronExpression
    ) {}
}
