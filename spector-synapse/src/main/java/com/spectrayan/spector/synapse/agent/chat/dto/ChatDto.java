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
            List<Map<String, Object>> approvedToolCalls,
            List<Map<String, Object>> approved_tool_calls
    ) {
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
            List<String> sources
    ) {}

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

    // ═══════════════════════════════════════════════════════════════
    // Tools
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tools list response.
     */
    public record ToolsResponse(List<Map<String, Object>> tools) {}
}
