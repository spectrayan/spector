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
package com.spectrayan.spector.synapse.agent.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spectrayan.spector.synapse.agent.chat.dto.ChatDto.TokenUsageDto;

import java.util.Map;

/**
 * Sealed hierarchy of typed Server-Sent Events (SSE) envelopes for streaming agentic chat
 * (Issue #263, ADR-0084).
 *
 * <p>Every event envelope guarantees strictly monotonic sequence numbering ({@code seq}),
 * consistent session and turn correlation IDs, and millisecond timestamps.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface ChatStreamEvent permits
        ChatStreamEvent.Session,
        ChatStreamEvent.Thinking,
        ChatStreamEvent.Token,
        ChatStreamEvent.ToolCall,
        ChatStreamEvent.ToolResult,
        ChatStreamEvent.Done,
        ChatStreamEvent.Error {

    /** Session correlation identifier. */
    String sessionId();

    /** Turn correlation identifier within the session. */
    String turnId();

    /** Strictly monotonic sequence counter starting at 0 for a given turn. */
    int seq();

    /** Milliseconds from Unix epoch when the event was emitted. */
    long tsEpochMs();

    /** Canonical SSE event type name on the wire. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    String eventType();

    /**
     * Session lifecycle event — pre-flushed immediately after TSID allocation (TTFT start).
     */
    record Session(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            boolean isNew,
            String model
    ) implements ChatStreamEvent {
        @Override
        public String eventType() {
            return "session";
        }
    }

    /**
     * Chain-of-Thought reasoning delta emitted during LLM cognitive processing.
     */
    record Thinking(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String text,
            long elapsedMs
    ) implements ChatStreamEvent {
        @Override
        public String eventType() {
            return "thinking";
        }
    }

    /**
     * Canonical visible assistant token delta emitted for user-facing markdown text.
     */
    record Token(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String text
    ) implements ChatStreamEvent {
        @Override
        public String eventType() {
            return "token";
        }
    }

    /**
     * Tool invocation request emitted when the LLM initiates a tool call.
     */
    record ToolCall(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String callId,
            String name,
            Map<String, Object> arguments
    ) implements ChatStreamEvent {
        public ToolCall {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }

        @Override
        public String eventType() {
            return "tool_call";
        }
    }

    /**
     * Tool execution outcome emitted when tool execution completes.
     * The wire preview is bounded to 2 KiB (2048 chars); full payload is stored in JDBC.
     */
    record ToolResult(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String callId,
            String name,
            String status,
            String preview,
            boolean truncated,
            long elapsedMs
    ) implements ChatStreamEvent {
        public ToolResult(
                String sessionId,
                String turnId,
                int seq,
                long tsEpochMs,
                String callId,
                String name,
                String status,
                String preview,
                long elapsedMs) {
            this(sessionId, turnId, seq, tsEpochMs, callId, name, status, preview,
                    preview != null && preview.length() >= 2048, elapsedMs);
        }

        @Override
        public String eventType() {
            return "tool_result";
        }
    }

    /**
     * Terminal success event marking normal completion of an agentic turn.
     */
    record Done(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String summary,
            long latencyMs,
            int primedMemories,
            TokenUsageDto usage
    ) implements ChatStreamEvent {
        @Override
        public String eventType() {
            return "done";
        }
    }

    /**
     * Error event emitted on unrecoverable model or runtime failures.
     */
    record Error(
            String sessionId,
            String turnId,
            int seq,
            long tsEpochMs,
            String code,
            String message,
            boolean retryable
    ) implements ChatStreamEvent {
        @Override
        public String eventType() {
            return "error";
        }
    }

    // ── Static Factories ───────────────────────────────────────────

    static Session session(String sessionId, String turnId, int seq, long tsEpochMs, boolean isNew, String model) {
        return new Session(sessionId, turnId, seq, tsEpochMs, isNew, model);
    }

    static Thinking thinking(String sessionId, String turnId, int seq, long tsEpochMs, String text, long elapsedMs) {
        return new Thinking(sessionId, turnId, seq, tsEpochMs, text, elapsedMs);
    }

    static Token token(String sessionId, String turnId, int seq, long tsEpochMs, String text) {
        return new Token(sessionId, turnId, seq, tsEpochMs, text);
    }

    static ToolCall toolCall(String sessionId, String turnId, int seq, long tsEpochMs, String callId, String name, Map<String, Object> arguments) {
        return new ToolCall(sessionId, turnId, seq, tsEpochMs, callId, name, arguments);
    }

    static ToolResult toolResult(String sessionId, String turnId, int seq, long tsEpochMs, String callId, String name, String status, String preview, boolean truncated, long elapsedMs) {
        return new ToolResult(sessionId, turnId, seq, tsEpochMs, callId, name, status, preview, truncated, elapsedMs);
    }

    static Done done(String sessionId, String turnId, int seq, long tsEpochMs, String summary, long latencyMs, int primedMemories, TokenUsageDto usage) {
        return new Done(sessionId, turnId, seq, tsEpochMs, summary, latencyMs, primedMemories, usage);
    }

    static Error error(String sessionId, String turnId, int seq, long tsEpochMs, String code, String message, boolean retryable) {
        return new Error(sessionId, turnId, seq, tsEpochMs, code, message, retryable);
    }
}
