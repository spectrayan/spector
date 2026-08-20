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
package com.spectrayan.spector.synapse.platform.events;

/**
 * Central registry of Server-Sent Events (SSE) topic names and event types.
 *
 * <p>Avoids string literals and ensures platform-wide consistency across
 * backend services, controllers, and frontend dashboard consumers.</p>
 */
public final class SseEventConstants {

    private SseEventConstants() {}

    // ═══════════════════════════════════════════════════════════════
    // SSE Topics
    // ═══════════════════════════════════════════════════════════════

    /** Topic for agent coordination, reasoning, and human-in-the-loop approvals. */
    public static final String TOPIC_AGENT = "agent";

    /** Topic for interactive chat streaming, thoughts, and message turns. */
    public static final String TOPIC_CHAT = "chat";

    /** Topic for cognitive telemetry, graph pulses, SIMD stats, and diagnostics. */
    public static final String TOPIC_CORTEX = "cortex";

    /** Topic for memory lifecycle events (recalled, consolidated, decayed, stored). */
    public static final String TOPIC_MEMORY = "memory";

    /** Topic for system status and health changes. */
    public static final String TOPIC_SYSTEM = "system";

    /** Topic for connector status and telemetry. */
    public static final String TOPIC_CONNECTORS = "connectors";

    // ═══════════════════════════════════════════════════════════════
    // Event Types — Agent & Approval
    // ═══════════════════════════════════════════════════════════════

    /** Emitted when an agent write tool requires human approval. */
    public static final String EVENT_AGENT_APPROVAL_REQUIRED = "agent.approval.required";

    /** Emitted when a pending human-in-the-loop approval is resolved. */
    public static final String EVENT_AGENT_APPROVAL_RESOLVED = "agent.approval.resolved";

    /** Emitted when a pending approval times out without human response. */
    public static final String EVENT_AGENT_APPROVAL_TIMEOUT = "agent.approval.timeout";

    // ═══════════════════════════════════════════════════════════════
    // Event Types — Chat
    // ═══════════════════════════════════════════════════════════════

    /** Emitted for streamed chat token content. */
    public static final String EVENT_CHAT_MESSAGE = "chat.message";

    /** Emitted when the agent enters a thinking phase. */
    public static final String EVENT_CHAT_THINKING = "chat.thinking";

    /** Emitted when a tool invocation is initiated by the LLM. */
    public static final String EVENT_CHAT_TOOL_CALL = "chat.tool_call";

    /** Emitted when a chat turn completes. */
    public static final String EVENT_CHAT_DONE = "chat.done";

    // ═══════════════════════════════════════════════════════════════
    // Event Types — Memory & Connectors
    // ═══════════════════════════════════════════════════════════════

    /** Emitted when a memory record is mutated. */
    public static final String EVENT_MEMORY_MUTATION = "memory.mutation";

    /** Emitted when connector state changes. */
    public static final String EVENT_CONNECTOR_STATUS = "status";
}
