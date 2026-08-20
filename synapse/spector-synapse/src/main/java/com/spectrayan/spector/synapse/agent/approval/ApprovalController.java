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
 * REST API for managing Human-in-the-Loop (HITL) agent approvals.
 */
@RestController
@RequestMapping("/api/v1/agent/approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ApprovalStore approvalStore;
    private final ApprovalGate approvalGate;

    public ApprovalController(ApprovalStore approvalStore, ApprovalGate approvalGate) {
        this.approvalStore = Objects.requireNonNull(approvalStore, "approvalStore must not be null");
        this.approvalGate = Objects.requireNonNull(approvalGate, "approvalGate must not be null");
    }

    /**
     * Lists approval requests.
     *
     * @param pendingOnly if true, returns only pending approvals (default: false)
     * @param limit       maximum number of records to return (default: 50)
     * @return list of approval requests
     */
    @GetMapping
    public List<ApprovalRequest> listApprovals(
            @RequestParam(defaultValue = "false") boolean pendingOnly,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (pendingOnly) {
            return approvalStore.listPending();
        }
        return approvalStore.listAll(limit);
    }

    /**
     * Retrieves details for a specific approval request.
     *
     * @param id the approval request ID
     * @return the approval request or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApprovalRequest> getApproval(@PathVariable String id) {
        return approvalStore.get(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Approves a pending tool execution request with original arguments.
     *
     * @param id the approval request ID
     * @return 200 OK if resolved, 400 Bad Request if already resolved or not found
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id) {
        log.info("[ApprovalController] Operator approving request id={}", id);
        boolean success = approvalGate.resolveApproval(id, ApprovalDecision.APPROVE, null, null);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "APPROVED", "id", id));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Approval not found or already resolved", "id", id));
    }

    /**
     * Rejects a pending tool execution request with an optional reason.
     *
     * @param id   the approval request ID
     * @param body optional payload containing rejection reason
     * @return 200 OK if rejected, 400 Bad Request if already resolved or not found
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String id,
            @RequestBody(required = false) ApprovalResponseDto body
    ) {
        String reason = body != null ? body.reason() : null;
        log.info("[ApprovalController] Operator rejecting request id={}, reason={}", id, reason);
        boolean success = approvalGate.resolveApproval(id, ApprovalDecision.REJECT, null, reason);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "REJECTED", "id", id, "reason", reason != null ? reason : ""));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Approval not found or already resolved", "id", id));
    }

    /**
     * Approves a pending tool execution request with modified arguments.
     *
     * @param id   the approval request ID
     * @param body payload containing modifiedArguments and optional reason
     * @return 200 OK if modified, 400 Bad Request if invalid or not found
     */
    @PostMapping("/{id}/modify")
    public ResponseEntity<Map<String, Object>> modify(
            @PathVariable String id,
            @RequestBody ApprovalResponseDto body
    ) {
        if (body == null || body.modifiedArguments() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "modifiedArguments is required for modify decision", "id", id));
        }
        log.info("[ApprovalController] Operator modifying request id={}, newArgs={}", id, body.modifiedArguments());
        boolean success = approvalGate.resolveApproval(
                id, ApprovalDecision.MODIFY, body.modifiedArguments(), body.reason()
        );
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "MODIFIED",
                    "id", id,
                    "modifiedArguments", body.modifiedArguments()
            ));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Approval not found or already resolved", "id", id));
    }
}
