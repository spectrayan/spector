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
import com.spectrayan.spector.synapse.catalog.GrantConstraints;
import com.spectrayan.spector.synapse.catalog.GrantRole;

/**
 * Request payload for creating or updating a trace grant on a namespace.
 *
 * @param granteeAccountId the account identifier receiving the grant
 * @param role             the grant role (READER, WRITER, ADMIN)
 * @param expiresAt        optional expiration timestamp, or null for no expiry
 * @param constraints      optional grant constraints, nullable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateGrantRequest(
        String granteeAccountId,
        GrantRole role,
        Instant expiresAt,
        GrantConstraints constraints
) {
}
