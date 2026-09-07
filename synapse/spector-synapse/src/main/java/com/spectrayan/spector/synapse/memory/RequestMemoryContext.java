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
package com.spectrayan.spector.synapse.memory;

import java.util.List;
import java.util.Set;

import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.GrantRole;

/**
 * Encapsulates the resolved security and memory context for an active request or session (ADR-0029 §6.1).
 *
 * @param tenantId          the tenant identifier (nullable on OSS)
 * @param orgUnitIds        the effective organizational unit identifiers
 * @param accountId         the authenticated account TSID
 * @param namespaceId       the resolved memory namespace TSID
 * @param slug              the requested or resolved namespace slug
 * @param role              the principal's authorization role on this namespace
 * @param allowSet          allowed namespace slugs / IDs from JWT claims (empty = unrestricted)
 * @param sessionId         optional session or connection ID
 * @param soulStack         the resolved hierarchical soul stack
 * @param primarySoul       the primary soul context
 * @param effectiveSalience the resolved effective salience profile for this request
 */
public record RequestMemoryContext(
        String tenantId,
        List<String> orgUnitIds,
        String accountId,
        String namespaceId,
        String slug,
        GrantRole role,
        Set<String> allowSet,
        String sessionId,
        List<SoulContext> soulStack,
        SoulContext primarySoul,
        SalienceProfile effectiveSalience
) {
    public RequestMemoryContext {
        orgUnitIds = orgUnitIds != null ? List.copyOf(orgUnitIds) : List.of();
        allowSet = allowSet != null ? Set.copyOf(allowSet) : Set.of();
        soulStack = soulStack != null ? List.copyOf(soulStack) : List.of();
    }

    public RequestMemoryContext(
            String tenantId,
            List<String> orgUnitIds,
            String accountId,
            String namespaceId,
            String slug,
            GrantRole role,
            Set<String> allowSet,
            String sessionId,
            List<SoulContext> soulStack,
            SoulContext primarySoul
    ) {
        this(tenantId, orgUnitIds, accountId, namespaceId, slug, role, allowSet, sessionId, soulStack, primarySoul, null);
    }
}

