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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;

/**
 * Account-level introspection metadata (ADR-0029 §21).
 * Reports profile, flags, quotas, slug mappings, accessible namespaces, active grants, and soul version.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountIntrospectResponse(
        String accountId,
        String displayName,
        String kind,
        String profile,
        String defaultNamespaceId,
        AccountQuotas quotas,
        AccountFlags flags,
        String tenantId,
        boolean legalHold,
        Map<String, String> slugMap,
        List<NamespaceResponse> namespaces,
        List<GrantResponse> activeGrants,
        Short soulVersion
) {
}
