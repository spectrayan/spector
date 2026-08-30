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

import java.util.List;
import java.util.Map;

/**
 * Optional soft domain tilt for a namespace. This is NOT a SoulContext — it adjusts the account
 * salience profile at bind time via a request-scoped overlay. Empty bias has no effect on scoring.
 *
 * @param domainFocus list of domain identifiers to prioritize in scoring
 * @param tagWeights mapping of tag names to their scoring weight multipliers
 */
public record NamespaceBias(
        List<String> domainFocus,
        Map<String, Float> tagWeights
) {

    /**
     * An empty namespace bias with no domain focus and no tag weights.
     */
    public static final NamespaceBias EMPTY = new NamespaceBias(List.of(), Map.of());
}
