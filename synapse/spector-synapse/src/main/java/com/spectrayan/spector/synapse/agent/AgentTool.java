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

import java.util.Map;

/**
 * Interface for agent tools — actions that an agent can execute.
 *
 * <p>Tools are discovered via Spring component scan and registered in the
 * {@link ToolRegistry}. Each tool has a name, description, and parameter schema
 * that gets exposed to the LLM for function calling.</p>
 *
 * <h3>Spring AI Alignment</h3>
 * <p>This interface is designed to align with Spring AI's {@code ToolCallback} pattern.
 * The {@link ToolRegistry} bridges these tools to LangGraph4j-Spring AI's tool
 * execution framework, making them automatically available to agentic graphs.</p>
 *
 * <h3>Spector Extensions</h3>
 * <ul>
 *   <li>{@link #isWriteTool()} — marks tools requiring supervised execution (approval)</li>
 *   <li>{@link #category()} — groups tools in the UI for better organization</li>
 * </ul>
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

    /**
     * Whether this tool performs write/mutation operations.
     *
     * <p>Write tools require explicit user approval before execution in supervised
     * mode (e.g., file writes, shell commands, memory mutations).</p>
     *
     * @return {@code true} if the tool mutates state; {@code false} for read-only tools
     */
    default boolean isWriteTool() {
        return false;
    }

    /**
     * Category for UI grouping and filtering.
     *
     * @return the tool category
     */
    default ToolCategory category() {
        return ToolCategory.GENERAL;
    }

    /**
     * Categories for organizing tools in the UI and marketplace.
     */
    enum ToolCategory {
        /** General-purpose tools (time, search). */
        GENERAL,
        /** Memory operations (recall, remember, reinforce). */
        MEMORY,
        /** File system operations (read, write). */
        FILESYSTEM,
        /** Network/HTTP operations. */
        NETWORK,
        /** System operations (shell, process). */
        SYSTEM,
        /** Data transformation and analysis. */
        DATA
    }
}
