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

import java.time.Instant;

/**
 * Catalog representation of an authenticated principal. The account is NOT a namespace — it owns namespaces.
 * The default namespace has namespaceId equal to accountId.
 *
 * @param id the unique account identifier
 * @param kind the kind of principal (human, agent, service)
 * @param profile the account profile metadata
 * @param displayName the human-readable display name
 * @param quotas the resource and rate quotas assigned to the account
 * @param flags the feature flags and account-level overrides
 * @param defaultNamespaceId the identifier of the default namespace owned by this account
 * @param createdAt the timestamp when the account was created
 */
public record Account(
        String id,
        PrincipalKind kind,
        AccountProfile profile,
        String displayName,
        AccountQuotas quotas,
        AccountFlags flags,
        String defaultNamespaceId,
        Instant createdAt
) {
}
