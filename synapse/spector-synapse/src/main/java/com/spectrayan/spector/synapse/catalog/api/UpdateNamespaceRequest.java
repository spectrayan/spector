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
 * Request payload for updating namespace mutable metadata (ADR-0029 §8.1).
 *
 * @param displayName new display name, or null to keep current
 * @param description new description, or null to keep current
 * @param type        new namespace type, or null to keep current
 * @param bias        new namespace bias overlay, or null to keep current
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateNamespaceRequest(
        String displayName,
        String description,
        NamespaceType type,
        NamespaceBias bias) {
}
