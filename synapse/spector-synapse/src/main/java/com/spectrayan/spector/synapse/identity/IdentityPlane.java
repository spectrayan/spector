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
package com.spectrayan.spector.synapse.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.identity.IdentityBundle;
import com.spectrayan.spector.memory.identity.IdentityRegionId;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantAction;

/**
 * Service coordinating identity bundle lifecycle, soul stack assembly, and Region 24 migration (ADR-0029 §23, §24).
 */
@Service
public class IdentityPlane {

    private static final Logger log = LoggerFactory.getLogger(IdentityPlane.class);

    private final IdentityCache identityCache;
    private final ObjectMapper mapper;
    private final AccountCatalog catalog;

    public IdentityPlane(IdentityCache identityCache, ObjectMapper mapper, AccountCatalog catalog) {
        this.identityCache = identityCache;
        this.mapper = mapper;
        this.catalog = catalog;
    }

    /**
     * Reads the primary soul for the given account with PEP authorization check (ADR-0029 §24).
     *
     * @param accountId account TSID (also used as caller for own-account access)
     * @return optional primary soul
     */
    public Optional<SoulContext> primarySoulFor(String accountId) {
        if (accountId == null || accountId.isBlank() || "default".equals(accountId)) {
            return Optional.empty();
        }
        // PEP check: caller must have READ on their own SOUL region
        if (!catalog.authorizeIdentity(accountId, accountId, IdentityRegionId.SOUL.name(), GrantAction.READ)) {
            log.warn("[IdentityPlane] Identity read denied for account {} on SOUL region", accountId);
            return Optional.empty();
        }
        IdentityBundle bundle = identityCache.getOrOpenAccount(accountId);
        return bundle.readSoul();
    }

    /**
     * Reads the salience profile for the given account.
     *
     * @param accountId account TSID
     * @return optional salience profile
     */
    public Optional<SalienceProfile> salienceFor(String accountId) {
        if (accountId == null || accountId.isBlank() || "default".equals(accountId)) {
            return Optional.empty();
        }
        if (!catalog.authorizeIdentity(accountId, accountId, IdentityRegionId.SALIENCE.name(), GrantAction.READ)) {
            log.warn("[IdentityPlane] Identity read denied for account {} on SALIENCE region", accountId);
            return Optional.empty();
        }
        IdentityBundle bundle = identityCache.getOrOpenAccount(accountId);
        return bundle.readSalience();
    }

    /**
     * Assembles the hierarchical soul stack for a request context.
     *
     * @param tenantId   optional tenant TSID
     * @param orgUnitIds list of organizational unit IDs
     * @param accountId  account TSID
     * @return ordered list of soul contexts (ancestors first, primary last)
     */
    public List<SoulContext> soulsFor(String tenantId, List<String> orgUnitIds, String accountId) {
        List<SoulContext> stack = new ArrayList<>();

        if (tenantId != null && !tenantId.isBlank()) {
            if (catalog.authorizeIdentity(accountId, tenantId, IdentityRegionId.SOUL.name(), GrantAction.READ)) {
                IdentityBundle tenantBundle = identityCache.getOrOpenTenant(tenantId);
                tenantBundle.readSoul().ifPresent(stack::add);

                // Process org unit souls from tenant ORG_DIR region (ADR-0029 §2.5.2)
                if (orgUnitIds != null && !orgUnitIds.isEmpty()) {
                    for (String orgUnitId : orgUnitIds) {
                        if (orgUnitId == null || orgUnitId.isBlank()) {
                            continue;
                        }
                        tenantBundle.readOrgUnitSoul(orgUnitId).ifPresent(orgSoul -> {
                            stack.add(orgSoul);
                            log.trace("[IdentityPlane] Added org unit soul for tenantId={}, orgUnitId={}", tenantId, orgUnitId);
                        });
                    }
                }
            } else {
                log.warn("[IdentityPlane] Access denied for account {} on tenant {} SOUL region", accountId, tenantId);
            }
        }

        if (accountId != null && !accountId.isBlank() && !"default".equals(accountId)) {
            primarySoulFor(accountId).ifPresent(stack::add);
        }

        return List.copyOf(stack);
    }

    /**
     * Updates or sets the account's primary soul in {@code identity.bundle}.
     *
     * @param accountId account TSID
     * @param soul      the soul context
     */
    public void updateAccountSoul(String accountId, SoulContext soul) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        if (!catalog.authorizeIdentity(accountId, accountId, IdentityRegionId.SOUL.name(), GrantAction.WRITE)) {
            log.warn("[IdentityPlane] Identity write denied for account {} on SOUL region", accountId);
            return;
        }
        IdentityBundle bundle = identityCache.getOrOpenAccount(accountId);
        bundle.writeSoul(soul);
        log.debug("[IdentityPlane] Updated primary soul for account {}", accountId);
    }

    /**
     * Updates or sets the account's salience profile in {@code identity.bundle}.
     *
     * @param accountId account TSID
     * @param salience  the salience profile
     */
    public void updateAccountSalience(String accountId, SalienceProfile salience) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        if (!catalog.authorizeIdentity(accountId, accountId, IdentityRegionId.SALIENCE.name(), GrantAction.WRITE)) {
            log.warn("[IdentityPlane] Identity write denied for account {} on SALIENCE region", accountId);
            return;
        }
        IdentityBundle bundle = identityCache.getOrOpenAccount(accountId);
        bundle.writeSalience(salience);
        log.debug("[IdentityPlane] Updated salience profile for account {}", accountId);
    }

    /**
     * Updates or sets the tenant's primary soul in {@code identity.bundle}.
     *
     * @param tenantId tenant TSID
     * @param soul     the tenant soul context
     */
    public void updateTenantSoul(String tenantId, SoulContext soul) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        if (!catalog.authorizeIdentity(tenantId, tenantId, IdentityRegionId.SOUL.name(), GrantAction.WRITE)) {
            log.warn("[IdentityPlane] Identity write denied for tenant {} on SOUL region", tenantId);
            return;
        }
        IdentityBundle bundle = identityCache.getOrOpenTenant(tenantId);
        bundle.writeSoul(soul);
        log.debug("[IdentityPlane] Updated primary soul for tenant {}", tenantId);
    }

    /**
     * Performs copy-once migration from default rememberer Region 24 to {@code identity.bundle} (ADR-0029 §23.6).
     *
     * @param accountId     the account TSID
     * @param defaultMemory the default namespace SpectorMemory instance
     */
    public void checkAndMigrateRegion24(String accountId, SpectorMemory defaultMemory) {
        if (accountId == null || accountId.isBlank() || "default".equals(accountId) || defaultMemory == null) {
            return;
        }

        try {
            IdentityBundle bundle = identityCache.getOrOpenAccount(accountId);
            if (!bundle.isEmpty(IdentityRegionId.SOUL)) {
                return; // Already migrated or has soul
            }

            Optional<byte[]> insulaBytes = defaultMemory.admin().insularCortex().get();
            if (insulaBytes.isEmpty()) {
                return;
            }

            InsulaSelfModel model = mapper.readValue(insulaBytes.get(), InsulaSelfModel.class);
            if (model != null) {
                if (model.soul() != null) {
                    bundle.writeSoul(model.soul());
                    log.info("[IdentityPlane] Migrated Region 24 soul to identity.bundle for account {}", accountId);
                }
                if (model.salience() != null) {
                    bundle.writeSalience(model.salience());
                    log.info("[IdentityPlane] Migrated Region 24 salience to identity.bundle for account {}", accountId);
                }
            }
        } catch (Exception e) {
            log.warn("[IdentityPlane] Non-fatal Region 24 migration check failed for account {}: {}", accountId, e.getMessage());
        }
    }
}
