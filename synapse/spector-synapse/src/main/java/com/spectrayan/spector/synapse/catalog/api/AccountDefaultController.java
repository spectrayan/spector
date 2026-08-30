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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * REST controller for account-level default namespace configuration (ADR-0029 §8.1).
 */
@RestController
@RequestMapping(value = "/api/v1/account", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountDefaultController {

    private static final Logger log = LoggerFactory.getLogger(AccountDefaultController.class);

    private final AccountCatalog catalog;

    public AccountDefaultController(AccountCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Persists a new default namespace for the authenticated account.
     */
    @PutMapping(value = "/default-namespace", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> setDefaultNamespace(
            @RequestBody SetDefaultNamespaceRequest request) {
        if (request.namespace() == null || request.namespace().isBlank()) {
            throw new IllegalArgumentException("Namespace identifier or slug must not be blank");
        }

        String accountId = SecurityUtils.getUserId();
        String slugOrId = request.namespace().trim();
        log.info("[AccountDefaultController] setDefaultNamespace: account={}, target={}", accountId, slugOrId);

        NamespaceRecord record = catalog.resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        catalog.setDefaultNamespace(accountId, record.namespaceId());
        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "accountId", accountId,
                "defaultNamespaceId", record.namespaceId(),
                "slug", record.slug()
        ));
    }
}
