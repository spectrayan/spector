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

/**
 * Simple edge connecting two nodes.
 *
 * @param from      source node name
 * @param to        target node name ("END" for terminal)
 * @param condition edge condition: "always", "on_tool_calls", or custom
 * @param label     human-readable label
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeSpec(
        String from,
        String to,
        String condition,
        String label
) {
    public EdgeSpec {
        if (condition == null || condition.isBlank()) condition = "always";
    }
}
