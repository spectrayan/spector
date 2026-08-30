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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Catalog entry for a namespace (rememberer ρ). The slug is an account-scoped mutable alias.
 * The namespaceId is a globally unique immutable TSID that names the data-plane directory.
 * Bias is nullable.
 *
 * @param namespaceId globally unique immutable TSID naming the data-plane directory
 * @param slug account-scoped mutable alias for the namespace
 * @param ownerAccountId identifier of the owning account
 * @param type type of namespace
 * @param status operational status of the namespace
 * @param displayName human-readable display name
 * @param description detailed description of the namespace
 * @param bias optional soft domain tilt for salience scoring, nullable
 * @param createdAt timestamp when the namespace was created
 * @param lastAccessedAt timestamp when the namespace was last accessed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NamespaceRecord(
        String namespaceId,
        String slug,
        String ownerAccountId,
        NamespaceType type,
        NamespaceStatus status,
        String displayName,
        String description,
        NamespaceBias bias,
        Instant createdAt,
        Instant lastAccessedAt
) {
}
