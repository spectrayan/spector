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
package com.spectrayan.spector.synapse.config.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Metadata descriptor for a single configuration field in Spector Synapse and Cortex.
 * Defines its type, default value, validation bounds, and runtime mutability mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfigFieldDescriptor(
        String key,
        Object defaultValue,
        String type,
        String description,
        String applyMode,
        List<String> options,
        Double min,
        Double max,
        Double step,
        boolean secret
) {
    public static ConfigFieldDescriptor of(String key, Object defaultValue, String type,
                                           String description, String applyMode) {
        return new ConfigFieldDescriptor(key, defaultValue, type, description, applyMode, null, null, null, null, false);
    }

    public static ConfigFieldDescriptor select(String key, Object defaultValue, String description,
                                               String applyMode, List<String> options) {
        return new ConfigFieldDescriptor(key, defaultValue, "select", description, applyMode, options, null, null, null, false);
    }

    public static ConfigFieldDescriptor number(String key, Number defaultValue, String description,
                                               String applyMode, double min, double max, double step) {
        return new ConfigFieldDescriptor(key, defaultValue, "number", description, applyMode, null, min, max, step, false);
    }

    public static ConfigFieldDescriptor bool(String key, boolean defaultValue, String description,
                                             String applyMode) {
        return new ConfigFieldDescriptor(key, defaultValue, "boolean", description, applyMode, null, null, null, null, false);
    }

    public static ConfigFieldDescriptor secret(String key, String defaultValue, String description,
                                               String applyMode) {
        return new ConfigFieldDescriptor(key, defaultValue, "string", description, applyMode, null, null, null, null, true);
    }
}
