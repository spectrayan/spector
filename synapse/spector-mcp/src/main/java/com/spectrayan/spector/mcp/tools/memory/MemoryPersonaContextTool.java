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

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.security.SpectorScopes;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_persona_context} — inspects persona profile (Idiolect, Vocal Prosody, Kinesics).
 */
public final class MemoryPersonaContextTool extends MemoryToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "memory_persona_context";

    public MemoryPersonaContextTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryPersonaContextTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        Map<String, Object> result = Map.of(
                "status", "ACTIVE",
                "totalMemories", memory.totalMemories()
        );
        return textResult(MAPPER.writeValueAsString(result));
    }
}
