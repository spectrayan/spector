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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.identity.IdentityPlane;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * REST controller for account-level default namespace and introspection (ADR-0029 §8.1, §21).
 */
@RestController
@RequestMapping(value = "/api/v1/account", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountDefaultController {

    private static final Logger log = LoggerFactory.getLogger(AccountDefaultController.class);

    private final AccountCatalog catalog;
    private final IdentityPlane identityPlane;

    @org.springframework.beans.factory.annotation.Autowired
    public AccountDefaultController(
            AccountCatalog catalog,
            @org.springframework.beans.factory.annotation.Autowired(required = false) IdentityPlane identityPlane) {
        this.catalog = catalog;
        this.identityPlane = identityPlane;
    }

    public AccountDefaultController(AccountCatalog catalog) {
        this(catalog, null);
    }

    /**
     * Account introspection endpoint (ADR-0029 §21).
     */
    @GetMapping("/introspect")
    public ResponseEntity<AccountIntrospectResponse> introspect() {
        String accountId = SecurityUtils.getUserId();
        Account account = catalog.getAccount(accountId);
        List<NamespaceRecord> records = catalog.listAccessible(accountId);

        Map<String, String> slugMap = new HashMap<>();
        List<NamespaceResponse> nsResponses = new ArrayList<>();
        for (NamespaceRecord r : records) {
            slugMap.put(r.slug(), r.namespaceId());
            nsResponses.add(NamespaceResponse.from(r));
        }

        Short soulVersion = null;
        if (identityPlane != null) {
            soulVersion = identityPlane.primarySoulFor(accountId)
                    .map(SoulContext::soulVersion)
                    .orElse(null);
        }

        AccountIntrospectResponse response = new AccountIntrospectResponse(
                account.id(),
                account.displayName(),
                account.kind() != null ? account.kind().name() : null,
                account.profile() != null ? account.profile().name() : null,
                account.defaultNamespaceId(),
                account.quotas(),
                account.flags(),
                account.tenantId(),
                account.legalHold(),
                slugMap,
                nsResponses,
                List.of(),
                soulVersion
        );
        return ResponseEntity.ok(response);
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
