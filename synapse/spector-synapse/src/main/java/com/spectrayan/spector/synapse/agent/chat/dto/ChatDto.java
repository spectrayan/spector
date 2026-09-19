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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for the Agent Chat REST API.
 *
 * <p>Every nested record maps to the TypeScript interfaces used by
 * the Cortex UI {@code AgentChatComponent}. Field names match the
 * JSON keys the frontend expects.</p>
 */
public final class ChatDto {

    private ChatDto() {} // non-instantiable

    // ═══════════════════════════════════════════════════════════════
    // Agent Chat
    // ═══════════════════════════════════════════════════════════════

    /**
     * Agent chat request body.
     */
    public record AgentChatRequest(
            String message,
            String sessionId,
            String conversationId,
            String model,
            Integer contextDepth,
            Boolean enableGraph,
            Boolean enableTextSearch,
            Boolean enableTrace,
            List<Map<String, Object>> messages,
            @JsonAlias("approved_tool_calls") List<Map<String, Object>> approvedToolCalls,
            @Schema(hidden = true) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) List<Map<String, Object>> approved_tool_calls
    ) {
        public AgentChatRequest(String message, String sessionId) {
            this(message, sessionId, null, null, null, null, null, null, null, null, null);
        }

        public AgentChatRequest(String message, String sessionId, String model) {
            this(message, sessionId, null, model, null, null, null, null, null, null, null);
        }

        /** Resolves sessionId from either field (backward compat). */
        public String resolvedSessionId() {
            return sessionId != null ? sessionId : conversationId;
        }

        /** Resolves approved tool calls from either field (backward compat). */
        public List<Map<String, Object>> resolvedApprovedToolCalls() {
            if (approvedToolCalls != null && !approvedToolCalls.isEmpty()) return approvedToolCalls;
            return approved_tool_calls;
        }
    }

    /**
     * Token consumption metadata for a chat turn.
     */
    public record TokenUsageDto(
            long inputTokens,
            long outputTokens,
            long totalTokens
    ) {
        public static TokenUsageDto of(long inputTokens, long outputTokens) {
            return new TokenUsageDto(inputTokens, outputTokens, inputTokens + outputTokens);
        }
    }

    /**
     * Agent chat response — consumed by Cortex UI AgentChatComponent.
     */
    public record AgentChatResponse(
            String response,
            String sessionId,
            boolean isNewSession,
            String model,
            String status,
            long latency,
            long durationMs,
            int primedMemories,
            List<TraceEvent> trace,
            List<Map<String, Object>> pendingToolCalls,
            List<String> sources,
            TokenUsageDto tokenUsage
    ) {
        public AgentChatResponse(
                String response,
                String sessionId,
                boolean isNewSession,
                String model,
                String status,
                long latency,
                long durationMs,
                int primedMemories,
                List<TraceEvent> trace,
                List<Map<String, Object>> pendingToolCalls,
                List<String> sources
        ) {
            this(response, sessionId, isNewSession, model, status, latency, durationMs, primedMemories, trace, pendingToolCalls, sources, null);
        }
    }

    /**
     * A trace event emitted during agentic execution.
     */
    public record TraceEvent(String type, String data) {}

    // ═══════════════════════════════════════════════════════════════
    // Models
    // ═══════════════════════════════════════════════════════════════

    /**
     * An available LLM model.
     */
    public record OllamaModel(
            String id,
            String name,
            long size,
            String modified_at,
            boolean active
    ) {}

    /**
     * Models list response.
     */
    public record ModelsResponse(List<OllamaModel> models) {}

    // ═══════════════════════════════════════════════════════════════
    // Config
    // ═══════════════════════════════════════════════════════════════

    /**
     * Chat configuration response.
     */
    public record ChatConfig(
            String defaultModel,
            int maxContextDepth,
            int defaultContextDepth,
            boolean agentMode,
            String version
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // Sessions
    // ═══════════════════════════════════════════════════════════════

    /**
     * A session summary for the session list.
     */
    public record SessionSummary(
            String sessionId,
            String preview,
            String lastActivity,
            int messageCount
    ) {}

    /**
     * Sessions list response.
     */
    public record SessionsResponse(
            List<SessionSummary> sessions,
            boolean hasMore
    ) {}

    /**
     * Session messages response.
     */
    public record SessionMessagesResponse(
            List<Map<String, Object>> messages,
            String sessionId
    ) {}

    /**
     * Request body for renaming an operational chat session.
     */
    public record RenameSessionRequest(
            @NotBlank(message = "Session title must not be blank")
            @Size(max = 255, message = "Session title must not exceed 255 characters")
            String title
    ) {
        public RenameSessionRequest {
            if (title != null) {
                title = title.trim();
            }
        }
    }

    /**
     * Operational record for a chat session.
     */
    public record ChatSessionRecord(
            String id,
            String title,
            String status,
            boolean archived,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Operational session summary with typed Instant.
     */
    public record ChatSessionSummary(
            String sessionId,
            String preview,
            Instant lastActivity,
            int messageCount
    ) {}

    /**
     * Historical replay response containing structured turns for a session.
     */
    public record SessionHistoryResponse(
            String sessionId,
            String title,
            List<ChatTurnView> turns
    ) {
        public SessionHistoryResponse(String sessionId, List<ChatTurnView> turns) {
            this(sessionId, null, turns != null ? List.copyOf(turns) : List.of());
        }

        public SessionHistoryResponse {
            turns = turns != null ? List.copyOf(turns) : List.of();
        }
    }

    /**
     * Structured view of a single conversation turn matching Cortex UI reducers.
     */
    public record ChatTurnView(
            String turnId,
            int seq,
            String status,
            UserMessageView user,
            ThinkingView thinking,
            List<ToolCardView> tools,
            AssistantMessageView assistant,
            TokenUsageDto usage,
            int primedMemories
    ) {
        public ChatTurnView {
            tools = tools != null ? List.copyOf(tools) : List.of();
        }
    }

    /**
     * User prompt view within a turn.
     */
    public record UserMessageView(String text) {}

    /**
     * Chain-of-Thought reasoning trace and live elapsed duration within a turn.
     */
    public record ThinkingView(
            String text,
            long elapsedMs
    ) {}

    /**
     * Real-time tool execution card matching Cortex tool-card.component.ts.
     */
    public record ToolCardView(
            String callId,
            String name,
            Map<String, Object> arguments,
            String status,
            String preview,
            boolean truncated,
            long elapsedMs
    ) {
        public ToolCardView(String callId, String name, Map<String, Object> arguments, String status, String preview, long elapsedMs) {
            this(callId, name, arguments, status, preview, false, elapsedMs);
        }

        public ToolCardView {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }
    }

    /**
     * Final assistant visible response text within a turn.
     */
    public record AssistantMessageView(String text) {}

    /**
     * Recalled cognitive memory item for context priming.
     */
    public record PrimedMemory(
            String text,
            String memoryType,
            String ageDescription,
            float score,
            float salienceScore,
            List<String> tags
    ) {
        public PrimedMemory {
            tags = tags != null ? List.copyOf(tags) : List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Tools
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tools list response.
     */
    public record ToolsResponse(List<Map<String, Object>> tools) {}
}
