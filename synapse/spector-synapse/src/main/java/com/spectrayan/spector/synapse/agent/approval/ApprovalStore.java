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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store for pending and resolved approval requests.
 */
@Component
public class ApprovalStore {

    private static final Logger log = LoggerFactory.getLogger(ApprovalStore.class);

    private static final int DEFAULT_MAX_HISTORY = 500;

    public record Entry(
            ApprovalRequest request,
            CompletableFuture<ApprovalRequest> future
    ) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Registers a new pending approval request with its associated completion future.
     */
    public ApprovalRequest register(ApprovalRequest request, CompletableFuture<ApprovalRequest> future) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(future, "future must not be null");

        entries.put(request.id(), new Entry(request, future));
        log.info("[ApprovalStore] Registered pending approval id={} tool={}", request.id(), request.toolName());
        return request;
    }

    /**
     * Retrieves an approval request by ID.
     */
    public Optional<ApprovalRequest> get(String id) {
        if (id == null) return Optional.empty();
        Entry entry = entries.get(id);
        return entry != null ? Optional.of(entry.request()) : Optional.empty();
    }

    /**
     * Retrieves the completion future for a pending approval request.
     */
    public Optional<CompletableFuture<ApprovalRequest>> getFuture(String id) {
        if (id == null) return Optional.empty();
        Entry entry = entries.get(id);
        return entry != null ? Optional.ofNullable(entry.future()) : Optional.empty();
    }

    /**
     * Updates an existing approval request (e.g. status transition).
     */
    public boolean update(ApprovalRequest updatedRequest) {
        Objects.requireNonNull(updatedRequest, "updatedRequest must not be null");
        Entry existing = entries.get(updatedRequest.id());
        if (existing == null) {
            return false;
        }
        entries.put(updatedRequest.id(), new Entry(updatedRequest, existing.future()));
        return true;
    }

    /**
     * Lists all currently pending approval requests, ordered by creation time (newest first).
     */
    public List<ApprovalRequest> listPending() {
        return entries.values().stream()
                .map(Entry::request)
                .filter(r -> r.status() == ApprovalStatus.PENDING)
                .sorted(Comparator.comparing(ApprovalRequest::createdAt).reversed())
                .toList();
    }

    /**
     * Lists all approval requests (pending and historical), ordered by creation time (newest first).
     */
    public List<ApprovalRequest> listAll(int limit) {
        int max = limit > 0 ? limit : DEFAULT_MAX_HISTORY;
        return entries.values().stream()
                .map(Entry::request)
                .sorted(Comparator.comparing(ApprovalRequest::createdAt).reversed())
                .limit(max)
                .toList();
    }

    /**
     * Evicts resolved entries older than the specified duration to prevent unbounded memory growth.
     */
    public int evictOlderThan(Duration maxAge) {
        Instant threshold = Instant.now().minus(maxAge);
        int evicted = 0;
        for (var it = entries.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next().getValue();
            if (entry.request().status() != ApprovalStatus.PENDING
                    && entry.request().resolvedAt() != null
                    && entry.request().resolvedAt().isBefore(threshold)) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("[ApprovalStore] Evicted {} old approval entries", evicted);
        }
        return evicted;
    }

    /**
     * Clears all stored entries (primarily for test resets).
     */
    public void clear() {
        entries.clear();
    }

    /**
     * Current total entries count.
     */
    public int size() {
        return entries.size();
    }
}
