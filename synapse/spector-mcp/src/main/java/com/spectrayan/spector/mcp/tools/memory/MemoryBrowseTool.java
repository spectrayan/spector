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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_browse} — browse memories by tag.
 *
 * <p>A fast, deterministic, non-vector search tool that returns all memories
 * that match the given tags (AND semantics). No embedding or similarity scoring
 * is involved — this is pure tag-based filtering.</p>
 *
 * <p>Maps to {@link SpectorMemory#browse(String...)}.</p>
 */
public final class MemoryBrowseTool extends MemoryToolHandler {

    public static final String NAME = "memory_browse";

    public MemoryBrowseTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryBrowseTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String[] filterTags = optionalTags(args, "tags");
        if (filterTags.length == 0) {
            return errorResult("At least one tag is required for browsing.");
        }

        List<CognitiveRecord> results = memory.browse(filterTags);

        if (results.isEmpty()) {
            return textResult("📭 No memories found matching tags: ["
                    + String.join(", ", filterTags) + "]");
        }

        record BrowseEntry(int index, CognitiveRecord record, boolean hasTags) {}

        List<BrowseEntry> entries = new java.util.ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            CognitiveRecord r = results.get(i);
            entries.add(new BrowseEntry(i + 1, r, r.tags() != null && r.tags().length > 0));
        }

        var model = Map.of(
                "totalResults", results.size(),
                "filterTags", List.of(filterTags),
                "results", entries
        );

        return textResult(McpTemplateEngine.render("memory-browse", model));
    }
}
