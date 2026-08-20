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
package com.spectrayan.spector.synapse.agent.approval;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.platform.events.SseEventConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Human-in-the-Loop (HITL) approval gate service for agent actions.
 *
 * <p>Intercepts write tools (e.g. {@code isWriteTool() == true}), emits real-time
 * SSE notifications via {@link EventPublisher}, and blocks execution until a human
 * operator approves, rejects, or modifies the tool invocation parameters.</p>
 */
@Service
public class ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(ApprovalGate.class);

    private final ApprovalStore approvalStore;
    private final EventPublisher eventPublisher;

    @Value("${spector.agent.approval.enabled:true}")
    private boolean enabled = true;

    @Value("${spector.agent.approval.timeout-seconds:300}")
    private long timeoutSeconds = 300;

    @Value("${spector.agent.approval.auto-reject-on-timeout:true}")
    private boolean autoRejectOnTimeout = true;

    public ApprovalGate(ApprovalStore approvalStore, EventPublisher eventPublisher) {
        this.approvalStore = Objects.requireNonNull(approvalStore, "approvalStore must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    /**
     * Functional interface for executing a tool with effective arguments.
     */
    @FunctionalInterface
    public interface ToolExecutor {
        String execute(Map<String, Object> arguments) throws Exception;
    }

    /**
     * Result of an approval gate evaluation and execution attempt.
     */
    public sealed interface ApprovalExecutionResult {
        /** Tool was executed successfully (either read-only or approved). */
        record Success(String output, ApprovalRequest request) implements ApprovalExecutionResult {}

        /** Tool execution was denied by human operator or timed out. */
        record Denied(String reason, ApprovalRequest request) implements ApprovalExecutionResult {}
    }

    /**
     * Checks if a tool requires human approval.
     */
    public boolean requiresApproval(McpToolHandler tool) {
        if (!enabled || tool == null) {
            return false;
        }
        return tool.isWriteTool();
    }

    /**
     * Evaluates whether approval is required, and either executes immediately or blocks
     * awaiting human decision over SSE / REST.
     *
     * @param tool        the tool being invoked
     * @param arguments   the proposed arguments
     * @param sessionId   optional conversation / chat session ID
     * @param agentId     optional agent identity
     * @param executor    the execution callback to run when approved
     * @return structured execution result (Success or Denied)
     */
    public ApprovalExecutionResult evaluateAndExecute(
            McpToolHandler tool,
            Map<String, Object> arguments,
            String sessionId,
            String agentId,
            ToolExecutor executor
    ) {
        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        Map<String, Object> safeArgs = arguments != null ? arguments : Map.of();

        if (!requiresApproval(tool)) {
            try {
                String output = executor.execute(safeArgs);
                return new ApprovalExecutionResult.Success(output, null);
            } catch (Exception e) {
                log.error("[ApprovalGate] Read-only tool '{}' execution failed", tool.name(), e);
                return new ApprovalExecutionResult.Denied("Tool execution failed: " + e.getMessage(), null);
            }
        }

        // Generate approval request and completion future
        String approvalId = UUID.randomUUID().toString();
        String category = tool.category() != null ? tool.category().name() : "GENERAL";
        ApprovalRequest pending = ApprovalRequest.pending(
                approvalId, tool.name(), safeArgs, category, sessionId, agentId
        );

        CompletableFuture<ApprovalRequest> future = new CompletableFuture<>();
        approvalStore.register(pending, future);

        // Emit SSE event to notify connected UI / operators
        eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_REQUIRED, pending);
        log.info("[ApprovalGate] Emitted SSE approval request id={} for tool={}", approvalId, tool.name());

        try {
            // Await human decision
            ApprovalRequest resolved = future.get(timeoutSeconds, TimeUnit.SECONDS);

            return switch (resolved.status()) {
                case APPROVED, MODIFIED -> {
                    Map<String, Object> effectiveArgs = resolved.effectiveArguments();
                    log.info("[ApprovalGate] Approval granted for id={} (status={}), executing tool...",
                            approvalId, resolved.status());
                    try {
                        String output = executor.execute(effectiveArgs);
                        yield new ApprovalExecutionResult.Success(output, resolved);
                    } catch (Exception e) {
                        log.error("[ApprovalGate] Approved tool '{}' execution failed", tool.name(), e);
                        yield new ApprovalExecutionResult.Denied(
                                "Approved tool execution failed: " + e.getMessage(), resolved);
                    }
                }
                case REJECTED -> {
                    String reason = resolved.reason() != null && !resolved.reason().isBlank()
                            ? resolved.reason()
                            : "Action rejected by human operator";
                    log.warn("[ApprovalGate] Approval rejected for id={}: {}", approvalId, reason);
                    yield new ApprovalExecutionResult.Denied(
                            "Tool execution rejected by user: " + reason, resolved);
                }
                default -> {
                    log.warn("[ApprovalGate] Unexpected resolution status {} for id={}", resolved.status(), approvalId);
                    yield new ApprovalExecutionResult.Denied(
                            "Tool execution not approved (status: " + resolved.status() + ")", resolved);
                }
            };

        } catch (TimeoutException e) {
            log.warn("[ApprovalGate] Approval request id={} timed out after {}s", approvalId, timeoutSeconds);
            ApprovalRequest timedOut = pending.asTimeout("Timed out waiting for operator approval (" + timeoutSeconds + "s)");
            approvalStore.update(timedOut);

            eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_TIMEOUT, timedOut);

            return new ApprovalExecutionResult.Denied(
                    "Tool execution timed out awaiting human approval (" + timeoutSeconds + "s)", timedOut);

        } catch (Exception e) {
            log.error("[ApprovalGate] Interrupted or failed waiting for approval id={}", approvalId, e);
            ApprovalRequest rejected = pending.asRejected("Approval gate error: " + e.getMessage());
            approvalStore.update(rejected);
            return new ApprovalExecutionResult.Denied("Approval wait interrupted: " + e.getMessage(), rejected);
        }
    }

    /**
     * Resolves a pending approval request with a human decision.
     *
     * @param id                the approval request ID
     * @param decision          the human decision (APPROVE, REJECT, MODIFY)
     * @param modifiedArguments modified parameters when decision is MODIFY (optional)
     * @param reason            reason or comment from the human operator (optional)
     * @return true if the approval was found and successfully resolved, false otherwise
     */
    public boolean resolveApproval(String id, ApprovalDecision decision,
                                   Map<String, Object> modifiedArguments, String reason) {
        Optional<ApprovalRequest> reqOpt = approvalStore.get(id);
        Optional<CompletableFuture<ApprovalRequest>> futureOpt = approvalStore.getFuture(id);

        if (reqOpt.isEmpty() || futureOpt.isEmpty()) {
            log.warn("[ApprovalGate] Cannot resolve non-existent approval id={}", id);
            return false;
        }

        ApprovalRequest current = reqOpt.get();
        if (current.status() != ApprovalStatus.PENDING) {
            log.warn("[ApprovalGate] Approval id={} is already resolved with status {}", id, current.status());
            return false;
        }

        ApprovalRequest resolved = switch (decision != null ? decision : ApprovalDecision.APPROVE) {
            case APPROVE -> current.asApproved();
            case REJECT -> current.asRejected(reason);
            case MODIFY -> current.asModified(modifiedArguments, reason);
        };

        approvalStore.update(resolved);
        futureOpt.get().complete(resolved);

        eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_RESOLVED, resolved);
        log.info("[ApprovalGate] Resolved approval id={} with decision={} status={}", id, decision, resolved.status());
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
