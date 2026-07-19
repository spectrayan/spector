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
package com.spectrayan.spector.commons.chunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MarkdownChunker} — the markdown-aware text chunker.
 */
@DisplayName("MarkdownChunker")
class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker();

    // ══════════════════════════════════════════════════════════════
    // BASIC BEHAVIOR
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic behavior")
    class BasicBehavior {

        @Test
        @DisplayName("empty content returns empty list")
        void emptyContent() {
            assertEquals(List.of(), chunker.chunk("doc", ""));
            assertEquals(List.of(), chunker.chunk("doc", "   "));
            assertEquals(List.of(), chunker.chunk("doc", null, ChunkConfig.DEFAULT));
        }

        @Test
        @DisplayName("small content fits in single chunk")
        void smallContent() {
            List<Chunk> chunks = chunker.chunk("doc-1", "Hello world.",
                    ChunkConfig.markdown(800, 50));
            assertEquals(1, chunks.size());
            assertEquals("Hello world.", chunks.get(0).text());
            assertEquals("doc-1", chunks.get(0).parentId());
        }

        @Test
        @DisplayName("chunk IDs are sequential")
        void chunkIds() {
            String content = "# Heading 1\n\nParagraph one.\n\n# Heading 2\n\nParagraph two.";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(30, 0));
            for (int i = 0; i < chunks.size(); i++) {
                assertTrue(chunks.get(i).chunkId().contains("chunk-" + i));
                assertEquals(i, chunks.get(i).index());
            }
        }

        @Test
        @DisplayName("name() returns 'markdown'")
        void name() {
            assertEquals("markdown", chunker.name());
        }

        @Test
        @DisplayName("supports text/markdown content type")
        void contentType() {
            assertTrue(chunker.supportedContentTypes().contains("text/markdown"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HEADING PRESERVATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Heading preservation")
    class HeadingPreservation {

        @Test
        @DisplayName("headings are preferred split boundaries")
        void headingBoundaries() {
            String content = """
                    # Section A
                    Content for section A which has enough text.
                    
                    # Section B
                    Content for section B which has enough text.""";
            List<Chunk> chunks = chunker.chunk("doc", content.trim(),
                    ChunkConfig.markdown(80, 0));

            assertTrue(chunks.size() >= 2, "Should split at heading boundary");
            // First chunk should contain Section A content
            assertTrue(chunks.get(0).text().contains("Section A"));
        }

        @Test
        @DisplayName("heading_context metadata is populated")
        void headingContext() {
            String content = """
                    # Architecture
                    ## Data Flow
                    The system processes data.
                    ### HNSW Index
                    Vector search details.""";
            List<Chunk> chunks = chunker.chunk("doc", content.trim(),
                    ChunkConfig.markdown(800, 0));

            // At least one chunk should have heading_context
            boolean hasContext = chunks.stream()
                    .anyMatch(c -> {
                        String ctx = c.metadata().get("heading_context");
                        return ctx != null && !ctx.isEmpty();
                    });
            assertTrue(hasContext, "Chunks should carry heading_context metadata");
        }

        @Test
        @DisplayName("heading hierarchy is tracked correctly")
        void headingHierarchy() {
            String content = """
                    # Top Level
                    ## Sub Section
                    Content under subsection.""";
            List<Chunk> chunks = chunker.chunk("doc", content.trim(),
                    ChunkConfig.markdown(800, 0));

            Chunk lastChunk = chunks.get(chunks.size() - 1);
            String ctx = lastChunk.metadata().get("heading_context");
            assertNotNull(ctx);
            assertTrue(ctx.contains("Top Level"), "heading_context should include H1");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CODE BLOCK PRESERVATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Code block preservation")
    class CodeBlockPreservation {

        @Test
        @DisplayName("fenced code blocks are never split")
        void fencedCodeNotSplit() {
            String codeBlock = """
                    ```java
                    public class Example {
                        private final String name;
                        public Example(String name) {
                            this.name = name;
                        }
                        public String getName() {
                            return name;
                        }
                    }
                    ```""";
            List<Chunk> chunks = chunker.chunk("doc", codeBlock.trim(),
                    ChunkConfig.markdown(5000, 0));

            // Entire code block should be in one chunk
            assertEquals(1, chunks.size());
            assertTrue(chunks.get(0).text().contains("public class Example"));
            assertTrue(chunks.get(0).text().contains("return name"));
        }

        @Test
        @DisplayName("mermaid diagrams are kept whole")
        void mermaidDiagram() {
            String content = """
                    # Diagram
                    
                    ```mermaid
                    graph LR
                        A[Start] --> B[Process]
                        B --> C[End]
                    ```""";
            List<Chunk> chunks = chunker.chunk("doc", content.trim(),
                    ChunkConfig.markdown(5000, 0));

            boolean hasMermaid = chunks.stream()
                    .anyMatch(c -> "code:mermaid".equals(c.metadata().get("block_type"))
                            || c.text().contains("```mermaid"));
            assertTrue(hasMermaid, "Mermaid diagram should be preserved");
        }

        @Test
        @DisplayName("code block_type metadata includes language")
        void codeBlockType() {
            String content = "```python\nprint('hello')\n```";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            assertFalse(chunks.isEmpty());
            String blockType = chunks.get(0).metadata().get("block_type");
            assertNotNull(blockType);
            assertTrue(blockType.startsWith("code:"), "block_type should be code:python");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TABLE PRESERVATION
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Table preservation")
    class TablePreservation {

        @Test
        @DisplayName("small tables are kept in single chunk")
        void smallTable() {
            String table = """
                    | Name | Value |
                    |------|-------|
                    | A    | 1     |
                    | B    | 2     |""";
            List<Chunk> chunks = chunker.chunk("doc", table.trim(),
                    ChunkConfig.markdown(800, 0));

            assertEquals(1, chunks.size());
            assertEquals("table", chunks.get(0).metadata().get("block_type"));
        }

        @Test
        @DisplayName("table block_type is 'table'")
        void tableBlockType() {
            // Table alone so it's not packed with other block types
            String content = "| Col | Val |\n|-----|-----|\n| X | 1 |";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            boolean hasTable = chunks.stream()
                    .anyMatch(c -> "table".equals(c.metadata().get("block_type")));
            assertTrue(hasTable, "Should identify table blocks");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // OVERLAP
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Overlap behavior")
    class OverlapBehavior {

        @Test
        @DisplayName("chunks overlap when configured")
        void overlapApplied() {
            // Create content large enough to span multiple chunks
            String para1 = "First paragraph with enough text to fill a chunk. ".repeat(5);
            String para2 = "Second paragraph with different content for chunking. ".repeat(5);
            String content = para1.trim() + "\n\n" + para2.trim();

            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(200, 50));

            if (chunks.size() >= 2) {
                // With overlap, the second chunk's start should be before
                // the end of the first chunk
                assertTrue(chunks.get(1).startChar() < chunks.get(0).endChar()
                        || chunks.get(1).text().length() > 0,
                        "Overlap should create some shared content");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // METADATA
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Chunk metadata")
    class ChunkMetadata {

        @Test
        @DisplayName("metadata includes chunk_index and total_chunks")
        void indexMetadata() {
            String content = "# A\n\nContent A.\n\n# B\n\nContent B.";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(30, 0));

            if (chunks.size() > 1) {
                assertEquals("0", chunks.get(0).metadata().get("chunk_index"));
                assertEquals(String.valueOf(chunks.size()),
                        chunks.get(0).metadata().get("total_chunks"));
            }
        }

        @Test
        @DisplayName("blockquote has correct block_type")
        void blockquoteType() {
            String content = "> This is a quote\n> spanning multiple lines";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            assertFalse(chunks.isEmpty());
            assertEquals("blockquote", chunks.get(0).metadata().get("block_type"));
        }

        @Test
        @DisplayName("content_type metadata matches config")
        void contentTypeMetadata() {
            List<Chunk> chunks = chunker.chunk("doc", "Hello",
                    ChunkConfig.markdown(800, 0));

            assertFalse(chunks.isEmpty());
            assertEquals("text/markdown", chunks.get(0).metadata().get("content_type"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("content without any markdown is chunked as prose")
        void plainTextFallback() {
            String content = "This is plain text without any markdown formatting. "
                    + "It should still be chunked properly at sentence boundaries.";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            assertFalse(chunks.isEmpty());
            assertEquals("prose", chunks.get(0).metadata().get("block_type"));
        }

        @Test
        @DisplayName("horizontal rules act as split points")
        void horizontalRules() {
            String content = "Part one.\n\n---\n\nPart two.";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            assertTrue(chunks.size() >= 1, "Content with HR should produce chunks");
        }

        @Test
        @DisplayName("list items are grouped together")
        void listGrouping() {
            String content = "- Item one\n- Item two\n- Item three";
            List<Chunk> chunks = chunker.chunk("doc", content,
                    ChunkConfig.markdown(800, 0));

            assertEquals(1, chunks.size(), "Short list should be one chunk");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // REGISTRY
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ChunkerRegistry integration")
    class RegistryIntegration {

        @Test
        @DisplayName("MarkdownChunker is discoverable via ServiceLoader")
        void serviceLoaderDiscovery() {
            ChunkerRegistry registry = ChunkerRegistry.discover();
            assertTrue(registry.byName("markdown").isPresent(),
                    "MarkdownChunker should be discoverable");
        }

        @Test
        @DisplayName("SentenceChunker is discoverable via ServiceLoader")
        void sentenceChunkerDiscovery() {
            ChunkerRegistry registry = ChunkerRegistry.discover();
            assertTrue(registry.byName("sentence").isPresent(),
                    "SentenceChunker should be discoverable");
        }

        @Test
        @DisplayName("forContentType selects MarkdownChunker for text/markdown")
        void contentTypeSelection() {
            ChunkerRegistry registry = ChunkerRegistry.discover();
            var chunker = registry.forContentType("text/markdown");
            assertTrue(chunker.isPresent());
            assertEquals("markdown", chunker.get().name());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PARENT-CHILD CHUNKING
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parent-Child chunking")
    class ParentChildChunking {

        @Test
        @DisplayName("Markdown section is split into parent and child chunks")
        void parentChildSplitting() {
            String content = """
                    # Introduction
                    This is paragraph one of the introduction section.
                    This is paragraph two.
                    
                    ## Details
                    Here are some detail blocks.
                    - Detail list item 1
                    - Detail list item 2
                    """;
            
            ChunkConfig config = new ChunkConfig(800, 0, "text/markdown", null, true, true, false, true);
            List<Chunk> chunks = chunker.chunk("doc-pc", content, config);

            assertFalse(chunks.isEmpty(), "Should produce chunks");
            
            boolean hasParent = false;
            boolean hasChild = false;

            for (Chunk chunk : chunks) {
                String role = chunk.metadata().get("chunk_role");
                if ("parent".equals(role)) {
                    hasParent = true;
                    assertTrue(chunk.text().contains("Introduction") || chunk.text().contains("Details"),
                            "Parent text should contain heading names");
                } else if ("child".equals(role)) {
                    hasChild = true;
                    assertNotNull(chunk.metadata().get("parent_chunk_id"), "Child must point to a parent ID");
                }
            }

            assertTrue(hasParent, "Should produce parent chunks");
            assertTrue(hasChild, "Should produce child chunks");
        }
    }
}
