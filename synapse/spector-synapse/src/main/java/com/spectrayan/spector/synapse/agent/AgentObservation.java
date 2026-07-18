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

import java.time.Instant;

/**
 * Represents the observed result of executing an {@link AgentAction}.
 *
 * <p>Part of the ReAct (Reasoning + Acting) pattern: Thought → Action → Observation.</p>
 *
 * @param result    the tool execution result
 * @param timestamp when the observation was recorded
 */
public record AgentObservation(String result, Instant timestamp) {

    public AgentObservation {
        if (result == null) {
            result = "";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public AgentObservation(String result) {
        this(result, Instant.now());
    }
}
