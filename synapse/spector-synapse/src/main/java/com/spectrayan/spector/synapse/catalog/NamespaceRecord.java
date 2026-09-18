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
 * @param legalHold whether the namespace is placed under enterprise legal hold
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
        Instant lastAccessedAt,
        boolean legalHold
) {
    public NamespaceRecord(
            String namespaceId,
            String slug,
            String ownerAccountId,
            NamespaceType type,
            NamespaceStatus status,
            String displayName,
            String description,
            NamespaceBias bias,
            Instant createdAt,
            Instant lastAccessedAt) {
        this(namespaceId, slug, ownerAccountId, type, status, displayName, description, bias, createdAt, lastAccessedAt, false);
    }
}
