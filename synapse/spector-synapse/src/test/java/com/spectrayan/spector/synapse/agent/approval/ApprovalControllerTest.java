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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ApprovalController REST API Tests")
class ApprovalControllerTest {

    private MockMvc mockMvc;
    private ApprovalStore store;
    private ApprovalGate gate;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new ApprovalStore();
        EventPublisher eventPublisher = mock(EventPublisher.class);
        gate = new ApprovalGate(store, eventPublisher);
        ApprovalController controller = new ApprovalController(store, gate);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/agent/approvals should list all and pending approvals")
    void testListApprovals() throws Exception {
        ApprovalRequest req1 = ApprovalRequest.pending("appr-1", "file_write", Map.of(), "FILESYSTEM", null, null);
        ApprovalRequest req2 = ApprovalRequest.pending("appr-2", "shell_exec", Map.of(), "SYSTEM", null, null);

        store.register(req1, new CompletableFuture<>());
        store.register(req2, new CompletableFuture<>());
        store.update(req1.asApproved());

        mockMvc.perform(get("/api/v1/agent/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/agent/approvals?pendingOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("appr-2"));
    }

    @Test
    @DisplayName("GET /api/v1/agent/approvals/{id} should return details or 404")
    void testGetApprovalDetail() throws Exception {
        ApprovalRequest req = ApprovalRequest.pending("appr-detail", "file_write", Map.of("path", "a.txt"), "FILESYSTEM", null, null);
        store.register(req, new CompletableFuture<>());

        mockMvc.perform(get("/api/v1/agent/approvals/appr-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("appr-detail"))
                .andExpect(jsonPath("$.toolName").value("file_write"));

        mockMvc.perform(get("/api/v1/agent/approvals/non-existent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/agent/approvals/{id}/approve should resolve pending request")
    void testApproveEndpoint() throws Exception {
        ApprovalRequest req = ApprovalRequest.pending("appr-approve", "file_write", Map.of(), "FILESYSTEM", null, null);
        CompletableFuture<ApprovalRequest> future = new CompletableFuture<>();
        store.register(req, future);

        mockMvc.perform(post("/api/v1/agent/approvals/appr-approve/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(future.isDone()).isTrue();
        assertThat(future.get().status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("POST /api/v1/agent/approvals/{id}/reject should reject pending request with reason")
    void testRejectEndpoint() throws Exception {
        ApprovalRequest req = ApprovalRequest.pending("appr-reject", "shell_exec", Map.of(), "SYSTEM", null, null);
        CompletableFuture<ApprovalRequest> future = new CompletableFuture<>();
        store.register(req, future);

        var body = new ApprovalResponseDto(ApprovalDecision.REJECT, null, "Security policy violation");

        mockMvc.perform(post("/api/v1/agent/approvals/appr-reject/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Security policy violation"));

        assertThat(future.isDone()).isTrue();
        assertThat(future.get().status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(future.get().reason()).isEqualTo("Security policy violation");
    }

    @Test
    @DisplayName("POST /api/v1/agent/approvals/{id}/modify should modify pending request parameters")
    void testModifyEndpoint() throws Exception {
        ApprovalRequest req = ApprovalRequest.pending("appr-mod", "file_write", Map.of("path", "orig.txt"), "FILESYSTEM", null, null);
        CompletableFuture<ApprovalRequest> future = new CompletableFuture<>();
        store.register(req, future);

        var body = new ApprovalResponseDto(ApprovalDecision.MODIFY, Map.of("path", "safe.txt"), "Sanitized path");

        mockMvc.perform(post("/api/v1/agent/approvals/appr-mod/modify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MODIFIED"))
                .andExpect(jsonPath("$.modifiedArguments.path").value("safe.txt"));

        assertThat(future.isDone()).isTrue();
        assertThat(future.get().status()).isEqualTo(ApprovalStatus.MODIFIED);
        assertThat(future.get().effectiveArguments()).containsEntry("path", "safe.txt");
    }
}
