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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApprovalStore Tests")
class ApprovalStoreTest {

    private ApprovalStore store;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore();
    }

    @Test
    @DisplayName("Should register pending approval request and retrieve it")
    void testRegisterAndRetrieve() {
        ApprovalRequest req = ApprovalRequest.pending(
                "req-1", "file_write", Map.of("path", "foo.txt"), "FILESYSTEM", "sess-1", "agent-1"
        );
        CompletableFuture<ApprovalRequest> future = new CompletableFuture<>();

        store.register(req, future);

        Optional<ApprovalRequest> retrieved = store.get("req-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().toolName()).isEqualTo("file_write");
        assertThat(retrieved.get().status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(retrieved.get().arguments()).containsEntry("path", "foo.txt");

        Optional<CompletableFuture<ApprovalRequest>> retrievedFuture = store.getFuture("req-1");
        assertThat(retrievedFuture).isPresent().containsSame(future);
    }

    @Test
    @DisplayName("Should list only pending approvals")
    void testListPending() {
        ApprovalRequest req1 = ApprovalRequest.pending("req-1", "file_write", Map.of(), "FILESYSTEM", null, null);
        ApprovalRequest req2 = ApprovalRequest.pending("req-2", "shell_exec", Map.of(), "SYSTEM", null, null);

        store.register(req1, new CompletableFuture<>());
        store.register(req2, new CompletableFuture<>());

        List<ApprovalRequest> pending = store.listPending();
        assertThat(pending).hasSize(2);

        // Update req1 to APPROVED
        store.update(req1.asApproved());

        List<ApprovalRequest> pendingAfter = store.listPending();
        assertThat(pendingAfter).hasSize(1);
        assertThat(pendingAfter.getFirst().id()).isEqualTo("req-2");
    }

    @Test
    @DisplayName("Should list all requests with limit")
    void testListAllWithLimit() {
        for (int i = 1; i <= 10; i++) {
            ApprovalRequest req = ApprovalRequest.pending("req-" + i, "tool", Map.of(), "CAT", null, null);
            store.register(req, new CompletableFuture<>());
        }

        assertThat(store.listAll(5)).hasSize(5);
        assertThat(store.listAll(20)).hasSize(10);
    }

    @Test
    @DisplayName("Should evict resolved entries older than threshold")
    void testEvictOlderThan() throws InterruptedException {
        ApprovalRequest req = ApprovalRequest.pending("req-old", "tool", Map.of(), "CAT", null, null);
        store.register(req, new CompletableFuture<>());

        // Mark as approved (resolved)
        store.update(req.asApproved());

        Thread.sleep(20);

        int evicted = store.evictOlderThan(Duration.ofMillis(10));
        assertThat(evicted).isEqualTo(1);
        assertThat(store.get("req-old")).isEmpty();
    }
}
