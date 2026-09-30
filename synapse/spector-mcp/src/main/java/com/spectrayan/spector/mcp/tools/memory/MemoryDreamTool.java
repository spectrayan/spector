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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.mcp.util.McpTemplateEngine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamReport;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_dream} — trigger a generative replay (dream) cycle.
 *
 * <p>Executes a generative replay cycle across episodic and semantic memory to
 * consolidate engrams, construct synthetic scenarios, synthesize novel insights,
 * and inhibit inconsistent counter-associations.</p>
 *
 * <p>Supports three dream modes with varying constraint relaxation:
 * <ul>
 *   <li>{@link DreamMode#REM} (default): Weak constraint relaxation (temp 2.0) for broad associative replay</li>
 *   <li>{@link DreamMode#DAYDREAM}: Moderate constraint relaxation (temp 1.0) for exploratory daytime associations</li>
 *   <li>{@link DreamMode#THOUGHT_EXPERIMENT}: Tight constraint relaxation (temp 0.5) for counterfactual hypothesis testing</li>
 * </ul>
 * </p>
 *
 * @see com.spectrayan.spector.memory.SpectorMemory#dream(DreamMode)
 */
public final class MemoryDreamTool extends MemoryToolHandler {

    public static final String NAME = "memory_dream";

    public MemoryDreamTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryDreamTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        String modeStr = optionalString(args, "mode", "REM");
        DreamMode mode = parseMode(modeStr);

        DreamReport report = memory.dream(mode);
        if (report == null) {
            report = DreamReport.empty();
        }

        Map<String, Object> model = buildTemplateModel(report, mode);
        return textResult(McpTemplateEngine.render("memory-dream", model));
    }

    private static DreamMode parseMode(String modeStr) {
        if (modeStr == null || modeStr.isBlank()) {
            return DreamMode.REM;
        }
        try {
            return DreamMode.valueOf(modeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ToolArgumentException("Invalid dream mode: '" + modeStr + "'. Expected one of: "
                    + Arrays.toString(DreamMode.values()));
        }
    }

    static Map<String, Object> buildTemplateModel(DreamReport report, DreamMode requestedMode) {
        Map<String, Object> model = new LinkedHashMap<>();
        DreamMode effectiveMode = report.mode() != null ? report.mode() : requestedMode;
        model.put("mode", effectiveMode != null ? effectiveMode.name() : DreamMode.REM.name());
        model.put("durationMs", report.elapsed() != null ? report.elapsed().toMillis() : 0L);
        String outcomeStr = "COMPLETED";
        if (report.outcome() != null && report.outcome().finish() != null) {
            outcomeStr = report.outcome().finish().name();
        }
        model.put("outcome", outcomeStr);
        model.put("seedsSampled", report.seedsSampled());
        model.put("scenesConstructed", report.scenesConstructed());
        model.put("scenesTriaged", report.scenesTriaged());
        model.put("insightsIngested", report.insightsIngested());
        model.put("journalEntriesWritten", report.journalEntriesWritten());
        model.put("failedPairsInhibited", report.failedPairsInhibited());

        boolean hadActivity = report.seedsSampled() > 0
                || report.scenesConstructed() > 0
                || report.insightsIngested() > 0
                || report.journalEntriesWritten() > 0;
        model.put("hadActivity", hadActivity);

        return model;
    }
}
