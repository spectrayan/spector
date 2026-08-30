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
package com.spectrayan.spector.synapse.catalog;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-request binding context assembled during the resolution chain. Contains the authenticated
 * principal identity, the resolved namespace, the effective grant role, and the token allow-set.
 * Soul stack assembly happens downstream in the memory layer after catalog resolution.
 *
 * @param tenantId    tenant identifier (nullable on OSS)
 * @param orgUnitIds  list of organizational unit identifiers
 * @param accountId   account identifier
 * @param namespaceId resolved namespace identifier
 * @param slug        namespace slug
 * @param role        effective grant role
 * @param allowSet    action allow-set from token (empty implies all granted)
 * @param sessionId   optional session identifier (nullable; MCP connection ID)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMemoryContext(
        String tenantId,
        List<String> orgUnitIds,
        String accountId,
        String namespaceId,
        String slug,
        GrantRole role,
        Set<String> allowSet,
        String sessionId
) {
}
