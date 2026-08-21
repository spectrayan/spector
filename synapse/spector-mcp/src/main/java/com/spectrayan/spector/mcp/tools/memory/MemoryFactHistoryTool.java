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
import com.spectrayan.spector.mcp.schema.ToolSchemaBuilder;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.FactHistory;
import com.spectrayan.spector.memory.model.FactHistory.FactSnapshot;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_fact_history} — retrieves bitemporal transition timelines and supersession chains.
 */
public final class MemoryFactHistoryTool extends MemoryToolHandler {

    public MemoryFactHistoryTool(SpectorMemory memory) {
        super(memory);
    }

    public MemoryFactHistoryTool(Supplier<SpectorMemory> memoryResolver) {
        super(memoryResolver);
    }

    @Override
    public String name() {
        return "memory_fact_history";
    }

    @Override
    public Set<String> requiredScopes() {
        return Set.of(SpectorScopes.MEMORY_READ);
    }

    @Override
    public String description() {
        return "Retrieve the complete chronological evolution, supersession chain, and "
                + "bitemporal validity intervals for an entity relationship. Enables "
                + "multi-evidence timeline exploration and historical auditability.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ToolSchemaBuilder.object()
                .requiredString("subject", "Subject entity name (e.g. 'Alice', 'career_goal').")
                .optionalString("predicate", "Relationship type or attribute name (e.g. 'works_at', 'role'). Default '*'.", "*")
                .build();
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                     Map<String, Object> args) throws Exception {
        String subject = requireString(args, "subject");
        String predicate = optionalString(args, "predicate", "*");

        var sb = new StringBuilder();
        sb.append("**Bitemporal Fact Evolution:** `").append(subject).append("` [predicate: `")
          .append(predicate).append("`]\n\n");

        FactHistory history = memory.factHistory(subject, predicate);
        if (history == null || (history.activeFact() == null && history.supersededFacts().isEmpty())) {
            return textResult("No historical evolution found for subject '" + subject
                    + "' with predicate '" + predicate + "'.");
        }

        if (history.activeFact() != null) {
            FactSnapshot active = history.activeFact();
            sb.append("### Active Consensus Fact\n");
            sb.append("- **Object:** `").append(active.object()).append("`\n");
            sb.append("- **Fact ID:** #").append(active.factId()).append("\n");
            sb.append("- **Confidence:** ").append(String.format("%.2f", active.confidence())).append("\n");
            sb.append("- **Valid From (epoch s):** ").append(active.validFrom()).append("\n");
            sb.append("- **Valid To (epoch s):** ").append(active.validTo() == Long.MAX_VALUE ? "open-ended" : active.validTo()).append("\n");
            sb.append("- **Transaction Time (epoch ms):** ").append(active.txTime()).append("\n\n");
        } else {
            sb.append("### No Active Consensus (All Versions Superseded / Retracted)\n\n");
        }

        if (!history.supersededFacts().isEmpty()) {
            sb.append("### Historical Superseded Chain (").append(history.supersededFacts().size()).append(" versions)\n");
            for (int i = 0; i < history.supersededFacts().size(); i++) {
                FactSnapshot s = history.supersededFacts().get(i);
                sb.append(i + 1).append(". **Object:** `").append(s.object()).append("` [Fact #").append(s.factId()).append("]\n");
                sb.append("   - Confidence: ").append(String.format("%.2f", s.confidence())).append("\n");
                sb.append("   - Valid Interval: [").append(s.validFrom()).append(" -> ")
                  .append(s.validTo() == Long.MAX_VALUE ? "open-ended" : s.validTo()).append("]\n");
                sb.append("   - Superseded By Fact: #").append(s.supersededByFactId()).append("\n");
            }
        } else {
            sb.append("_No prior historical versions._\n");
        }

        return textResult(sb.toString());
    }
}
