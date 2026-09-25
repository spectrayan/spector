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
 * Empirical Adversarial Challenge Test Suite for MCP Tools (R5 / F18, F19).
 */
@DisplayName("MemoryListToolAdversarialChallengeTest — MCP Listing & Export Adversarial Challenge")
class MemoryListToolAdversarialChallengeTest {

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

    @Nested
    @DisplayName("Challenge 1: MCP Tool Malformed Cursor Rejection")
    class MalformedCursorTests {

        @ParameterizedTest(name = "Malformed cursor input: \"{0}\"")
        @ValueSource(strings = {
                "not-valid-base64-!@#$",
                "bm9jb2xvbg",                  // Base64 for "nocolon"
                "bm9udW1lcmljOnNvbWVpZA",      // Base64 for "nonumeric:someid"
                "Om9ubHlpZA"                   // Base64 for ":onlyid"
        })
        @DisplayName("Direct execute throws IllegalArgumentException on malformed cursor")
        void directExecute_malformedCursor_throwsException(String malformed) {
            CognitiveRecord sample = createRecord("mem-1", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "Sample");
            when(admin.listAll()).thenReturn(List.of(sample));

            assertThatThrownBy(() -> listTool.execute(Map.of("cursor", malformed)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Blank or null cursor executes successfully starting from page 1")
        void blankOrNullCursor_executesSuccessfully() throws Exception {
            CognitiveRecord sample = createRecord("mem-1", 1000L, MemoryType.SEMANTIC, MemorySource.OBSERVED, "Sample");
            when(admin.listAll()).thenReturn(List.of(sample));

            McpSchema.CallToolResult nullResult = listTool.execute(Collections.singletonMap("cursor", null));
            assertThat(nullResult.isError()).isFalse();

            McpSchema.CallToolResult blankResult = listTool.execute(Map.of("cursor", "   "));
            assertThat(blankResult.isError()).isFalse();
        }
    }

    @Nested
    @DisplayName("Challenge 2: Multi-Page Cursor Walking & Tie-Breaker Stability")
    class MultiPageAndTieBreakerTests {

        @Test
        @DisplayName("Walk 25 records with limit 7: zero duplicates, zero skips, exact page boundaries")
        void walkEntireDataset_zeroDuplicatesZeroSkips() throws Exception {
            int total = 25;
            List<CognitiveRecord> dataset = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                dataset.add(createRecord("mem-" + String.format("%02d", i), 1000L + (i * 10L),
                        MemoryType.SEMANTIC, MemorySource.OBSERVED, "Text " + i));
            }
            when(admin.listAll()).thenReturn(dataset);

            int limit = 7;
            List<String> visitedIds = new ArrayList<>();
            String cursor = null;
            int pages = 0;

            while (true) {
                Map<String, Object> args = new HashMap<>();
                args.put("limit", limit);
                if (cursor != null) args.put("cursor", cursor);

                McpSchema.CallToolResult result = listTool.execute(args);
                assertThat(result.isError()).isFalse();
                pages++;

                String text = ((McpSchema.TextContent) result.content().get(0)).text();
                // Extract IDs from table rows: | `mem-XX` | ...
                for (String line : text.split("\n")) {
                    if (line.startsWith("| `mem-")) {
                        int start = line.indexOf("`") + 1;
                        int end = line.indexOf("`", start);
                        visitedIds.add(line.substring(start, end));
                    }
                }

                if (text.contains("**Next Cursor:** `")) {
                    int cStart = text.indexOf("**Next Cursor:** `") + 18;
                    cursor = text.substring(cStart, text.indexOf("`", cStart));
                } else {
                    cursor = null;
                    break;
                }
            }

            assertThat(visitedIds).hasSize(total);
            assertThat(new HashSet<>(visitedIds)).hasSize(total); // Zero duplicates
            assertThat(pages).isEqualTo(4); // ceil(25 / 7) = 4
        }

        @Test
        @DisplayName("Identical timestamps are tie-broken strictly by id DESC across cursor pages")
        void identicalTimestamps_tieBrokenByIdDescending() throws Exception {
            long sharedTs = 2000L;
            List<CognitiveRecord> records = List.of(
                    createRecord("id-01", sharedTs, MemoryType.EPISODIC, MemorySource.USER_STATED, "One"),
                    createRecord("id-02", sharedTs, MemoryType.EPISODIC, MemorySource.USER_STATED, "Two"),
                    createRecord("id-03", sharedTs, MemoryType.EPISODIC, MemorySource.USER_STATED, "Three"),
                    createRecord("id-04", sharedTs, MemoryType.EPISODIC, MemorySource.USER_STATED, "Four")
            );
            when(admin.listAll()).thenReturn(records);

            // Page 1: limit 2 -> should return id-04, id-03
            McpSchema.CallToolResult p1 = listTool.execute(Map.of("limit", 2));
            String t1 = ((McpSchema.TextContent) p1.content().get(0)).text();
            assertThat(t1).contains("`id-04`").contains("`id-03`");
            assertThat(t1).doesNotContain("`id-02`", "`id-01`");

            int cStart = t1.indexOf("**Next Cursor:** `") + 18;
            String cursor1 = t1.substring(cStart, t1.indexOf("`", cStart));

            // Page 2: limit 2 with cursor1 -> should return id-02, id-01
            McpSchema.CallToolResult p2 = listTool.execute(Map.of("limit", 2, "cursor", cursor1));
            String t2 = ((McpSchema.TextContent) p2.content().get(0)).text();
            assertThat(t2).contains("`id-02`").contains("`id-01`");
            assertThat(t2).doesNotContain("`id-04`", "`id-03`");
            assertThat(t2).doesNotContain("**Next Cursor:**");
        }
    }

    @Nested
    @DisplayName("Challenge 3: MemoryExportTool Scoping & Pagination Parity")
    class MemoryExportToolChallengeTests {

        @Test
        @DisplayName("Export with limit and cursor pages JSON array without corruption")
        void exportTool_limitAndCursor_pagesCleanly() throws Exception {
            List<CognitiveRecord> records = List.of(
                    createRecord("exp-1", 1000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Content 1"),
                    createRecord("exp-2", 2000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Content 2"),
                    createRecord("exp-3", 3000L, MemoryType.EPISODIC, MemorySource.USER_STATED, "Content 3")
            );
            when(admin.listAll()).thenReturn(records);

            // Page 1: limit 2 (sorted order: exp-3, exp-2)
            McpSchema.CallToolResult p1 = exportTool.execute(Map.of("limit", 2));
            String t1 = ((McpSchema.TextContent) p1.content().get(0)).text();
            assertThat(t1).contains("exp-3").contains("exp-2");
            assertThat(t1).doesNotContain("exp-1");
            assertThat(t1).contains("**Next Cursor:** `");

            int cStart = t1.indexOf("**Next Cursor:** `") + 18;
            String cursor = t1.substring(cStart, t1.indexOf("`", cStart));

            // Page 2: with cursor, limit 2 -> returns exp-1
            McpSchema.CallToolResult p2 = exportTool.execute(Map.of("limit", 2, "cursor", cursor));
            String t2 = ((McpSchema.TextContent) p2.content().get(0)).text();
            assertThat(t2).contains("exp-1");
            assertThat(t2).doesNotContain("exp-2", "exp-3");
            assertThat(t2).doesNotContain("**Next Cursor:**");
        }
    }
}
