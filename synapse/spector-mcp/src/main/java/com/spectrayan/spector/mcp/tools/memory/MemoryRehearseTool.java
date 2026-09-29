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
import java.util.function.Supplier;

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_rehearse} — pure strength rehearsal without valence change.
 *
 * <p>Updates storage strength S via ΔS = s_gain(1-D) and retrieval strength D,
 * making the memory more resistant to decay and easier to recall. Unlike
 * {@link MemoryReinforceTool}, rehearsal does not shift the emotional valence.</p>
 *
 * <p>This implements the MF-001 §5 {@code rehearse} operation: explicit strength
 * update without returning content.</p>
 *
 * @see com.spectrayan.spector.memory.SpectorMemory#rehearse(String)
 */
public final class MemoryRehearseTool extends MemoryToolHandler {

    public static final String NAME = "memory_rehearse";

    public MemoryRehearseTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryRehearseTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String memoryId = requireString(args, "memory_id");

        memory.rehearse(memoryId);

        Map<String, Object> model = Map.of(
                "memoryId", memoryId,
                "found", true
        );
        return textResult(McpTemplateEngine.render("memory-rehearse", model));
    }
}
