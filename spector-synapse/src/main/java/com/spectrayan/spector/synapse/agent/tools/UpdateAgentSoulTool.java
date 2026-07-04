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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentSoul;
import com.spectrayan.spector.synapse.agent.AgentSoulRepository;
import com.spectrayan.spector.synapse.agent.AgentTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent tool that lets the agent propose modifications to its own identity (soul).
 *
 * <h3>When the LLM should call this</h3>
 * <p>When the user gives feedback about the agent's behavior, expertise, or style:</p>
 * <ul>
 *   <li>"Please be more direct in your responses"</li>
 *   <li>"Your name should be Aria"</li>
 *   <li>"You should focus on research tasks"</li>
 * </ul>
 *
 * <h3>Write Tool — Requires User Approval</h3>
 * <p>Mutations to the agent identity are surfaced as pending tool calls
 * in the UI for user confirmation before execution.</p>
 */
@Component
public class UpdateAgentSoulTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(UpdateAgentSoulTool.class);

    /** Fields the agent CAN modify (with user approval). */
    private static final Set<String> MUTABLE_FIELDS = Set.of(
            "name", "description", "system_prompt", "model", "personality");

    private final AgentSoulRepository soulRepository;

    public UpdateAgentSoulTool(AgentSoulRepository soulRepository) {
        this.soulRepository = soulRepository;
    }

    @Override public String name() { return "update_agent_soul"; }

    @Override public String description() {
        return "Proposes a modification to the agent's own identity (soul). "
                + "Call when the user gives feedback about behavior, expertise, or style. "
                + "Requires user confirmation. "
                + "Mutable fields: " + String.join(", ", MUTABLE_FIELDS);
    }

    @Override public ToolCategory category() { return ToolCategory.MEMORY; }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "field", Map.of("type", "string",
                                "description", "Soul field to update: " + String.join(", ", MUTABLE_FIELDS)),
                        "value", Map.of("type", "string",
                                "description", "The new value for the field"),
                        "personality_key", Map.of("type", "string",
                                "description", "For personality field: the key to set (e.g., 'style', 'tone')")
                ),
                "required", List.of("field", "value")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String field = String.valueOf(arguments.getOrDefault("field", "")).trim().toLowerCase();
        String value = String.valueOf(arguments.getOrDefault("value", "")).trim();

        if (field.isBlank()) return "ERROR: 'field' parameter is required";
        if (value.isBlank()) return "ERROR: 'value' parameter is required";

        if (!MUTABLE_FIELDS.contains(field)) {
            return "ERROR: Invalid field '" + field + "'. Mutable fields: "
                    + String.join(", ", MUTABLE_FIELDS);
        }

        try {
            AgentSoul current = soulRepository.loadDefault();

            AgentSoul updated = switch (field) {
                case "name" -> new AgentSoul(current.id(), value, current.description(),
                        current.systemPrompt(), current.personality(), current.model(),
                        current.tools(), current.createdAt(), Instant.now());

                case "description" -> new AgentSoul(current.id(), current.name(), value,
                        current.systemPrompt(), current.personality(), current.model(),
                        current.tools(), current.createdAt(), Instant.now());

                case "system_prompt" -> new AgentSoul(current.id(), current.name(), current.description(),
                        value, current.personality(), current.model(),
                        current.tools(), current.createdAt(), Instant.now());

                case "model" -> new AgentSoul(current.id(), current.name(), current.description(),
                        current.systemPrompt(), current.personality(), value,
                        current.tools(), current.createdAt(), Instant.now());

                case "personality" -> {
                    String key = String.valueOf(arguments.getOrDefault("personality_key", "trait")).trim();
                    var newPersonality = new HashMap<>(current.personality());
                    newPersonality.put(key, value);
                    yield new AgentSoul(current.id(), current.name(), current.description(),
                            current.systemPrompt(), Map.copyOf(newPersonality), current.model(),
                            current.tools(), current.createdAt(), Instant.now());
                }

                default -> throw new IllegalArgumentException("Unknown field: " + field);
            };

            soulRepository.save(updated);
            log.info("[UpdateSoulTool] Updated {} = '{}'", field, value);
            return "Updated agent soul: " + field + " = '" + value + "'";

        } catch (Exception e) {
            log.error("[UpdateSoulTool] Failed: {}", e.getMessage());
            return "ERROR: Failed to update agent soul: " + e.getMessage();
        }
    }
}
