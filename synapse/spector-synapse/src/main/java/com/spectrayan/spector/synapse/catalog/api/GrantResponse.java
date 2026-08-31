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

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantConstraints;
import com.spectrayan.spector.synapse.catalog.GrantRole;

/**
 * Response payload representing an active grant.
 *
 * @param grantId          unique grant identifier
 * @param granteeAccountId identifier of the grantee account
 * @param namespaceId      target namespace identifier
 * @param role             grant role
 * @param grantedBy        account that granted access
 * @param grantedAt        creation timestamp
 * @param expiresAt        expiration timestamp, nullable
 * @param constraints      grant constraints, nullable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrantResponse(
        String grantId,
        String granteeAccountId,
        String namespaceId,
        GrantRole role,
        String grantedBy,
        Instant grantedAt,
        Instant expiresAt,
        GrantConstraints constraints
) {
    public static GrantResponse from(Grant grant) {
        return new GrantResponse(
                grant.grantId(),
                grant.principalId(),
                grant.objectId(),
                grant.role(),
                grant.grantedBy(),
                grant.grantedAt(),
                grant.expiresAt(),
                grant.constraints()
        );
    }
}
