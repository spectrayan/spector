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
 * MCP tool: {@code memory_consolidate} — trigger a manual memory consolidation cycle.
 *
 * <p>Consolidation is one of the four <b>required</b> MF-001 algebra operations
 * (remember, recall, consolidate, forget). It lifts episodic traces into semantic
 * or procedural tiers with lineage, compacts tombstoned records, and updates
 * decay strengths.</p>
 *
 * <p>This is a batch operation that may take several seconds depending on the
 * number of traces eligible for consolidation. The tool returns a summary
 * of the work performed.</p>
 *
 * @see com.spectrayan.spector.memory.SpectorMemory#consolidate()
 */
public final class MemoryConsolidateTool extends MemoryToolHandler {

    public static final String NAME = "memory_consolidate";

    public MemoryConsolidateTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryConsolidateTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        int beforeCount = memory.totalMemories();
        memory.consolidate();
        int afterCount = memory.totalMemories();

        var sb = new StringBuilder();
        sb.append("🧠 Consolidation cycle completed.\n");
        sb.append("  Memories before: ").append(beforeCount).append('\n');
        sb.append("  Memories after:  ").append(afterCount).append('\n');

        int delta = afterCount - beforeCount;
        if (delta > 0) {
            sb.append("  New distilled traces: +").append(delta).append('\n');
        } else if (delta < 0) {
            sb.append("  Traces compacted: ").append(Math.abs(delta)).append('\n');
        } else {
            sb.append("  No trace count change (decay/strength updates may still have occurred).\n");
        }

        return textResult(sb.toString());
    }
}
