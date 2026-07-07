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
