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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveRecord;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: {@code memory_list} — list memories with safe cursor pagination, time bounds, and tier filtering without scoring.
 *
 * <p>Conforms to Milestone 5 (Requirement R5 / F18) and Interface Contract 5.</p>
 */
public final class MemoryListTool extends MemoryToolHandler {

    public static final String NAME = "memory_list";

    public MemoryListTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    public MemoryListTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return (spec != null && spec.description() != null)
                ? spec.description()
                : "List memories with safe cursor pagination, time bounds, and tier filtering without scoring.";
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.MEMORY;
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
        int limit = 50;
        if (args != null && args.containsKey("limit")) {
            Object limitObj = args.get("limit");
            if (limitObj instanceof Number num) {
                limit = Math.min(Math.max(1, num.intValue()), 500);
            }
        }

        String tierFilter = args != null ? (String) args.get("tier") : null;
        String sourceFilter = args != null ? (String) args.get("source") : null;
        Long createdFrom = (args != null && args.containsKey("created_from"))
                ? ((Number) args.get("created_from")).longValue() : null;
        Long createdTo = (args != null && args.containsKey("created_to"))
                ? ((Number) args.get("created_to")).longValue() : null;
        String cursor = args != null ? (String) args.get("cursor") : null;

        List<CognitiveRecord> all = memory.admin().listAll();
        if (all == null || all.isEmpty()) {
            return textResult("📭 No memories found matching criteria.");
        }

        // Filter by tier, source, created_from, and created_to
        List<CognitiveRecord> filtered = all.stream()
                .filter(r -> tierFilter == null || r.memoryType().name().equalsIgnoreCase(tierFilter))
                .filter(r -> sourceFilter == null || (r.source() != null && r.source().name().equalsIgnoreCase(sourceFilter)))
                .filter(r -> createdFrom == null || r.timestampMs() >= createdFrom)
                .filter(r -> createdTo == null || r.timestampMs() <= createdTo)
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.timestampMs(), a.timestampMs()); // newest first
                    if (cmp != 0) return cmp;
                    return b.id().compareTo(a.id()); // tiebreak by ID descending
                })
                .toList();

        if (filtered.isEmpty()) {
            return textResult("📭 No memories found matching criteria.");
        }

        // Seek after cursor if present
        int startIdx = 0;
        if (cursor != null && !cursor.isBlank()) {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Malformed cursor token: missing timestamp separator");
            }
            long curTs = Long.parseLong(raw.substring(0, colon));
            String curId = raw.substring(colon + 1);

            for (int i = 0; i < filtered.size(); i++) {
                CognitiveRecord r = filtered.get(i);
                boolean isAfter = (r.timestampMs() < curTs) || (r.timestampMs() == curTs && r.id().compareTo(curId) < 0);
                if (isAfter) {
                    startIdx = i;
                    break;
                }
                if (i == filtered.size() - 1) {
                    startIdx = filtered.size();
                }
            }
        }

        int endIdx = Math.min(startIdx + limit, filtered.size());
        List<CognitiveRecord> page = filtered.subList(startIdx, endIdx);

        String nextCursor = null;
        if (endIdx < filtered.size() && !page.isEmpty()) {
            CognitiveRecord last = page.get(page.size() - 1);
            String raw = last.timestampMs() + ":" + last.id();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Memory List (").append(page.size()).append(" of ").append(filtered.size()).append(")\n\n");
        sb.append("| ID | Tier | Source | Timestamp | Text |\n");
        sb.append("|:---|:---|:---|:---|:---|\n");
        for (CognitiveRecord r : page) {
            sb.append("| `").append(r.id()).append("` ")
                    .append("| ").append(r.memoryType()).append(" ")
                    .append("| ").append(r.source()).append(" ")
                    .append("| ").append(r.timestampMs()).append(" ")
                    .append("| ").append(r.text()).append(" |\n");
        }
        if (nextCursor != null) {
            sb.append("\n**Next Cursor:** `").append(nextCursor).append("`\n");
        }

        return textResult(sb.toString());
    }
}
