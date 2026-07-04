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

import java.util.Map;

/**
 * Interface for agent tools — actions that an agent can execute.
 *
 * <p>Tools are discovered via Spring component scan and registered in the
 * {@link ToolRegistry}. Each tool has a name, description, and parameter schema
 * that gets exposed to the LLM for function calling.</p>
 */
public interface AgentTool {

    /** Unique tool name (e.g., "file_read", "web_search", "memory_recall"). */
    String name();

    /** Human-readable description for the LLM. */
    String description();

    /** JSON Schema describing the tool's parameters. */
    Map<String, Object> parameterSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments key-value arguments matching the parameter schema
     * @return tool execution result as a string
     */
    String execute(Map<String, Object> arguments);
}
