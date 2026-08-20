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
package com.spectrayan.spector.synapse.agent.approval.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Domain entity representing an agent action requiring human supervision and approval.
 *
 * @param id                unique identifier for this approval entity
 * @param toolName          name of the tool requesting execution
 * @param arguments         proposed tool arguments
 * @param category          tool category (e.g. FILESYSTEM, MEMORY, SYSTEM)
 * @param sessionId         originating session or conversation identifier (optional)
 * @param agentId           originating agent soul or persona identifier (optional)
 * @param status            current approval lifecycle status
 * @param createdAt         timestamp when request was created
 * @param resolvedAt        timestamp when request was resolved (null if pending)
 * @param modifiedArguments modified arguments if approved with modifications (null if unchanged)
 * @param reason            rationale provided by operator or system on rejection/modification/cancellation
 */
public record AgentActionApproval(
        String id,
        String toolName,
        Map<String, Object> arguments,
        String category,
        String sessionId,
        String agentId,
        ApprovalStatus status,
        Instant createdAt,
        Instant resolvedAt,
        Map<String, Object> modifiedArguments,
        String reason
) {
    public AgentActionApproval {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        arguments = arguments != null ? Collections.unmodifiableMap(arguments) : Map.of();
        modifiedArguments = modifiedArguments != null ? Collections.unmodifiableMap(modifiedArguments) : null;
    }

    /**
     * Creates a new pending approval entity.
     */
    public static AgentActionApproval pending(String id, String toolName, Map<String, Object> arguments,
                                              String category, String sessionId, String agentId) {
        return new AgentActionApproval(
                id,
                toolName,
                arguments,
                category,
                sessionId,
                agentId,
                ApprovalStatus.PENDING,
                Instant.now(),
                null,
                null,
                null
        );
    }

    /**
     * Creates a resolved copy with status APPROVED.
     */
    public AgentActionApproval asApproved() {
        return new AgentActionApproval(
                id, toolName, arguments, category, sessionId, agentId,
                ApprovalStatus.APPROVED, createdAt, Instant.now(), null, null
        );
    }

    /**
     * Creates a resolved copy with status REJECTED.
     */
    public AgentActionApproval asRejected(String reason) {
        return new AgentActionApproval(
                id, toolName, arguments, category, sessionId, agentId,
                ApprovalStatus.REJECTED, createdAt, Instant.now(), null, reason
        );
    }

    /**
     * Creates a resolved copy with status MODIFIED.
     */
    public AgentActionApproval asModified(Map<String, Object> modifiedArgs, String reason) {
        return new AgentActionApproval(
                id, toolName, arguments, category, sessionId, agentId,
                ApprovalStatus.MODIFIED, createdAt, Instant.now(), modifiedArgs, reason
        );
    }

    /**
     * Creates a resolved copy with status CANCELLED.
     */
    public AgentActionApproval asCancelled(String reason) {
        return new AgentActionApproval(
                id, toolName, arguments, category, sessionId, agentId,
                ApprovalStatus.CANCELLED, createdAt, Instant.now(), null, reason
        );
    }

    /**
     * Creates a resolved copy with status TIMED_OUT.
     */
    public AgentActionApproval asTimedOut(String timeoutReason) {
        return new AgentActionApproval(
                id, toolName, arguments, category, sessionId, agentId,
                ApprovalStatus.TIMED_OUT, createdAt, Instant.now(), null, timeoutReason
        );
    }

    /**
     * Returns the effective parameters to pass to the tool (modified if present, else original).
     */
    public Map<String, Object> effectiveArguments() {
        return modifiedArguments != null ? modifiedArguments : arguments;
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }
}
