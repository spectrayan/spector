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

import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_export} — export all memories as JSON.
 *
 * <p>Exports all live (non-tombstoned) memories as a JSON array.
 * Each memory includes its full cognitive profile: text, header fields,
 * tags, source, and physical location metadata.</p>
 *
 * <p>Use for backup, migration, audit, and debugging.
 * For large stores, consider using {@code memory_browse} with tag filters first.</p>
 *
 * <p>Maps to {@link SpectorMemory#exportJson()}.</p>
 */
public final class MemoryExportTool extends MemoryToolHandler {

    public static final String NAME = "memory_export";

    public MemoryExportTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryExportTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        int totalCount = memory.totalMemories();
        if (totalCount == 0) {
            return textResult("📭 No memories to export. The memory store is empty.");
        }

        String json = memory.exportJson();

        StringBuilder sb = new StringBuilder();
        sb.append("📦 Exported ").append(totalCount).append(" memories\n\n");
        sb.append(json);

        return textResult(sb.toString());
    }
}
