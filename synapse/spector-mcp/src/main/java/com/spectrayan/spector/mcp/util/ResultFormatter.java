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
package com.spectrayan.spector.mcp.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;

/**
 * Shared formatting utilities for MCP tool and resource responses.
 */
public final class ResultFormatter {

    private static final TemplateEngine templateEngine = TemplateEngine.createDefault();

    private ResultFormatter() {} // static utility

    // ═══════════════════════════════════════════════════════════════
    //  Memory Results
    // ═══════════════════════════════════════════════════════════════

    /**
     * Formats memory results for LLM consumption with score and truncated content.
     */
    public static String formatMemoryResults(List<CognitiveResult> results) {
        if (results == null || results.isEmpty()) {
            return "No results found.";
        }

        var model = Map.of(
                "count", results.size(),
                "results", results
        );

        return templateEngine.render("mcp/cognitive-results", model);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Memory Status Map
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds a structured map of memory status fields.
     */
    public static Map<String, Object> buildMemoryStatusMap(SpectorMemory memory, String version) {
        var status = new LinkedHashMap<String, Object>(12);
        status.put("engine", "spector-memory");
        status.put("version", version);
        if (memory != null) {
            status.put("totalMemories", memory.totalMemories());
            status.put("workingCount", memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.WORKING));
            status.put("episodicCount", memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.EPISODIC));
            status.put("semanticCount", memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.SEMANTIC));
            status.put("proceduralCount", memory.memoryCount(com.spectrayan.spector.memory.model.MemoryType.PROCEDURAL));
            status.put("walSize", memory.admin().wal().size());
            status.put("suppressedCount", memory.admin().suppression().size());
            status.put("pendingReminders", memory.admin().prospective().pendingCount());
        }
        status.put("simd", SimdCapability.report());
        return status;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Timing Footer
    // ═══════════════════════════════════════════════════════════════

    /**
     * Appends a timing footer to a result string.
     */
    public static String withTimingFooter(String text, String label, long elapsedMs) {
        return text + "\n[" + label + " completed in " + elapsedMs + "ms]";
    }
}
