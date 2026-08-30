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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;
import com.spectrayan.spector.synapse.catalog.NamespaceType;

/**
 * Request payload for creating a new namespace (ADR-0029 §8.1).
 * The caller supplies the {@code slug}; the system allocates the immutable TSID {@code namespaceId}.
 *
 * @param slug        human-readable slug, unique per account (1-63 alphanumeric/hyphen/underscore)
 * @param type        namespace type (default: PROJECT)
 * @param displayName optional human-readable display name
 * @param description optional human-readable description
 * @param bias        optional namespace domain bias overlay
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateNamespaceRequest(
        String slug,
        NamespaceType type,
        String displayName,
        String description,
        NamespaceBias bias) {
}
