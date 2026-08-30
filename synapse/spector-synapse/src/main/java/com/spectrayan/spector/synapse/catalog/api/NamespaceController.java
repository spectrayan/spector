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
package com.spectrayan.spector.synapse.catalog.api;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * REST controller for catalog-plane namespace lifecycle management (ADR-0029 §8.1).
 *
 * <p>All endpoints operate on the catalog metadata layer without mapping or locking
 * data-plane memory bundles.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/namespaces", produces = MediaType.APPLICATION_JSON_VALUE)
public class NamespaceController {

    private static final Logger log = LoggerFactory.getLogger(NamespaceController.class);

    private final AccountCatalog catalog;

    public NamespaceController(AccountCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Lists all accessible namespaces for the authenticated account (owned + granted).
     */
    @GetMapping
    public ResponseEntity<List<NamespaceResponse>> listNamespaces() {
        String accountId = SecurityUtils.getUserId();
        log.debug("[NamespaceController] listNamespaces for account={}", accountId);
        List<NamespaceResponse> responses = catalog.listAccessible(accountId).stream()
                .map(NamespaceResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Creates a new namespace for the authenticated account.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NamespaceResponse> createNamespace(@RequestBody CreateNamespaceRequest request) {
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceController] createNamespace: account={}, slug={}, type={}",
                accountId, request.slug(), request.type());

        NamespaceType type = request.type() != null ? request.type() : NamespaceType.PROJECT;
        NamespaceRecord created = catalog.createNamespace(
                accountId,
                request.slug(),
                type,
                request.displayName(),
                request.description(),
                request.bias()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(NamespaceResponse.from(created));
    }

    /**
     * Retrieves namespace details by slug or namespaceId.
     */
    @GetMapping("/{slugOrId}")
    public ResponseEntity<NamespaceResponse> getNamespace(@PathVariable String slugOrId) {
        String accountId = SecurityUtils.getUserId();
        log.debug("[NamespaceController] getNamespace: account={}, slugOrId={}", accountId, slugOrId);
        NamespaceRecord record = catalog.resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));
        return ResponseEntity.ok(NamespaceResponse.from(record));
    }

    /**
     * Updates mutable metadata of an existing namespace.
     */
    @PutMapping(value = "/{slugOrId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NamespaceResponse> updateNamespace(
            @PathVariable String slugOrId,
            @RequestBody UpdateNamespaceRequest request) {
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceController] updateNamespace: account={}, slugOrId={}", accountId, slugOrId);
        NamespaceRecord updated = catalog.updateNamespace(
                accountId,
                slugOrId,
                request.displayName(),
                request.description(),
                request.type(),
                request.bias()
        );
        return ResponseEntity.ok(NamespaceResponse.from(updated));
    }

    /**
     * Soft-deletes (tombstones) a namespace. The default namespace cannot be deleted.
     */
    @DeleteMapping("/{slugOrId}")
    public ResponseEntity<Void> deleteNamespace(@PathVariable String slugOrId) {
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceController] deleteNamespace: account={}, slugOrId={}", accountId, slugOrId);
        catalog.tombstone(accountId, slugOrId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resets a namespace by purging its memory state while keeping catalog registration.
     */
    @PostMapping("/{slugOrId}/reset")
    public ResponseEntity<Map<String, String>> resetNamespace(@PathVariable String slugOrId) {
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceController] resetNamespace: account={}, slugOrId={}", accountId, slugOrId);
        catalog.resetNamespace(accountId, slugOrId);
        return ResponseEntity.ok(Map.of("status", "reset", "namespace", slugOrId));
    }
}
