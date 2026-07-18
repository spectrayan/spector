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
package com.spectrayan.spector.synapse.agent;

import java.util.Collections;
import java.util.Map;

/**
 * Represents an action determined by the reasoning agent (e.g., executing a tool).
 *
 * <p>Part of the ReAct (Reasoning + Acting) pattern: Thought → Action → Observation.</p>
 *
 * @param toolName  name of the tool to execute
 * @param arguments tool arguments (key-value pairs)
 */
public record AgentAction(String toolName, Map<String, Object> arguments) {

    public AgentAction {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName cannot be null or empty");
        }
        if (arguments == null) {
            arguments = Collections.emptyMap();
        }
    }
}
