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
package com.spectrayan.spector.synapse.agent.approval.service;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.agent.approval.model.AgentActionApproval;
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalExecutionResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service contract for Human-in-the-Loop agent action approvals.
 */
public interface AgentApprovalService {

    /**
     * Functional callback for executing tool logic with approved arguments.
     */
    @FunctionalInterface
    interface ToolExecutor {
        String execute(Map<String, Object> arguments) throws Exception;
    }

    /**
     * Determines whether the tool requires human approval.
     */
    boolean isApprovalRequired(McpToolHandler tool);

    /**
     * Evaluates whether approval is required and either executes immediately or blocks
     * awaiting human decision.
     */
    ApprovalExecutionResult evaluateAndExecute(
            McpToolHandler tool,
            Map<String, Object> arguments,
            String sessionId,
            String agentId,
            ToolExecutor executor
    );

    /**
     * Approves a pending action with original parameters.
     */
    AgentActionApproval approve(String id);

    /**
     * Rejects a pending action with a reason.
     */
    AgentActionApproval reject(String id, String reason);

    /**
     * Approves a pending action with modified parameters.
     */
    AgentActionApproval modify(String id, Map<String, Object> modifiedArguments, String reason);

    /**
     * Cancels a pending action.
     */
    AgentActionApproval cancel(String id, String reason);

    /**
     * Retrieves an approval entity by ID.
     */
    Optional<AgentActionApproval> getApproval(String id);

    /**
     * Lists approval entities.
     */
    List<AgentActionApproval> listApprovals(boolean pendingOnly, int limit);
}
