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
package com.spectrayan.spector.synapse.agent.graph;

import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;

import java.util.Map;

/**
 * Core event bus listener for real-time streaming events from the agentic chat graph
 * (Issue #263, ADR-0084).
 *
 * <p>Implementations receive callbacks as the graph progresses through
 * LLM streaming, reasoning separation, tool executions, and final turn completion.</p>
 */
public interface AgentChatListener {

    /**
     * Emitted immediately upon connection accept and ID allocation (TTFT signal).
     */
    default void onSession(String sessionId, String turnId) {}

    /**
     * Emitted during Chain-of-Thought reasoning generation.
     */
    default void onThinking(String delta, long elapsedMs) {
        onThinking(delta);
    }

    /**
     * Canonical event emitted for visible assistant answer deltas.
     */
    default void onToken(String delta) {
        onContent(delta);
    }

    /**
     * Emitted when the LLM requests execution of an agent tool.
     */
    default void onToolCall(String callId, String name, Map<String, Object> arguments) {
        onToolCall(name, arguments);
    }

    /**
     * Emitted when a tool finishes execution.
     *
     * @param callId      correlated tool call ID
     * @param name        tool name
     * @param status      "success" or "failure"
     * @param preview     2 KiB truncated preview for wire serialization
     * @param fullPayload un-truncated full payload for JDBC persistence
     * @param elapsedMs   tool execution duration in milliseconds
     */
    default void onToolResult(String callId, String name, String status, String preview, String fullPayload, long elapsedMs) {
        onToolResult(name, preview, "success".equalsIgnoreCase(status));
    }

    /**
     * Emitted when turn execution terminates successfully.
     */
    default void onDone(String summary, TokenUsageDto usage) {
        onDone(summary);
    }

    /**
     * Emitted when an error occurs during turn execution.
     */
    default void onError(String code, String message, boolean retryable) {
        onError(message);
    }

    // ── Backward Compatibility Shims ──────────────────────────────

    default void onThinking(String thought) {}

    default void onContent(String text) {}

    default void onToolCall(String name, Map<String, Object> arguments) {}

    default void onToolResult(String name, String result, boolean success) {}

    default void onToolResult(String callId, String name, String status, String preview, long elapsedMs) {
        onToolResult(callId, name, status, preview, preview, elapsedMs);
    }

    default void onDone(String summary) {}

    default void onError(String error) {}

    // ── No-Op Implementation ──────────────────────────────────────

    AgentChatListener NOOP = new AgentChatListener() {};
}
