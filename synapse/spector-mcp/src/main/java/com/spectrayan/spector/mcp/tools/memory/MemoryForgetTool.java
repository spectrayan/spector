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
import com.spectrayan.spector.commons.security.SpectorScopes;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_forget} — explicitly forget a memory by ID.
 */
public final class MemoryForgetTool extends MemoryToolHandler {

    public static final String NAME = "memory_forget";

    public MemoryForgetTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryForgetTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String memoryId = requireString(args, "memory_id");
        var result = memory.forgetWithResult(memoryId);
        if (!result.found()) {
            // Previously reported "has been forgotten (tombstoned)" regardless, so a typo'd or
            // already-deleted id produced a confident false confirmation on a deletion path (#983).
            return textResult("⚠️ No memory found with id '" + memoryId
                    + "'. Nothing was forgotten.");
        }
        // "tombstoned" is stated explicitly because the payload bytes remain on disk and in any snapshot
        // taken since; this is not erasure. An agent relaying a deletion confirmation to a user needs to be
        // relaying that distinction too, which is why memory_purge is named here rather than left to be
        // discovered.
        return textResult("🗑️ Memory '" + memoryId + "' has been forgotten (tombstoned — hidden from "
                + "recall, but its content bytes remain on disk and in backups). Use memory_purge if the "
                + "data itself must be destroyed.");
    }
}
