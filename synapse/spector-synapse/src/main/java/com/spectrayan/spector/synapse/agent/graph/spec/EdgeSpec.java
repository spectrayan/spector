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
