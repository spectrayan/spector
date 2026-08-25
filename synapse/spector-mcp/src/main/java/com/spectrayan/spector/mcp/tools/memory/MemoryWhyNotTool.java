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
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.WhyNotExplanation;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_why_not} — explains recall misses.
 *
 * <p>When a developer or LLM expects a specific memory to be returned by a recall
 * query but it isn't, this tool diagnoses the exact reason: not found, tombstoned,
 * suppressed, outranked, or pre-filtered.</p>
 *
 * <p>Always runs in OBSERVE mode — diagnosing a miss never mutates memory state.</p>
 *
 * @see WhyNotExplanation
 */
public final class MemoryWhyNotTool extends MemoryToolHandler {

    public static final String NAME = "memory_why_not";

    public MemoryWhyNotTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryWhyNotTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String memoryId = requireString(args, "memory_id");
        String query = requireString(args, "query");
        int topK = optionalInt(args, "top_k", 5);

        RecallOptions options = RecallOptions.builder().topK(topK).build();
        WhyNotExplanation explanation = memory.whyNot(memoryId, query, options);

        var model = Map.<String, Object>ofEntries(
                Map.entry("memoryId", memoryId),
                Map.entry("query", query),
                Map.entry("reason", explanation.reason()),
                Map.entry("exists", explanation.exists()),
                Map.entry("suppressed", explanation.suppressed()),
                Map.entry("hasScoreGap", explanation.scoreGap() > 0f),
                Map.entry("scoreGap", explanation.scoreGap()),
                Map.entry("breakdown", explanation.breakdown() != null ? explanation.breakdown() : ""),
                Map.entry("summary", explanation.summary() != null ? explanation.summary() : "")
        );

        return textResult(McpTemplateEngine.render("memory-why-not", model));
    }
}
