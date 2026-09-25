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
 * MCP tool: {@code memory_export} — export memories as JSON with optional scoping filters.
 *
 * <p>Exports memories as a JSON array. Each memory includes its full cognitive profile:
 * text, header fields, tags, source, and physical location metadata.</p>
 *
 * <p>Supports scoping by {@code tier}, {@code source}, {@code created_from}, {@code created_to},
 * {@code limit}, and {@code cursor}. If no scoping arguments are provided, exports all
 * active memories via full export.</p>
 *
 * <p>Maps to {@link SpectorMemory#exportJson()}.</p>
 */
public final class MemoryExportTool extends MemoryToolHandler {

    public static final String NAME = "memory_export";

    public MemoryExportTool(SpectorMemory memory) {
        super(NAME, memory);
    }

    /** Enterprise constructor: resolves memory per-request for tenant isolation. */
    public MemoryExportTool(Supplier<SpectorMemory> memoryResolver) {
        super(NAME, memoryResolver);
    }

    @Override
    protected McpSchema.CallToolResult executeMemory(SpectorMemory memory,
                                                       Map<String, Object> args) throws Exception {
        boolean hasFilters = args != null && (
                args.containsKey("tier")
                || args.containsKey("source")
                || args.containsKey("created_from")
                || args.containsKey("created_to")
                || args.containsKey("limit")
                || args.containsKey("cursor")
        );

        if (!hasFilters) {
            int totalCount = memory.totalMemories();
            if (totalCount == 0) {
                return textResult("📭 No memories to export. The memory store is empty.");
            }

            String json = memory.exportJson();

            StringBuilder sb = new StringBuilder();
            sb.append("📦 Exported ").append(totalCount).append(" memories\n\n");
            sb.append(json);

            return textResult(sb.toString());
        }

        String tierFilter = (String) args.get("tier");
        String sourceFilter = (String) args.get("source");
        Long createdFrom = args.containsKey("created_from")
                ? ((Number) args.get("created_from")).longValue() : null;
        Long createdTo = args.containsKey("created_to")
                ? ((Number) args.get("created_to")).longValue() : null;
        String cursor = (String) args.get("cursor");
        Integer limit = args.containsKey("limit")
                ? Math.max(1, ((Number) args.get("limit")).intValue()) : null;

        List<CognitiveRecord> all = memory.admin().listAll();
        if (all == null || all.isEmpty()) {
            return textResult("📭 No memories to export. The memory store is empty.");
        }

        List<CognitiveRecord> filtered = all.stream()
                .filter(r -> !r.isPurged() && !r.isTombstoned())
                .filter(r -> tierFilter == null || r.memoryType().name().equalsIgnoreCase(tierFilter))
                .filter(r -> sourceFilter == null || (r.source() != null && r.source().name().equalsIgnoreCase(sourceFilter)))
                .filter(r -> createdFrom == null || r.timestampMs() >= createdFrom)
                .filter(r -> createdTo == null || r.timestampMs() <= createdTo)
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.timestampMs(), a.timestampMs());
                    if (cmp != 0) return cmp;
                    return b.id().compareTo(a.id());
                })
                .toList();

        if (filtered.isEmpty()) {
            return textResult("📭 No memories found matching export criteria.");
        }

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

        int endIdx = limit != null ? Math.min(startIdx + limit, filtered.size()) : filtered.size();
        List<CognitiveRecord> page = filtered.subList(startIdx, endIdx);

        String nextCursor = null;
        if (endIdx < filtered.size() && !page.isEmpty()) {
            CognitiveRecord last = page.get(page.size() - 1);
            String raw = last.timestampMs() + ":" + last.id();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        var mapper = new tools.jackson.databind.ObjectMapper();
        var arrayNode = mapper.createArrayNode();
        for (CognitiveRecord r : page) {
            arrayNode.add(mapper.readTree(r.toJson()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📦 Exported ").append(page.size()).append(" memories (scoped)\n\n");
        sb.append(arrayNode.toString());
        if (nextCursor != null) {
            sb.append("\n\n**Next Cursor:** `").append(nextCursor).append("`\n");
        }

        return textResult(sb.toString());
    }
}
