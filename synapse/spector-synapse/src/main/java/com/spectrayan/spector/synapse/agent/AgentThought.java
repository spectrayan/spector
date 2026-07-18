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
 * Represents an internal thought process/monologue of the agent.
 *
 * <p>Part of the ReAct (Reasoning + Acting) pattern: Thought → Action → Observation.</p>
 *
 * @param content   the thought text
 * @param timestamp when the thought occurred
 */
public record AgentThought(String content, Instant timestamp) {

    public AgentThought {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thought content cannot be null or empty");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public AgentThought(String content) {
        this(content, Instant.now());
    }
}
