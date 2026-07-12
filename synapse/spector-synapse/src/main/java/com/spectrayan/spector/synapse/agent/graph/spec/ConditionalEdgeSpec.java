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
package com.spectrayan.spector.synapse.agent.graph.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Conditional edge with routing logic.
 *
 * @param from             source node name
 * @param conditionField   state field to evaluate (e.g. "decision")
 * @param conditionMapping maps field values to target node names
 * @param defaultTarget    fallback target if no mapping matches
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConditionalEdgeSpec(
        String from,
        @JsonProperty("condition_field") String conditionField,
        @JsonProperty("condition_mapping") Map<String, String> conditionMapping,
        @JsonProperty("default_target") String defaultTarget
) {
    public ConditionalEdgeSpec {
        if (conditionMapping == null) conditionMapping = Map.of();
        if (defaultTarget == null) defaultTarget = "END";
    }
}
