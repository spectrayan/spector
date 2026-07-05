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
