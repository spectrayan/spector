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

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_status} — memory stats per tier.
 */
public final class MemoryStatusTool extends MemoryToolHandler {

    private static final TemplateEngine templateEngine = TemplateEngine.createDefault();

    public MemoryStatusTool(SpectorMemory memory) {
        super(memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryStatusTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override public String name() { return "memory_status"; }

    @Override public Set<String> requiredScopes() { return Set.of(SpectorScopes.MEMORY_READ); }

    @Override
    public String description() {
        return "View memory system statistics: total memories, per-tier counts, "
                + "WAL event count, suppression set size, and pending reminders.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object().build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) {
        LateralEvaluator lateral = memory.admin().lateralEvaluator();
        LateralEvaluator.LateralMetrics metrics = lateral.metrics();

        var model = Map.<String, Object>ofEntries(
                Map.entry("totalMemories", memory.totalMemories()),
                Map.entry("workingCount", memory.memoryCount(MemoryType.WORKING)),
                Map.entry("episodicCount", memory.memoryCount(MemoryType.EPISODIC)),
                Map.entry("semanticCount", memory.memoryCount(MemoryType.SEMANTIC)),
                Map.entry("proceduralCount", memory.memoryCount(MemoryType.PROCEDURAL)),
                Map.entry("walSize", memory.admin().wal().size()),
                Map.entry("walHighWaterMark", memory.admin().wal().highWaterMark()),
                Map.entry("suppressedCount", memory.admin().suppression().size()),
                Map.entry("pendingReminders", memory.admin().prospective().pendingCount()),
                Map.entry("lateralEnabled", lateral.isLateralEnabled()),
                Map.entry("lateralThreshold", lateral.currentDistanceThreshold()),
                Map.entry("lateralSampleSize", metrics.sampleSize()),
                Map.entry("lateralUtilityRate", metrics.utilityRate()),
                Map.entry("lateralSuppressionRate", metrics.suppressionRate()),
                Map.entry("lateralHallucinationIndex", metrics.hallucinationIndex())
        );

        return textResult(templateEngine.render("mcp/memory-status", model));
    }
}
