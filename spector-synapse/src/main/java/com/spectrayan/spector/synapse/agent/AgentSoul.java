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
import java.util.List;
import java.util.Map;

/**
 * Agent Soul — the identity, personality, and capabilities of an agent.
 *
 * <p>An AgentSoul defines who the agent is: its name, system prompt, personality
 * traits, preferred model, and available tools. Souls are persisted to H2 and
 * can be customized per use case.</p>
 *
 * @param id            unique identifier
 * @param name          display name (e.g., "Research Assistant")
 * @param description   brief description of purpose
 * @param systemPrompt  system-level instructions for the LLM
 * @param personality   key-value personality traits (JSON-serialized)
 * @param model         preferred LLM model (e.g., "llama3.2")
 * @param tools         list of enabled tool names
 * @param createdAt     creation timestamp
 * @param updatedAt     last update timestamp
 */
public record AgentSoul(
        String id,
        String name,
        String description,
        String systemPrompt,
        Map<String, String> personality,
        String model,
        List<String> tools,
        Instant createdAt,
        Instant updatedAt
) {
    /** Creates a minimal agent soul with defaults. */
    public static AgentSoul of(String id, String name, String systemPrompt) {
        Instant now = Instant.now();
        return new AgentSoul(id, name, null, systemPrompt,
                Map.of(), null, List.of(), now, now);
    }
}
