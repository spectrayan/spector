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
package com.spectrayan.spector.synapse.agent.approval.api;

import com.spectrayan.spector.synapse.agent.approval.dto.ApprovalDecisionRequest;
import com.spectrayan.spector.synapse.agent.approval.model.AgentActionApproval;
import com.spectrayan.spector.synapse.agent.approval.service.AgentApprovalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST controller for managing Human-in-the-Loop (HITL) agent action approvals.
 *
 * <p>Strictly handles HTTP endpoint routing and status translation, delegating
 * all domain operations to {@link AgentApprovalService}.</p>
 */
@RestController
@RequestMapping("/api/v1/agent/approvals")
public class AgentApprovalController {

    private static final Logger log = LoggerFactory.getLogger(AgentApprovalController.class);

    private final AgentApprovalService approvalService;

    public AgentApprovalController(AgentApprovalService approvalService) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
    }

    /**
     * Lists agent action approvals.
     */
    @GetMapping
    public List<AgentActionApproval> listApprovals(
            @RequestParam(defaultValue = "false") boolean pendingOnly,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return approvalService.listApprovals(pendingOnly, limit);
    }

    /**
     * Retrieves a single action approval by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AgentActionApproval> getApproval(@PathVariable String id) {
        return approvalService.getApproval(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Approves a pending tool action.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id) {
        try {
            AgentActionApproval resolved = approvalService.approve(id);
            return ResponseEntity.ok(resolved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "id", id));
        }
    }

    /**
     * Rejects a pending tool action.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String id,
            @RequestBody(required = false) ApprovalDecisionRequest body
    ) {
        try {
            String reason = body != null ? body.reason() : null;
            AgentActionApproval resolved = approvalService.reject(id, reason);
            return ResponseEntity.ok(resolved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "id", id));
        }
    }

    /**
     * Approves a pending tool action with modified arguments.
     */
    @PostMapping("/{id}/modify")
    public ResponseEntity<?> modify(
            @PathVariable String id,
            @RequestBody ApprovalDecisionRequest body
    ) {
        if (body == null || body.modifiedArguments() == null || body.modifiedArguments().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "modifiedArguments is required for modify decision", "id", id));
        }
        try {
            AgentActionApproval resolved = approvalService.modify(id, body.modifiedArguments(), body.reason());
            return ResponseEntity.ok(resolved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "id", id));
        }
    }

    /**
     * Cancels a pending tool action.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable String id,
            @RequestBody(required = false) ApprovalDecisionRequest body
    ) {
        try {
            String reason = body != null ? body.reason() : null;
            AgentActionApproval resolved = approvalService.cancel(id, reason);
            return ResponseEntity.ok(resolved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "id", id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "id", id));
        }
    }
}
