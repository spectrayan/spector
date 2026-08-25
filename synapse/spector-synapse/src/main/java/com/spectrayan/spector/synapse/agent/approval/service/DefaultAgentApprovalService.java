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
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalStatus;
import com.spectrayan.spector.synapse.agent.approval.repository.AgentApprovalRepository;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.platform.events.SseEventConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of {@link AgentApprovalService}.
 */
@Service
public class DefaultAgentApprovalService implements AgentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentApprovalService.class);

    private final AgentApprovalRepository repository;
    private final EventPublisher eventPublisher;

    @Value("${spector.agent.approval.enabled:true}")
    private boolean enabled = true;

    @Value("${spector.agent.approval.timeout-seconds:300}")
    private long timeoutSeconds = 300;

    public DefaultAgentApprovalService(AgentApprovalRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    public boolean isApprovalRequired(McpToolHandler tool) {
        if (!enabled || tool == null) {
            return false;
        }
        return tool.isWriteTool();
    }

    @Override
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

        if (!isApprovalRequired(tool)) {
            try {
                String output = executor.execute(safeArgs);
                return new ApprovalExecutionResult.Success(output, null);
            } catch (Exception e) {
                log.error("[DefaultAgentApprovalService] Read-only tool '{}' execution failed", tool.name(), e);
                return new ApprovalExecutionResult.Denied("Tool execution failed: " + e.getMessage(), null);
            }
        }

        String approvalId = UUID.randomUUID().toString();
        String category = tool.category() != null ? tool.category().name() : "GENERAL";
        AgentActionApproval pending = AgentActionApproval.pending(
                approvalId, tool.name(), safeArgs, category, sessionId, agentId
        );

        CompletableFuture<AgentActionApproval> future = new CompletableFuture<>();
        repository.save(pending, future);

        // Notify subscribers over SSE
        eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_REQUIRED, pending);
        log.info("[DefaultAgentApprovalService] Registered pending approval id={} tool={}", approvalId, tool.name());

        try {
            AgentActionApproval resolved = future.get(timeoutSeconds, TimeUnit.SECONDS);

            return switch (resolved.status()) {
                case APPROVED, MODIFIED -> {
                    Map<String, Object> effectiveArgs = resolved.effectiveArguments();
                    log.info("[DefaultAgentApprovalService] Approval granted for id={} (status={}), executing tool...",
                            approvalId, resolved.status());
                    try {
                        String output = executor.execute(effectiveArgs);
                        yield new ApprovalExecutionResult.Success(output, resolved);
                    } catch (Exception e) {
                        log.error("[DefaultAgentApprovalService] Approved tool '{}' execution failed", tool.name(), e);
                        yield new ApprovalExecutionResult.Denied(
                                "Approved tool execution failed: " + e.getMessage(), resolved);
                    }
                }
                case REJECTED -> {
                    String reason = resolved.reason() != null && !resolved.reason().isBlank()
                            ? resolved.reason()
                            : "Action rejected by human operator";
                    log.warn("[DefaultAgentApprovalService] Approval rejected for id={}: {}", approvalId, reason);
                    yield new ApprovalExecutionResult.Denied("Tool execution rejected by user: " + reason, resolved);
                }
                case CANCELLED -> {
                    String reason = resolved.reason() != null && !resolved.reason().isBlank()
                            ? resolved.reason()
                            : "Action cancelled";
                    log.warn("[DefaultAgentApprovalService] Approval cancelled for id={}: {}", approvalId, reason);
                    yield new ApprovalExecutionResult.Denied("Tool execution cancelled: " + reason, resolved);
                }
                default -> {
                    log.warn("[DefaultAgentApprovalService] Unexpected resolution status {} for id={}", resolved.status(), approvalId);
                    yield new ApprovalExecutionResult.Denied(
                            "Tool execution not approved (status: " + resolved.status() + ")", resolved);
                }
            };

        } catch (TimeoutException e) {
            log.warn("[DefaultAgentApprovalService] Approval request id={} timed out after {}s", approvalId, timeoutSeconds);
            AgentActionApproval timedOut = pending.asTimedOut(
                    "Timed out waiting for operator approval (" + timeoutSeconds + "s)"
            );
            repository.update(timedOut);
            eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_TIMEOUT, timedOut);

            return new ApprovalExecutionResult.Denied(
                    "Tool execution timed out awaiting human approval (" + timeoutSeconds + "s)", timedOut);

        } catch (Exception e) {
            log.error("[DefaultAgentApprovalService] Approval wait failed for id={}", approvalId, e);
            AgentActionApproval rejected = pending.asRejected("Approval gate error: " + e.getMessage());
            repository.update(rejected);
            return new ApprovalExecutionResult.Denied("Approval wait error: " + e.getMessage(), rejected);
        }
    }

    @Override
    public AgentActionApproval approve(String id) {
        return resolveDecision(id, AgentActionApproval::asApproved);
    }

    @Override
    public AgentActionApproval reject(String id, String reason) {
        return resolveDecision(id, existing -> existing.asRejected(reason));
    }

    @Override
    public AgentActionApproval modify(String id, Map<String, Object> modifiedArguments, String reason) {
        if (modifiedArguments == null || modifiedArguments.isEmpty()) {
            throw new IllegalArgumentException("modifiedArguments is required when modifying approval request");
        }
        return resolveDecision(id, existing -> existing.asModified(modifiedArguments, reason));
    }

    @Override
    public AgentActionApproval cancel(String id, String reason) {
        return resolveDecision(id, existing -> existing.asCancelled(reason));
    }

    @Override
    public Optional<AgentActionApproval> getApproval(String id) {
        return repository.findById(id);
    }

    @Override
    public List<AgentActionApproval> listApprovals(boolean pendingOnly, int limit) {
        if (pendingOnly) {
            return repository.findPending();
        }
        return repository.findAll(limit);
    }

    private AgentActionApproval resolveDecision(String id, java.util.function.Function<AgentActionApproval, AgentActionApproval> resolver) {
        Optional<AgentActionApproval> existingOpt = repository.findById(id);
        Optional<CompletableFuture<AgentActionApproval>> futureOpt = repository.findFutureById(id);

        if (existingOpt.isEmpty() || futureOpt.isEmpty()) {
            throw new IllegalArgumentException("Approval request not found for id: " + id);
        }

        AgentActionApproval current = existingOpt.get();
        if (current.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval request id=" + id + " is already resolved with status " + current.status());
        }

        AgentActionApproval resolved = resolver.apply(current);
        repository.update(resolved);
        eventPublisher.agentEvent(SseEventConstants.EVENT_AGENT_APPROVAL_RESOLVED, resolved);
        log.info("[DefaultAgentApprovalService] Resolved approval id={} status={}", id, resolved.status());
        futureOpt.get().complete(resolved);
        return resolved;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
