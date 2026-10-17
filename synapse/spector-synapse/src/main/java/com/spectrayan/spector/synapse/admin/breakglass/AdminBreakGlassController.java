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
package com.spectrayan.spector.synapse.admin.breakglass;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.ApproveBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.BreakGlassResponse;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.CreateBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.RejectBreakGlassRequest;
import com.spectrayan.spector.synapse.admin.breakglass.dto.BreakGlassDto.RevokeBreakGlassRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for emergency break-glass namespace access at {@code /api/v1/admin/break-glass}
 * (ADR-0087 §5.4, Issue #1060).
 *
 * <p>Provides dual-control approval, BYOK tenant hard-deny enforcement, time-boxed read-only grant
 * issuance, manual revocation (by admin or namespace owner), and session inspection.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/admin/break-glass", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('admin', 'super-admin')")
@Tag(name = "Admin Break-Glass", description = "Emergency time-boxed, dual-admin approved, read-only namespace access for non-BYOK tenants")
public class AdminBreakGlassController {

    private final BreakGlassService breakGlassService;

    public AdminBreakGlassController(BreakGlassService breakGlassService) {
        this.breakGlassService = Objects.requireNonNull(breakGlassService, "breakGlassService must not be null");
    }

    /**
     * Submits an emergency break-glass request for a namespace (`POST /api/v1/admin/break-glass`).
     * Hard-denied with HTTP 403 (`SPE-820-004` / `SPE-SEC-004`) if the target namespace's tenant is BYOK-enabled.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('admin', 'super-admin')")
    @Operation(operationId = "requestBreakGlass", summary = "Submit a time-boxed emergency break-glass access request")
    public ResponseEntity<BreakGlassResponse> createRequest(@RequestBody CreateBreakGlassRequest request) {
        BreakGlassResponse created = breakGlassService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Approves a pending break-glass request (`POST /api/v1/admin/break-glass/{id}/approve`).
     * Requires a second distinct administrator (`approver != requester`).
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('admin', 'super-admin')")
    @Operation(operationId = "approveBreakGlass", summary = "Approve a pending break-glass request (requires distinct second admin)")
    public ResponseEntity<BreakGlassResponse> approveRequest(
            @PathVariable String id,
            @RequestBody(required = false) ApproveBreakGlassRequest request) {
        return ResponseEntity.ok(breakGlassService.approveRequest(id, request));
    }

    /**
     * Rejects a pending break-glass request (`POST /api/v1/admin/break-glass/{id}/reject`).
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('admin', 'super-admin')")
    @Operation(operationId = "rejectBreakGlass", summary = "Reject a pending break-glass request")
    public ResponseEntity<BreakGlassResponse> rejectRequest(
            @PathVariable String id,
            @RequestBody(required = false) RejectBreakGlassRequest request) {
        return ResponseEntity.ok(breakGlassService.rejectRequest(id, request));
    }

    /**
     * Manually revokes a break-glass session (`POST /api/v1/admin/break-glass/{id}/revoke`).
     * Callable by any tenant admin, super-admin, the requesting admin, or the namespace owner.
     */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "revokeBreakGlass", summary = "Manually revoke an active or pending break-glass session")
    public ResponseEntity<BreakGlassResponse> revokeRequest(
            @PathVariable String id,
            @RequestBody(required = false) RevokeBreakGlassRequest request) {
        return ResponseEntity.ok(breakGlassService.revokeRequest(id, request));
    }

    /**
     * Lists break-glass sessions (`GET /api/v1/admin/break-glass`).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('admin', 'super-admin')")
    @Operation(operationId = "listBreakGlassRequests", summary = "List break-glass requests and sessions")
    public ResponseEntity<List<BreakGlassResponse>> listRequests(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "namespaceId", required = false) String namespaceId,
            @RequestParam(name = "status", required = false) String status) {
        BreakGlassStatus parsedStatus = (status != null && !status.isBlank())
                ? BreakGlassStatus.fromString(status)
                : null;
        return ResponseEntity.ok(breakGlassService.listRequests(tenantId, namespaceId, parsedStatus));
    }

    /**
     * Retrieves a single break-glass request by ID (`GET /api/v1/admin/break-glass/{id}`).
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getBreakGlassRequest", summary = "Get break-glass session details by ID")
    public ResponseEntity<BreakGlassResponse> getRequest(@PathVariable String id) {
        return ResponseEntity.ok(breakGlassService.getRequest(id));
    }
}
