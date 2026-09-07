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
package com.spectrayan.spector.mcp.tools.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.aisme.enactment.EnactmentEngine;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MCP tool: {@code persona_enact} — executes Dual-Process Persona Enactment over SpectorMemory (ADR-0032).
 */
public final class PersonaEnactTool extends MemoryToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static final String NAME = "persona_enact";

    public PersonaEnactTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public PersonaEnactTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        String problem = requireString(args, "problem");
        String modeStr = optionalString(args, "mode", "REACT");
        String actingSoulId = optionalString(args, "acting_soul_id", "default");

        EnactMode mode = EnactMode.REACT;
        try {
            mode = EnactMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        SituationFrame situation = SituationFrame.of(problem);

        AgentSoul soul = null;
        if (memory != null && memory.aismeBundle() != null && memory.aismeBundle().agentSoul() != null) {
            soul = memory.aismeBundle().agentSoul();
        }
        if (soul == null) {
            soul = AgentSoul.builder()
                    .id(actingSoulId)
                    .name(actingSoulId)
                    .purpose("Persona Enactment for " + actingSoulId)
                    .build();
        }

        Enactment enactment = EnactmentEngine.enact(memory, soul, situation, mode);

        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(enactment);
        return textResult(json);
    }
}
