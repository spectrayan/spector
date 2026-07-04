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

import java.util.List;
import java.util.Map;

/**
 * Agent tool that reads the current agent soul identity.
 *
 * <h3>When the LLM should call this</h3>
 * <ul>
 *   <li>"Who are you?"</li>
 *   <li>"What are your capabilities?"</li>
 *   <li>Before proposing identity updates (to check current state)</li>
 * </ul>
 */
@Component
public class ReadIdentityTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ReadIdentityTool.class);

    private final AgentSoulRepository soulRepository;

    public ReadIdentityTool(AgentSoulRepository soulRepository) {
        this.soulRepository = soulRepository;
    }

    @Override public String name() { return "read_identity"; }

    @Override public String description() {
        return "Reads the current agent soul identity. Use when the user asks 'who are you?' "
                + "or before proposing identity updates.";
    }

    @Override public ToolCategory category() { return ToolCategory.MEMORY; }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target", Map.of("type", "string",
                                "description", "'agent' to read agent soul identity")
                ),
                "required", List.of("target")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        try {
            AgentSoul soul = soulRepository.loadDefault();
            var sb = new StringBuilder("Agent Identity:\n");
            if (soul.name() != null) sb.append("  Name: ").append(soul.name()).append('\n');
            if (soul.description() != null) sb.append("  Description: ").append(soul.description()).append('\n');
            if (soul.model() != null) sb.append("  Preferred Model: ").append(soul.model()).append('\n');
            if (soul.personality() != null && !soul.personality().isEmpty()) {
                sb.append("  Personality:\n");
                soul.personality().forEach((k, v) -> sb.append("    ").append(k).append(": ").append(v).append('\n'));
            }
            if (soul.tools() != null && !soul.tools().isEmpty()) {
                sb.append("  Available Tools: ").append(String.join(", ", soul.tools())).append('\n');
            }
            if (soul.systemPrompt() != null) {
                String prompt = soul.systemPrompt();
                sb.append("  System Prompt: ").append(prompt.length() > 200
                        ? prompt.substring(0, 200) + "..." : prompt).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[ReadIdentityTool] Failed to read identity: {}", e.getMessage());
            return "Error reading agent identity: " + e.getMessage();
        }
    }
}
