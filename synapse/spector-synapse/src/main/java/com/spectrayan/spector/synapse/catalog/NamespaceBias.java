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
