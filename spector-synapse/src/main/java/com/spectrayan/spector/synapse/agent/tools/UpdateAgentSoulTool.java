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
import com.spectrayan.spector.synapse.agent.AgentTool;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
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
 *
 * <h3>Enterprise Guardrails</h3>
 * <p>Ethical guardrails are immutable — the agent cannot modify them.</p>
 */
@Component
public class UpdateAgentSoulTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(UpdateAgentSoulTool.class);

    /** Fields the agent CAN modify (with user approval). */
    private static final Set<String> MUTABLE_FIELDS = Set.of(
            "name", "description", "system_prompt", "model", "personality",
            "purpose", "communication_style", "expertise_domain", "core_value");

    private final CognitiveSoulService soulService;

    public UpdateAgentSoulTool(CognitiveSoulService soulService) {
        this.soulService = soulService;
    }

    @Override public String name() { return "update_agent_soul"; }

    @Override public String description() {
        return "Proposes a modification to the agent's own identity (soul). "
                + "Call when the user gives feedback about behavior, expertise, or style. "
                + "Requires user confirmation. Ethical guardrails are immutable. "
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
                        "action", Map.of("type", "string",
                                "description", "'add' (default) or 'remove' — for list fields like expertise_domain, core_value")
                ),
                "required", List.of("field", "value")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String field = String.valueOf(arguments.getOrDefault("field", "")).trim().toLowerCase();
        String value = String.valueOf(arguments.getOrDefault("value", "")).trim();
        String action = String.valueOf(arguments.getOrDefault("action", "add")).trim().toLowerCase();

        if (field.isBlank()) return "ERROR: 'field' parameter is required";
        if (value.isBlank()) return "ERROR: 'value' parameter is required";

        // Reject ethical guardrail modifications
        if (field.contains("guardrail") || field.contains("ethic")) {
            return "ERROR: Ethical guardrails are immutable and cannot be modified by the agent";
        }

        if (!MUTABLE_FIELDS.contains(field)) {
            return "ERROR: Invalid field '" + field + "'. Mutable fields: "
                    + String.join(", ", MUTABLE_FIELDS);
        }

        try {
            AgentSoul current = soulService.getEffectiveSoul(null);

            var builder = AgentSoul.builder()
                    .id(current.id())
                    .name(current.name())
                    .description(current.description())
                    .systemPrompt(current.systemPrompt())
                    .purpose(current.purpose())
                    .personality(current.personality())
                    .expertiseDomains(current.expertiseDomains())
                    .coreValues(current.coreValues())
                    .ethicalGuardrails(current.ethicalGuardrails())
                    .emotionalBaseline(current.emotionalBaseline())
                    .communicationStyle(current.communicationStyle())
                    .model(current.model())
                    .tools(current.tools())
                    .createdAt(current.createdAt())
                    .updatedAt(Instant.now());

            switch (field) {
                case "name" -> builder.name(value);
                case "description" -> builder.description(value);
                case "system_prompt" -> builder.systemPrompt(value);
                case "model" -> builder.model(value);
                case "personality" -> builder.personality(value);
                case "purpose" -> builder.purpose(value);
                case "communication_style" -> builder.communicationStyle(value);
                case "expertise_domain" -> {
                    var domains = new ArrayList<>(current.expertiseDomains());
                    if ("remove".equals(action)) domains.remove(value);
                    else if (!domains.contains(value)) domains.add(value);
                    builder.expertiseDomains(domains);
                }
                case "core_value" -> {
                    var values = new ArrayList<>(current.coreValues());
                    if ("remove".equals(action)) values.remove(value);
                    else if (!values.contains(value)) values.add(value);
                    builder.coreValues(values);
                }
                default -> throw new IllegalArgumentException("Unknown field: " + field);
            }

            soulService.saveAgentSoul(builder.build());
            log.info("[UpdateSoulTool] Updated {} = '{}'", field, value);
            return "Updated agent soul: " + field + " = '" + value + "'";

        } catch (Exception e) {
            log.error("[UpdateSoulTool] Failed: {}", e.getMessage());
            return "ERROR: Failed to update agent soul: " + e.getMessage();
        }
    }
}
