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
