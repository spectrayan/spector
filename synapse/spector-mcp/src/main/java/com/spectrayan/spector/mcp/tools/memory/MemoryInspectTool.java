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
import com.spectrayan.spector.commons.template.TemplateEngine;

import io.modelcontextprotocol.spec.McpSchema;

import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;

/**
 * MCP tool: {@code memory_inspect} — full cognitive X-ray of a single memory.
 *
 * <p>Returns the complete cognitive snapshot: text, header fields (importance,
 * valence, arousal, recall counts, synaptic tags, flags), and the quantized
 * vector. This is the "microscope" for debugging and auditing agent memories.</p>
 *
 * <p>Maps to {@link SpectorMemory#inspect(String)}.</p>
 */
public final class MemoryInspectTool extends MemoryToolHandler {

    private static final TemplateEngine templateEngine = TemplateEngine.createDefault();

    public MemoryInspectTool(SpectorMemory memory) {
        super(memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryInspectTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override public String name() { return "memory_inspect"; }

    @Override public Set<String> requiredScopes() { return Set.of(SpectorScopes.MEMORY_READ); }

    @Override
    public String description() {
        return "Inspect a single memory by ID — returns the full cognitive X-ray: "
                + "text content, cognitive header (importance, valence, arousal, recall counts, "
                + "synaptic tags bloom filter, storage strength, flags), vector dimensions, "
                + "physical location, and flag states (tombstoned, consolidated, pinned, resolved). "
                + "Use this to debug why a memory scores high or low, verify ingestion, "
                + "or understand the full internal state of a specific memory.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                .requiredString("id", "The memory ID to inspect.")
                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String id = requireString(args, "id");
        CognitiveRecord record = memory.inspect(id);

        if (record == null) {
            return errorResult("Memory '" + id + "' not found in the index.");
        }

        String valenceLabel = record.valence() > 0 ? "(positive)" : record.valence() < 0 ? "(negative)" : "(neutral)";

        var model = Map.<String, Object>ofEntries(
                Map.entry("id", id),
                Map.entry("record", record),
                Map.entry("hasTags", record.tags() != null && record.tags().length > 0),
                Map.entry("valenceLabel", valenceLabel),
                Map.entry("arousalUnsigned", Byte.toUnsignedInt(record.arousal())),
                Map.entry("synapticTagsHex", Long.toHexString(record.synapticTags())),
                Map.entry("vectorDimensions", record.hasVector() ? record.quantizedVector().length : 0),
                Map.entry("rawJson", record.toJson())
        );

        return textResult(templateEngine.render("mcp/memory-inspect", model));
    }
}
