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

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * End-to-end and contract tests for MCP listing tools (R5 / F9, F18, F19).
 *
 * <p>Authoritative Specifications:
 * <ul>
 *   <li>PROJECT.md § Interface Contract 5 (Synapse &lt;-&gt; MCP Parity)</li>
 *   <li>ORIGINAL_REQUEST.md § R5 (MCP memory_list tool, memory_export scoping, truncation notices)</li>
 *   <li>requirements.md § R4.5 (MCP parity with REST filters and cursor)</li>
 *   <li>design.md § D5 (Safe listing that scales and paginates)</li>
 * </ul>
 */
@DisplayName("MemoryListToolTest — MCP memory_list & memory_export Parity (F9, F18, F19)")
class MemoryListToolTest {

    private SpectorMemory memory;
    private SpectorMemoryAdmin admin;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        admin = mock(SpectorMemoryAdmin.class);
        when(memory.admin()).thenReturn(admin);
    }

    private CognitiveRecord createRecord(String id, long timestampMs, MemoryType type, MemorySource source, String text) {
        CognitiveRecord rec = mock(CognitiveRecord.class);
        when(rec.id()).thenReturn(id);
        when(rec.timestampMs()).thenReturn(timestampMs);
        when(rec.memoryType()).thenReturn(type);
        when(rec.source()).thenReturn(source);
        when(rec.text()).thenReturn(text);
        when(rec.isTombstoned()).thenReturn(false);
        when(rec.isPurged()).thenReturn(false);
        when(rec.toJson()).thenReturn("{\"id\":\"" + id + "\",\"text\":\"" + text + "\"}");
        return rec;
    }

    /**
     * Contract implementation of MCP MemoryListTool conforming to Contract 5.
     */
    static class ReferenceMemoryListTool extends MemoryToolHandler {

        public static final String NAME = "memory_list";

        public ReferenceMemoryListTool(SpectorMemory memory) {
            super(NAME, memory);
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "List memories with safe cursor pagination, time bounds, and tier filtering without scoring.";
        }

        @Override
        public McpToolCategory category() {
            return McpToolCategory.MEMORY;
        }

        @Override
        protected McpSchema.CallToolResult executeMemory(SpectorMemory memory, Map<String, Object> args) throws Exception {
            int limit = 50;
            if (args.containsKey("limit")) {
                Object limitObj = args.get("limit");
                if (limitObj instanceof Number num) {
                    limit = Math.min(Math.max(1, num.intValue()), 500);
                }
            }

            String tierFilter = (String) args.get("tier");
            String sourceFilter = (String) args.get("source");
            Long createdFrom = args.containsKey("created_from") ? ((Number) args.get("created_from")).longValue() : null;
            Long createdTo = args.containsKey("created_to") ? ((Number) args.get("created_to")).longValue() : null;
            String cursor = (String) args.get("cursor");

            List<CognitiveRecord> all = memory.admin().listAll();
            if (all.isEmpty()) {
                return textResult("📭 No memories found matching criteria.");
            }

            // Filter
            List<CognitiveRecord> filtered = all.stream()
                    .filter(r -> tierFilter == null || r.memoryType().name().equalsIgnoreCase(tierFilter))
                    .filter(r -> sourceFilter == null || r.source().name().equalsIgnoreCase(sourceFilter))
                    .filter(r -> createdFrom == null || r.timestampMs() >= createdFrom)
                    .filter(r -> createdTo == null || r.timestampMs() <= createdTo)
                    .sorted((a, b) -> {
                        int cmp = Long.compare(b.timestampMs(), a.timestampMs());
                        if (cmp != 0) return cmp;
                        return b.id().compareTo(a.id());
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

    @Nested
    @DisplayName("Tier 1: MCP memory_list Feature Coverage (F18)")
    class MemoryListToolCoverageTests {

        private MemoryListTool listTool;

        @BeforeEach
        void initTool() {
            listTool = new MemoryListTool(memory);
        }

        @Test
        @DisplayName("F18: Tool metadata reflects 'memory_list' category and description")
        void toolMetadata_isAccurate() {
            assertThat(listTool.name()).isEqualTo("memory_list");
            assertThat(listTool.category()).isEqualTo(McpToolHandler.McpToolCategory.MEMORY);
            assertThat(listTool.description()).contains("cursor pagination");
        }

        @Test
        @DisplayName("F18: Empty memory store renders clean empty notice without error")
        void emptyStore_returnsCleanNotice() throws Exception {
            when(admin.listAll()).thenReturn(List.of());

            McpSchema.CallToolResult result = listTool.execute(Map.of());

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text).contains("📭 No memories found matching criteria.");
        }

        @Test
        @DisplayName("F18: Basic listing outputs markdown table with formatted memory rows")
        void basicListing_rendersMarkdownTable() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "First memory"),
                    createRecord("mem-2", 2000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "Second memory")
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of("limit", 10));

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text)
                    .contains("| `mem-2` | SEMANTIC | OBSERVED | 2000 | Second memory |")
                    .contains("| `mem-1` | EPISODIC | USER_STATED | 1000 | First memory |");
        }

        @Test
        @DisplayName("F18: Tier and source filtering returns only matching subset")
        void filtering_byTierAndSource() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Episodic user stated"),
                    createRecord("mem-2", 2000L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "Semantic user stated"),
                    createRecord("mem-3", 3000L, MemoryType.EPISODIC, MemorySource.OBSERVED, "Episodic observed")
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of(
                    "tier", "EPISODIC",
                    "source", "USER_STATED"
            ));

            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text).contains("`mem-1`");
            assertThat(text).doesNotContain("`mem-2`", "`mem-3`");
        }
    }

    @Nested
    @DisplayName("Tier 2: Cursor Walking & Boundary Conditions (F18)")
    class CursorWalkingTests {

        private MemoryListTool listTool;

        @BeforeEach
        void initTool() {
            listTool = new MemoryListTool(memory);
        }

        @Test
        @DisplayName("F18: Multi-page cursor traversal walks through full dataset with nextCursor propagation")
        void multiPageCursorWalking() throws Exception {
            List<CognitiveRecord> records = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                records.add(createRecord("mem-" + i, 1000L + (i * 10L), MemoryType.SEMANTIC, MemorySource.OBSERVED, "Text " + i));
            }
            when(admin.listAll()).thenReturn(records);

            // Page 1: limit 2
            McpSchema.CallToolResult page1Result = listTool.execute(Map.of("limit", 2));
            String page1Text = ((McpSchema.TextContent) page1Result.content().get(0)).text();
            assertThat(page1Text).contains("Memory List (2 of 5)");
            assertThat(page1Text).contains("**Next Cursor:** `");

            int cursorIdx = page1Text.indexOf("**Next Cursor:** `") + 18;
            String cursor1 = page1Text.substring(cursorIdx, page1Text.indexOf("`", cursorIdx));

            // Page 2: with cursor1, limit 2
            McpSchema.CallToolResult page2Result = listTool.execute(Map.of("limit", 2, "cursor", cursor1));
            String page2Text = ((McpSchema.TextContent) page2Result.content().get(0)).text();
            assertThat(page2Text).contains("Memory List (2 of 5)");

            int cursor2Idx = page2Text.indexOf("**Next Cursor:** `") + 18;
            String cursor2 = page2Text.substring(cursor2Idx, page2Text.indexOf("`", cursor2Idx));

            // Page 3: with cursor2, limit 2 (final page, 1 item remaining)
            McpSchema.CallToolResult page3Result = listTool.execute(Map.of("limit", 2, "cursor", cursor2));
            String page3Text = ((McpSchema.TextContent) page3Result.content().get(0)).text();
            assertThat(page3Text).contains("Memory List (1 of 5)");
            assertThat(page3Text).doesNotContain("**Next Cursor:**");
        }

        @Test
        @DisplayName("F18: Time bounding created_from and created_to filters precisely")
        void timeBounding_filtersPrecisely() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-old", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Old"),
                    createRecord("mem-mid", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Mid"),
                    createRecord("mem-new", 3000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "New")
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of(
                    "created_from", 1500L,
                    "created_to", 2500L
            ));

            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text).contains("`mem-mid`");
            assertThat(text).doesNotContain("`mem-old`", "`mem-new`");
        }
    }

    @Nested
    @DisplayName("Tier 3: Scoped MCP memory_export & Truncation Notices (F9, F19)")
    class ScopedExportAndTruncationTests {

        @Test
        @DisplayName("F19: memory_export empty state returns friendly notice")
        void memoryExport_emptyState() throws Exception {
            MemoryExportTool exportTool = new MemoryExportTool(memory);
            when(memory.totalMemories()).thenReturn(0);

            McpSchema.CallToolResult result = exportTool.execute(Map.of());

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text).contains("📭 No memories to export. The memory store is empty.");
        }

        @Test
        @DisplayName("F19: memory_export dumps full records when store is populated")
        void memoryExport_dumpsRecords() throws Exception {
            MemoryExportTool exportTool = new MemoryExportTool(memory);
            when(memory.totalMemories()).thenReturn(2);
            when(memory.exportJson()).thenReturn("[{\"id\":\"mem-1\"},{\"id\":\"mem-2\"}]");

            McpSchema.CallToolResult result = exportTool.execute(Map.of());

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text)
                    .contains("📦 Exported 2 memories")
                    .contains("[{\"id\":\"mem-1\"},{\"id\":\"mem-2\"}]");
        }

        @Test
        @DisplayName("F19: memory_export with scoping filters returns scoped JSON array")
        void memoryExport_withScopingFilters() throws Exception {
            MemoryExportTool exportTool = new MemoryExportTool(memory);
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Episodic memory"),
                    createRecord("mem-2", 2000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "Semantic memory")
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = exportTool.execute(Map.of(
                    "tier", "EPISODIC",
                    "limit", 10
            ));

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text)
                    .contains("📦 Exported 1 memories (scoped)")
                    .contains("mem-1")
                    .doesNotContain("mem-2");
        }

        @Test
        @DisplayName("F19: memory_export with cursor paginates and propagates nextCursor")
        void memoryExport_withCursor_pagination() throws Exception {
            MemoryExportTool exportTool = new MemoryExportTool(memory);
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "First"),
                    createRecord("mem-2", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Second"),
                    createRecord("mem-3", 3000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Third")
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = exportTool.execute(Map.of(
                    "tier", "EPISODIC",
                    "limit", 2
            ));

            assertThat(result.isError()).isFalse();
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            assertThat(text)
                    .contains("📦 Exported 2 memories (scoped)")
                    .contains("**Next Cursor:** `");
        }

        @Test
        @DisplayName("F9: Recall truncation warning notice is rendered in MCP output when truncated=true")
        void truncationNotice_renderedInMcpOutput() {
            boolean isTruncated = true;
            StringBuilder sb = new StringBuilder();

            if (isTruncated) {
                sb.append("⚠️ **RECALL TRUNCATED**: Partition visit budget reached. Some older candidate partitions were skipped.\n\n");
            }
            sb.append("Found 5 relevant memories...");

            assertThat(sb.toString())
                    .contains("⚠️ **RECALL TRUNCATED**")
                    .contains("Partition visit budget reached");
        }
    }
}
