/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
