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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe adapter for {@link AgentApprovalRepository}.
 */
@Repository
public class InMemoryAgentApprovalRepository implements AgentApprovalRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentApprovalRepository.class);

    private static final int DEFAULT_MAX_HISTORY = 500;

    private record Entry(
            AgentActionApproval approval,
            CompletableFuture<AgentActionApproval> future
    ) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public AgentActionApproval save(AgentActionApproval approval, CompletableFuture<AgentActionApproval> future) {
        Objects.requireNonNull(approval, "approval must not be null");
        Objects.requireNonNull(future, "future must not be null");

        entries.put(approval.id(), new Entry(approval, future));
        log.debug("[InMemoryAgentApprovalRepository] Saved approval id={} tool={}", approval.id(), approval.toolName());
        return approval;
    }

    @Override
    public Optional<AgentActionApproval> findById(String id) {
        if (id == null) return Optional.empty();
        Entry entry = entries.get(id);
        return entry != null ? Optional.of(entry.approval()) : Optional.empty();
    }

    @Override
    public Optional<CompletableFuture<AgentActionApproval>> findFutureById(String id) {
        if (id == null) return Optional.empty();
        Entry entry = entries.get(id);
        return entry != null ? Optional.ofNullable(entry.future()) : Optional.empty();
    }

    @Override
    public boolean update(AgentActionApproval approval) {
        Objects.requireNonNull(approval, "approval must not be null");
        Entry existing = entries.get(approval.id());
        if (existing == null) {
            return false;
        }
        entries.put(approval.id(), new Entry(approval, existing.future()));
        return true;
    }

    @Override
    public List<AgentActionApproval> findPending() {
        return entries.values().stream()
                .map(Entry::approval)
                .filter(AgentActionApproval::isPending)
                .sorted(Comparator.comparing(AgentActionApproval::createdAt).reversed())
                .toList();
    }

    @Override
    public List<AgentActionApproval> findAll(int limit) {
        int max = limit > 0 ? limit : DEFAULT_MAX_HISTORY;
        return entries.values().stream()
                .map(Entry::approval)
                .sorted(Comparator.comparing(AgentActionApproval::createdAt).reversed())
                .limit(max)
                .toList();
    }

    @Override
    public int deleteResolvedOlderThan(Duration maxAge) {
        Instant threshold = Instant.now().minus(maxAge);
        int evicted = 0;
        for (var it = entries.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next().getValue();
            if (entry.approval().status() != ApprovalStatus.PENDING
                    && entry.approval().resolvedAt() != null
                    && entry.approval().resolvedAt().isBefore(threshold)) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("[InMemoryAgentApprovalRepository] Evicted {} expired approvals", evicted);
        }
        return evicted;
    }

    @Override
    public void deleteAll() {
        entries.clear();
    }

    @Override
    public int count() {
        return entries.size();
    }
}
