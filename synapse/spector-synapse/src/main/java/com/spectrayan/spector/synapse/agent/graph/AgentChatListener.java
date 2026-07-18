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
package com.spectrayan.spector.synapse.agent.graph;

import java.util.Map;

/**
 * Listener for real-time streaming events from the agentic chat graph.
 *
 * <p>Implementations receive callbacks as the graph progresses through
 * LLM calls, tool executions, and final content generation. Used by
 * the chat service for SSE streaming to the Cortex UI.</p>
 */
public interface AgentChatListener {

    /** Called when the agent is thinking (LLM processing). */
    void onThinking(String thought);

    /** Called when a tool call is requested by the LLM. */
    void onToolCall(String name, Map<String, Object> arguments);

    /** Called when a tool execution completes. */
    void onToolResult(String name, String result, boolean success);

    /** Called when content is generated. */
    void onContent(String text);

    /** Called when the graph execution completes. */
    void onDone(String summary);

    /** Called when an error occurs. */
    void onError(String error);

    /** No-op listener for non-interactive callers. */
    AgentChatListener NOOP = new AgentChatListener() {
        @Override public void onThinking(String thought) {}
        @Override public void onToolCall(String name, Map<String, Object> arguments) {}
        @Override public void onToolResult(String name, String result, boolean success) {}
        @Override public void onContent(String text) {}
        @Override public void onDone(String summary) {}
        @Override public void onError(String error) {}
    };
}
