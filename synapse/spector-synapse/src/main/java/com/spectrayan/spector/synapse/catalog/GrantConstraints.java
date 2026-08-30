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

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional constraints on a grant. All fields are nullable. Empty tierMask means all cognitive tiers.
 * Constraints are intersected with the role — a READER cannot be granted remember.
 *
 * @param tierMask set of cognitive tier identifiers allowed by this grant, or null/empty for all tiers
 * @param tagPrefix prefix filter for tags accessible under this grant, nullable
 * @param operations set of specific permitted operations, nullable
 * @param regionIds set of identity region identifiers permitted by this grant, nullable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrantConstraints(
        Set<String> tierMask,
        String tagPrefix,
        Set<String> operations,
        Set<String> regionIds
) {
}
