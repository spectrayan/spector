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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_reflect} — trigger a sleep consolidation (reflection) cycle.
 *
 * <p>Reflection runs the full 14-relay cognitive pathway (episodic log consolidation,
 * soul drift re-fusion, Hebbian homeostasis, synaptic/temporal pruning, cross-layer
 * promotion, procedural crystallization, entity maintenance, idiolect learning, and
 * WAL journaling). Returns a detailed {@link ReflectReport} summary.</p>
 *
 * <p>This is the MCP counterpart of the REST {@code POST /api/v1/memory/reflect}
 * endpoint and the CLI {@code spector memory reflect} command.</p>
 *
 * @see com.spectrayan.spector.memory.SpectorMemory#reflect()
 * @see com.spectrayan.spector.memory.pathway.reflect.ReflectPathway
 */
public final class MemoryReflectTool extends MemoryToolHandler {

    public static final String NAME = "memory_reflect";

    public MemoryReflectTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryReflectTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        int sessionLimit = optionalInt(args, "session_limit", 0);
        boolean consolidationOnly = optionalBoolean(args, "consolidation_only", false);

        ReflectReport report = executeReflect(memory, sessionLimit, consolidationOnly);

        Map<String, Object> model = buildTemplateModel(report);
        return textResult(McpTemplateEngine.render("memory-reflect", model));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal Helpers
    // ═══════════════════════════════════════════════════════════════

    private static ReflectReport executeReflect(SpectorMemory memory,
                                                 int sessionLimit,
                                                 boolean consolidationOnly) {
        if (consolidationOnly) {
            return memory.reflect(ReflectSweepSpec.consolidationOnly(sessionLimit));
        }
        if (sessionLimit > 0) {
            ReflectSweepSpec spec = ReflectSweepSpec.builder()
                    .sessionLimit(sessionLimit)
                    .runCompanionRelays(true)
                    .build();
            return memory.reflect(spec);
        }
        return memory.reflect();
    }

    static Map<String, Object> buildTemplateModel(ReflectReport report) {
        Map<String, Object> model = new LinkedHashMap<>();

        model.put("durationMs", report.duration().toMillis());
        model.put("consolidatedCount", report.consolidatedCount());
        model.put("logTurnsConsolidated", report.logTurnsConsolidated());
        model.put("tombstonedCount", report.tombstonedCount());
        model.put("compactedPartitions", report.compactedPartitions());
        model.put("temporalPrunedCount", report.temporalPrunedCount());
        model.put("hadActivity", report.hadActivity());

        // Soul drift section (conditional)
        boolean hasSoulDrift = report.soulDriftedCount() > 0 || report.soulRefusedCount() > 0;
        model.put("hasSoulDrift", hasSoulDrift);
        model.put("soulDriftedCount", report.soulDriftedCount());
        model.put("soulRefusedCount", report.soulRefusedCount());
        model.put("averageImportanceDelta", report.averageImportanceDelta());

        // Graph health section (conditional) — access via toString() to respect
        // ArchUnit module boundary: MCP must not import memory.graph.* internals.
        Object gh = report.graphHealth();
        boolean hasGraphHealth = gh != null;
        model.put("hasGraphHealth", hasGraphHealth);
        if (hasGraphHealth) {
            model.put("graphHealthSummary", gh.toString());
        }

        return model;
    }
}
