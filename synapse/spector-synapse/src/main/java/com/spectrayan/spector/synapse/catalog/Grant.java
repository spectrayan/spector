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
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Authorization grant on a namespace, identity bundle, or identity region. Exactly one OWNER
 * per namespace. INJECT on IDENTITY_REGION(SOUL) does NOT imply READ on any NAMESPACE traces.
 * ExpiresAt null means no expiry. Role is used when objectType is NAMESPACE; actions are used
 * when objectType is IDENTITY_BUNDLE or IDENTITY_REGION.
 *
 * @param grantId unique grant identifier
 * @param objectType type of object being granted access to
 * @param objectId identifier of the target object
 * @param principalId identifier of the principal receiving the grant
 * @param principalType type of principal (account, org unit, etc.)
 * @param role role-based access level when objectType is NAMESPACE, nullable otherwise
 * @param actions set of fine-grained actions when objectType is IDENTITY_BUNDLE or IDENTITY_REGION, nullable otherwise
 * @param grantedBy identifier of the principal that created this grant
 * @param grantedAt timestamp when the grant was created
 * @param expiresAt timestamp when the grant expires, or null if it does not expire
 * @param constraints optional additional constraints restricting this grant, nullable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Grant(
        String grantId,
        GrantObjectType objectType,
        String objectId,
        String principalId,
        PrincipalType principalType,
        GrantRole role,
        Set<GrantAction> actions,
        String grantedBy,
        Instant grantedAt,
        Instant expiresAt,
        GrantConstraints constraints
) {

    /**
     * Checks if this grant is expired based on current time.
     *
     * @return {@code true} if an expiry timestamp is set and is before the current instant, {@code false} otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
