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
package com.spectrayan.spector.synapse.agent.approval.repository;

import com.spectrayan.spector.synapse.agent.approval.model.AgentActionApproval;
import com.spectrayan.spector.synapse.agent.approval.model.ApprovalStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryAgentApprovalRepository Tests")
class InMemoryAgentApprovalRepositoryTest {

    private InMemoryAgentApprovalRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAgentApprovalRepository();
    }

    @Test
    @DisplayName("Should save and find approval entity by ID")
    void testSaveAndFindById() {
        AgentActionApproval approval = AgentActionApproval.pending(
                "appr-1", "file_write", Map.of("path", "data.txt"), "FILESYSTEM", "sess-1", "agent-1"
        );
        CompletableFuture<AgentActionApproval> future = new CompletableFuture<>();

        repository.save(approval, future);

        Optional<AgentActionApproval> found = repository.findById("appr-1");
        assertThat(found).isPresent();
        assertThat(found.get().toolName()).isEqualTo("file_write");
        assertThat(found.get().status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(found.get().arguments()).containsEntry("path", "data.txt");

        Optional<CompletableFuture<AgentActionApproval>> foundFuture = repository.findFutureById("appr-1");
        assertThat(foundFuture).isPresent().containsSame(future);
    }

    @Test
    @DisplayName("Should find pending approvals only")
    void testFindPending() {
        AgentActionApproval appr1 = AgentActionApproval.pending("appr-1", "file_write", Map.of(), "FILESYSTEM", null, null);
        AgentActionApproval appr2 = AgentActionApproval.pending("appr-2", "shell_exec", Map.of(), "SYSTEM", null, null);

        repository.save(appr1, new CompletableFuture<>());
        repository.save(appr2, new CompletableFuture<>());

        List<AgentActionApproval> pending = repository.findPending();
        assertThat(pending).hasSize(2);

        repository.update(appr1.asApproved());

        List<AgentActionApproval> pendingAfter = repository.findPending();
        assertThat(pendingAfter).hasSize(1);
        assertThat(pendingAfter.getFirst().id()).isEqualTo("appr-2");
    }

    @Test
    @DisplayName("Should list all with limit")
    void testFindAllWithLimit() {
        for (int i = 1; i <= 10; i++) {
            AgentActionApproval appr = AgentActionApproval.pending("appr-" + i, "tool", Map.of(), "CAT", null, null);
            repository.save(appr, new CompletableFuture<>());
        }

        assertThat(repository.findAll(5)).hasSize(5);
        assertThat(repository.findAll(20)).hasSize(10);
    }

    @Test
    @DisplayName("Should delete resolved records older than threshold")
    void testDeleteResolvedOlderThan() throws InterruptedException {
        AgentActionApproval appr = AgentActionApproval.pending("appr-old", "tool", Map.of(), "CAT", null, null);
        repository.save(appr, new CompletableFuture<>());
        repository.update(appr.asApproved());

        Thread.sleep(20);

        int evicted = repository.deleteResolvedOlderThan(Duration.ofMillis(10));
        assertThat(evicted).isEqualTo(1);
        assertThat(repository.findById("appr-old")).isEmpty();
    }
}
