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
package com.spectrayan.spector.synapse.agent;

import java.util.List;

/**
 * Agent capability discovery card manifest (A2A compatible).
 */
public record AgentCard(
        String name,
        String description,
        String version,
        List<String> capabilities,
        List<String> tools,
        List<String> input_modes,
        List<String> output_modes,
        String endpoint
) {

    /**
     * Converts an {@link AgentSoul} into its discoverable {@link AgentCard} manifest.
     *
     * @param soul the agent soul
     * @return the agent card manifest
     */
    public static AgentCard from(AgentSoul soul) {
        if (soul == null) {
            return new AgentCard(
                    "Default Agent",
                    "Cognitive memory assistant",
                    "1.0",
                    List.of(),
                    List.of(),
                    List.of("text"),
                    List.of("text"),
                    "/api/v1/agents/default/invoke"
            );
        }

        String id = soul.id() != null ? soul.id() : "default";
        String name = soul.name() != null ? soul.name() : "Agent " + id;
        String desc = soul.description() != null ? soul.description() :
                (soul.purpose() != null ? soul.purpose() : "Spector Cognitive Agent");

        List<String> enabledTools = soul.tools() != null ? soul.tools() : List.of();

        return new AgentCard(
                name,
                desc,
                "1.0",
                enabledTools,
                enabledTools,
                List.of("text"),
                List.of("text"),
                "/api/v1/agents/" + id + "/invoke"
        );
    }
}
