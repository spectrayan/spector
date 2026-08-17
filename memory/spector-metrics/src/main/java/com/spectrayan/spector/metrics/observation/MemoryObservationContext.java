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
package com.spectrayan.spector.metrics.observation;

import io.micrometer.observation.Observation;

import java.util.HashMap;
import java.util.Map;

/**
 * Micrometer Observation Context holding cognitive memory operation metadata,
 * session IDs, namespace context, and execution status.
 */
public class MemoryObservationContext extends Observation.Context {

    private final String operation;
    private String tier;
    private String namespace;
    private String sessionId;
    private String memoryId;
    private String query;
    private String taskId;
    private String status = "SUCCESS";
    private final Map<String, String> customTags = new HashMap<>();

    public MemoryObservationContext(String operation) {
        this.operation = operation != null ? operation : "unknown";
    }

    public String getOperation() {
        return operation;
    }

    public String getTier() {
        return tier;
    }

    public MemoryObservationContext setTier(String tier) {
        this.tier = tier;
        return this;
    }

    public String getNamespace() {
        return namespace;
    }

    public MemoryObservationContext setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public MemoryObservationContext setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public MemoryObservationContext setMemoryId(String memoryId) {
        this.memoryId = memoryId;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public MemoryObservationContext setQuery(String query) {
        this.query = query;
        return this;
    }

    public String getTaskId() {
        return taskId;
    }

    public MemoryObservationContext setTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public MemoryObservationContext setStatus(String status) {
        this.status = status;
        return this;
    }

    public Map<String, String> getCustomTags() {
        return customTags;
    }

    public MemoryObservationContext addCustomTag(String key, String value) {
        if (key != null && value != null) {
            this.customTags.put(key, value);
        }
        return this;
    }
}
