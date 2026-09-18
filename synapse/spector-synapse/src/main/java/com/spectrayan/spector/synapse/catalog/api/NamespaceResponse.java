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

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;

/**
 * Standard REST representation of a catalog namespace record (ADR-0029 §8.1).
 *
 * @param namespaceId    globally unique immutable TSID
 * @param slug           account-scoped alias
 * @param ownerAccountId owning account TSID
 * @param type           namespace type
 * @param status         lifecycle status
 * @param displayName    human-readable display name
 * @param description    human-readable description
 * @param bias           domain bias overlay
 * @param createdAt      creation timestamp
 * @param lastAccessedAt last access timestamp
 * @param legalHold      whether the namespace is under enterprise legal hold
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NamespaceResponse(
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
        boolean legalHold) {

    public static NamespaceResponse from(NamespaceRecord record) {
        return new NamespaceResponse(
                record.namespaceId(),
                record.slug(),
                record.ownerAccountId(),
                record.type(),
                record.status(),
                record.displayName(),
                record.description(),
                record.bias(),
                record.createdAt(),
                record.lastAccessedAt(),
                record.legalHold()
        );
    }
}
