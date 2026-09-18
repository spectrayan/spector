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
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Real-time status descriptor for a scheduled memory task.
 *
 * @param id               task identifier (e.g. "sleep-consolidation", "rem-dreaming")
 * @param namespaceId      namespace / tenant owning this task
 * @param state            trigger state (e.g. "NORMAL", "PAUSED", "BLOCKED", "ERROR", "NONE")
 * @param previousFireTime timestamp of previous execution (or null if never executed)
 * @param nextFireTime     timestamp of next scheduled execution (or null if not scheduled)
 * @param description      human-readable description of the background task
 * @param metadata         additional task-specific attributes
 */
public record TaskStatus(
        String id,
        String namespaceId,
        String state,
        Date previousFireTime,
        Date nextFireTime,
        String description,
        Map<String, Object> metadata
) implements Serializable {

    public TaskStatus(String id, String namespaceId, String state, Date previousFireTime, Date nextFireTime) {
        this(id, namespaceId, state, previousFireTime, nextFireTime, null, Map.of());
    }

    public TaskStatus(String id, String namespaceId, String state, Date previousFireTime, Date nextFireTime, String description) {
        this(id, namespaceId, state, previousFireTime, nextFireTime, description, Map.of());
    }
}
