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
