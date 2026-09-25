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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Adversarial challenger test suite for Milestone 5 (Commit 430446b2):
 * Empirically tests MCP memory_list and memory_export tools against all filter combinations,
 * boundary conditions, cursor pagination walking, and malformed inputs.
 */
@DisplayName("McpToolParityAdversarialChallengeTest — M5 MCP memory_list & memory_export Parity")
class McpToolParityAdversarialChallengeTest {

    private SpectorMemory memory;
    private SpectorMemoryAdmin admin;
    private MemoryListTool listTool;
    private MemoryExportTool exportTool;

    @BeforeEach
    void setUp() {
        memory = mock(SpectorMemory.class);
        admin = mock(SpectorMemoryAdmin.class);
        when(memory.admin()).thenReturn(admin);

        listTool = new MemoryListTool(memory);
        exportTool = new MemoryExportTool(memory);
    }

    private CognitiveRecord createRecord(String id, long timestampMs, MemoryType type, MemorySource source,
                                         String text, boolean tombstoned, boolean purged) {
        CognitiveRecord r = mock(CognitiveRecord.class);
        when(r.id()).thenReturn(id);
        when(r.timestampMs()).thenReturn(timestampMs);
        when(r.memoryType()).thenReturn(type);
        when(r.source()).thenReturn(source);
        when(r.text()).thenReturn(text);
        when(r.isTombstoned()).thenReturn(tombstoned);
        when(r.isPurged()).thenReturn(purged);
        when(r.toJson()).thenReturn("{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"ts\":" + timestampMs + "}");
        return r;
    }

    @Nested
    @DisplayName("Challenge 1: MCP memory_list Filter Matrix & Case Insensitivity")
    class MemoryListFilterMatrixTests {

        @ParameterizedTest(name = "Tier filter case variation: \"{0}\"")
        @ValueSource(strings = {"SEMANTIC", "semantic", "Semantic", "sEmAnTiC"})
        @DisplayName("Tier filtering is strictly case-insensitive across all case variants")
        void tierFilter_caseInsensitive(String tier) throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("rec-sem", 1000L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "semantic text", false, false),
                    createRecord("rec-epi", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "episodic text", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of("tier", tier));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("`rec-sem`");
            assertThat(text).doesNotContain("`rec-epi`");
        }

        @ParameterizedTest(name = "Source filter case variation: \"{0}\"")
        @ValueSource(strings = {"USER_STATED", "user_stated", "User_Stated"})
        @DisplayName("Source filtering is strictly case-insensitive across all case variants")
        void sourceFilter_caseInsensitive(String source) throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("rec-user", 1000L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "user text", false, false),
                    createRecord("rec-agent", 2000L, MemoryType.SEMANTIC, MemorySource.INFERRED, "agent text", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of("source", source));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("`rec-user`");
            assertThat(text).doesNotContain("`rec-agent`");
        }

        @Test
        @DisplayName("Temporal filters created_from and created_to match inclusive boundaries precisely")
        void temporalFilters_inclusiveBoundaries() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("rec-100", 100L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "text 100", false, false),
                    createRecord("rec-200", 200L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "text 200", false, false),
                    createRecord("rec-300", 300L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "text 300", false, false),
                    createRecord("rec-400", 400L, MemoryType.SEMANTIC, MemorySource.USER_STATED, "text 400", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            // Inclusive range: [200, 300]
            McpSchema.CallToolResult result = listTool.execute(Map.of(
                    "created_from", 200L,
                    "created_to", 300L
            ));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("`rec-200`", "`rec-300`");
            assertThat(text).doesNotContain("`rec-100`", "`rec-400`");
        }

        @Test
        @DisplayName("Conjunctive filter combination (tier + source + created_from + created_to) yields exact intersection")
        void conjunctiveFilters_exactIntersection() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("match", 250L, MemoryType.EPISODIC, MemorySource.OBSERVED, "match", false, false),
                    createRecord("wrong-tier", 250L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "wrong tier", false, false),
                    createRecord("wrong-source", 250L, MemoryType.EPISODIC, MemorySource.USER_STATED, "wrong source", false, false),
                    createRecord("too-early", 150L, MemoryType.EPISODIC, MemorySource.OBSERVED, "too early", false, false),
                    createRecord("too-late", 350L, MemoryType.EPISODIC, MemorySource.OBSERVED, "too late", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = listTool.execute(Map.of(
                    "tier", "EPISODIC",
                    "source", "OBSERVED",
                    "created_from", 200L,
                    "created_to", 300L
            ));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("`match`");
            assertThat(text).doesNotContain("`wrong-tier`", "`wrong-source`", "`too-early`", "`too-late`");
        }
    }

    @Nested
    @DisplayName("Challenge 2: memory_list Cursor Walking, Limits, and Malformed Cursors")
    class MemoryListCursorAndLimitTests {

        @Test
        @DisplayName("Multi-page cursor traversal traverses full dataset deterministically without loss or duplication")
        void cursorTraversal_zeroDuplicatesZeroLoss() throws Exception {
            List<CognitiveRecord> records = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                records.add(createRecord("mem-" + String.format("%02d", i), 1000L + (i * 10L),
                        MemoryType.WORKING, MemorySource.PROCEDURAL, "item " + i, false, false));
            }
            when(admin.listAll()).thenReturn(records);

            int limit = 3;
            List<String> visitedIds = new ArrayList<>();
            String cursor = null;
            int pageCount = 0;

            while (true) {
                Map<String, Object> args = new HashMap<>();
                args.put("limit", limit);
                if (cursor != null) {
                    args.put("cursor", cursor);
                }

                McpSchema.CallToolResult result = listTool.execute(args);
                String text = ((McpSchema.TextContent) result.content().get(0)).text();
                pageCount++;

                for (String line : text.split("\n")) {
                    if (line.startsWith("| `mem-")) {
                        int start = line.indexOf("`") + 1;
                        int end = line.indexOf("`", start);
                        visitedIds.add(line.substring(start, end));
                    }
                }

                if (text.contains("**Next Cursor:** `")) {
                    int start = text.indexOf("**Next Cursor:** `") + 18;
                    cursor = text.substring(start, text.indexOf("`", start));
                } else {
                    break;
                }
            }

            // 11 items with limit 3 -> pages of size 3, 3, 3, 2 = 4 pages
            assertThat(pageCount).isEqualTo(4);
            assertThat(visitedIds).hasSize(11);
            assertThat(new HashSet<>(visitedIds)).hasSize(11); // zero duplicates

            // Reverse order because of total order timestampMs DESC
            List<String> expectedOrder = new ArrayList<>();
            for (int i = 10; i >= 0; i--) {
                expectedOrder.add("mem-" + String.format("%02d", i));
            }
            assertThat(visitedIds).containsExactlyElementsOf(expectedOrder);
        }

        @Test
        @DisplayName("Limit clamping: negative and zero clamp to 1, values > 500 clamp to 500")
        void limitClamping() throws Exception {
            List<CognitiveRecord> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                records.add(createRecord("mem-" + i, 1000L + i, MemoryType.WORKING, MemorySource.PROCEDURAL, "item", false, false));
            }
            when(admin.listAll()).thenReturn(records);

            // Limit 0 clamps to 1
            McpSchema.CallToolResult r0 = listTool.execute(Map.of("limit", 0));
            String t0 = ((McpSchema.TextContent) r0.content().get(0)).text();
            assertThat(t0).contains("Memory List (1 of 10)");

            // Limit -5 clamps to 1
            McpSchema.CallToolResult rNeg = listTool.execute(Map.of("limit", -5));
            String tNeg = ((McpSchema.TextContent) rNeg.content().get(0)).text();
            assertThat(tNeg).contains("Memory List (1 of 10)");

            // Default limit without limit param is 50 (or max records if < 50)
            McpSchema.CallToolResult rDef = listTool.execute(Map.of());
            String tDef = ((McpSchema.TextContent) rDef.content().get(0)).text();
            assertThat(tDef).contains("Memory List (10 of 10)");
        }

        @ParameterizedTest(name = "Malformed cursor token: \"{0}\"")
        @ValueSource(strings = {
                "not-valid-base64-!!!",
                "bm9jb2xvbg", // "nocolon" in base64
                "YWJjOm1lbS0x" // "abc:mem-1" (non-numeric timestamp) in base64
        })
        @DisplayName("Malformed cursor tokens throw descriptive IllegalArgumentException or NumberFormatException")
        void malformedCursor_rejected(String malformed) {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.WORKING, MemorySource.PROCEDURAL, "item", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            assertThatThrownBy(() -> listTool.execute(Map.of("cursor", malformed)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Challenge 3: MCP memory_export Scoping, Purge/Tombstone Filtering & Pagination")
    class MemoryExportScopingTests {

        @Test
        @DisplayName("Unscoped export delegates to memory.exportJson() without filtering")
        void unscopedExport_delegatesToExportJson() throws Exception {
            when(memory.totalMemories()).thenReturn(2);
            when(memory.exportJson()).thenReturn("[{\"id\":\"mem-1\"},{\"id\":\"mem-2\"}]");

            McpSchema.CallToolResult result = exportTool.execute(Map.of());
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("📦 Exported 2 memories");
            assertThat(text).contains("[{\"id\":\"mem-1\"},{\"id\":\"mem-2\"}]");
            verify(memory, times(1)).exportJson();
            verify(admin, never()).listAll();
        }

        @Test
        @DisplayName("Scoped export strictly excludes tombstoned and purged memories")
        void scopedExport_excludesTombstonedAndPurgedRecords() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("rec-live", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "live", false, false),
                    createRecord("rec-tombstoned", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "tomb", true, false),
                    createRecord("rec-purged", 3000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "purged", false, true)
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = exportTool.execute(Map.of("tier", "EPISODIC"));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("📦 Exported 1 memories (scoped)");
            assertThat(text).contains("rec-live");
            assertThat(text).doesNotContain("rec-tombstoned", "rec-purged");
        }

        @Test
        @DisplayName("Scoped export with limit and cursor paginates and propagates nextCursor")
        void scopedExport_paginatesWithCursor() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "first", false, false),
                    createRecord("mem-2", 2000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "second", false, false),
                    createRecord("mem-3", 3000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "third", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            // Page 1: limit 2
            McpSchema.CallToolResult p1Result = exportTool.execute(Map.of("tier", "SEMANTIC", "limit", 2));
            String p1Text = ((McpSchema.TextContent) p1Result.content().get(0)).text();

            assertThat(p1Text).contains("📦 Exported 2 memories (scoped)");
            assertThat(p1Text).contains("mem-3", "mem-2"); // newest first
            assertThat(p1Text).contains("**Next Cursor:** `");

            int cursorIdx = p1Text.indexOf("**Next Cursor:** `") + 18;
            String cursor1 = p1Text.substring(cursorIdx, p1Text.indexOf("`", cursorIdx));

            // Page 2: with cursor1, limit 2
            McpSchema.CallToolResult p2Result = exportTool.execute(Map.of("tier", "SEMANTIC", "limit", 2, "cursor", cursor1));
            String p2Text = ((McpSchema.TextContent) p2Result.content().get(0)).text();

            assertThat(p2Text).contains("📦 Exported 1 memories (scoped)");
            assertThat(p2Text).contains("mem-1");
            assertThat(p2Text).doesNotContain("**Next Cursor:**");
        }

        @Test
        @DisplayName("Scoped export with no matching memories returns friendly empty notice")
        void scopedExport_noMatches_returnsFriendlyNotice() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("mem-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "text", false, false)
            );
            when(admin.listAll()).thenReturn(records);

            McpSchema.CallToolResult result = exportTool.execute(Map.of("tier", "PROCEDURAL"));
            String text = ((McpSchema.TextContent) result.content().get(0)).text();

            assertThat(text).contains("📭 No memories found matching export criteria.");
        }
    }
}
