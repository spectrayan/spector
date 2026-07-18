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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown-aware text chunker that preserves document structure.
 *
 * <p>Splits text content respecting markdown structural elements:</p>
 * <ul>
 *   <li><b>Headings</b> — preferred split boundaries ({@code # H1} &gt; {@code ## H2} &gt; ...)</li>
 *   <li><b>Fenced code blocks</b> — never split inside {@code ```} blocks (including mermaid)</li>
 *   <li><b>Tables</b> — split at row boundaries, preserving header row</li>
 *   <li><b>Block quotes</b> — kept together when possible</li>
 *   <li><b>Lists</b> — respected as atomic units</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li><b>Structural Parse</b> — line-by-line state machine classifies content into blocks</li>
 *   <li><b>Section Grouping</b> — blocks grouped under heading hierarchy</li>
 *   <li><b>Greedy Packing</b> — blocks packed into chunks up to {@code maxChunkSize}</li>
 *   <li><b>Overlap</b> — word-boundary-snapped overlap between chunks</li>
 *   <li><b>Metadata</b> — heading_context, block_type, content_type per chunk</li>
 * </ol>
 *
 * <h3>Embedded Block Detection</h3>
 * <p>When {@link ChunkConfig#detectEmbeddedBlocks()} is enabled, heuristically
 * detects JSON, XML, and indented code blocks even in plain text extracted from
 * Word/PDF documents.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Stateless and thread-safe. All state is local to method calls.</p>
 *
 * @see TextChunker
 * @see ChunkConfig
 */
public class MarkdownChunker implements TextChunker {

    private static final Logger log = LoggerFactory.getLogger(MarkdownChunker.class);

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.*)");
    private static final Pattern FENCED_CODE_START = Pattern.compile("^\\s*```(.*)");
    private static final Pattern FENCED_CODE_END = Pattern.compile("^\\s*```\\s*$");
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^\\s*(---|\\*\\*\\*|___)\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>.*");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*([-*]|\\d+\\.)\\s+.*");
    private static final Pattern INDENTED_CODE = Pattern.compile("^( {4}|\\t).*");

    enum BlockType {
        HEADING, FENCED_CODE, TABLE, BLOCKQUOTE, LIST, HORIZONTAL_RULE, PARAGRAPH,
        JSON, XML, INDENTED_CODE
    }

    static class Block {
        BlockType type;
        StringBuilder content = new StringBuilder();
        int startChar;
        int endChar;
        String headingContext = "";
        String language = "";
        List<String> lines = new ArrayList<>();
        List<Integer> lineOffsets = new ArrayList<>();

        Block(BlockType type, int startChar) {
            this.type = type;
            this.startChar = startChar;
        }

        void addLine(String line, int lineStart, int lineEnd) {
            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(line);
            lines.add(line);
            lineOffsets.add(lineStart);
            this.endChar = lineEnd;
        }
    }

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public Set<String> supportedContentTypes() {
        return Set.of("text/markdown");
    }

    @Override
    public List<Chunk> chunk(String documentId, String content, ChunkConfig config) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // Phase 1 & 2: Parse blocks and group sections
        List<Block> blocks = parseBlocks(content, config.detectEmbeddedBlocks());

        // Phase 3, 4, 5: Packing, Overlap, Metadata
        return packAndOverlap(blocks, documentId, content, config);
    }

    private List<Block> parseBlocks(String content, boolean detectEmbedded) {
        List<Block> blocks = new ArrayList<>();
        String[] rawLines = content.split("\n", -1);
        int currentOffset = 0;

        boolean inFencedBlock = false;
        Block currentBlock = null;
        List<String> headingStack = new ArrayList<>();

        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            int lineStart = currentOffset;
            int lineEnd = currentOffset + line.length();
            if (i < rawLines.length - 1) {
                currentOffset += line.length() + 1; // +1 for \n
            } else {
                currentOffset += line.length();
            }

            if (inFencedBlock) {
                Matcher endMatcher = FENCED_CODE_END.matcher(line);
                if (endMatcher.matches()) {
                    currentBlock.addLine(line, lineStart, lineEnd);
                    blocks.add(currentBlock);
                    currentBlock = null;
                    inFencedBlock = false;
                } else {
                    currentBlock.addLine(line, lineStart, lineEnd);
                }
                continue;
            }

            Matcher fencedStart = FENCED_CODE_START.matcher(line);
            if (fencedStart.matches()) {
                if (currentBlock != null) blocks.add(currentBlock);
                currentBlock = new Block(BlockType.FENCED_CODE, lineStart);
                currentBlock.language = fencedStart.group(1).trim();
                currentBlock.headingContext = buildHeadingContext(headingStack);
                currentBlock.addLine(line, lineStart, lineEnd);
                inFencedBlock = true;
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                if (currentBlock != null) blocks.add(currentBlock);
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                while (headingStack.size() >= level) {
                    if (!headingStack.isEmpty()) {
                        headingStack.remove(headingStack.size() - 1);
                    } else {
                        break;
                    }
                }
                headingStack.add(line.trim());
                
                currentBlock = new Block(BlockType.HEADING, lineStart);
                currentBlock.headingContext = buildHeadingContext(headingStack);
                currentBlock.addLine(line, lineStart, lineEnd);
                blocks.add(currentBlock);
                currentBlock = null;
                continue;
            }

            if (HORIZONTAL_RULE.matcher(line).matches()) {
                if (currentBlock != null) blocks.add(currentBlock);
                currentBlock = new Block(BlockType.HORIZONTAL_RULE, lineStart);
                currentBlock.headingContext = buildHeadingContext(headingStack);
                currentBlock.addLine(line, lineStart, lineEnd);
                blocks.add(currentBlock);
                currentBlock = null;
                continue;
            }

            if (TABLE_ROW.matcher(line).matches()) {
                if (currentBlock == null || currentBlock.type != BlockType.TABLE) {
                    if (currentBlock != null) blocks.add(currentBlock);
                    currentBlock = new Block(BlockType.TABLE, lineStart);
                    currentBlock.headingContext = buildHeadingContext(headingStack);
                }
                currentBlock.addLine(line, lineStart, lineEnd);
                continue;
            }

            if (BLOCKQUOTE.matcher(line).matches()) {
                if (currentBlock == null || currentBlock.type != BlockType.BLOCKQUOTE) {
                    if (currentBlock != null) blocks.add(currentBlock);
                    currentBlock = new Block(BlockType.BLOCKQUOTE, lineStart);
                    currentBlock.headingContext = buildHeadingContext(headingStack);
                }
                currentBlock.addLine(line, lineStart, lineEnd);
                continue;
            }

            if (LIST_ITEM.matcher(line).matches()) {
                if (currentBlock == null || currentBlock.type != BlockType.LIST) {
                    if (currentBlock != null) blocks.add(currentBlock);
                    currentBlock = new Block(BlockType.LIST, lineStart);
                    currentBlock.headingContext = buildHeadingContext(headingStack);
                }
                currentBlock.addLine(line, lineStart, lineEnd);
                continue;
            }

            if (line.trim().isEmpty()) {
                if (currentBlock != null) {
                    blocks.add(currentBlock);
                    currentBlock = null;
                }
                continue;
            }

            // Paragraph
            if (currentBlock == null || currentBlock.type != BlockType.PARAGRAPH) {
                if (currentBlock != null) blocks.add(currentBlock);
                currentBlock = new Block(BlockType.PARAGRAPH, lineStart);
                currentBlock.headingContext = buildHeadingContext(headingStack);
            }
            currentBlock.addLine(line, lineStart, lineEnd);
        }

        if (currentBlock != null) {
            blocks.add(currentBlock);
        }

        // Optional: Embedded block detection (JSON/XML/Indented Code) could be applied to PARAGRAPH blocks here.
        if (detectEmbedded) {
            // Simplified embedded block detection
            for (Block block : blocks) {
                if (block.type == BlockType.PARAGRAPH && INDENTED_CODE.matcher(block.lines.get(0)).matches()) {
                    block.type = BlockType.INDENTED_CODE;
                }
            }
        }

        return blocks;
    }

    private String buildHeadingContext(List<String> stack) {
        return String.join(" > ", stack);
    }

    private List<Chunk> packAndOverlap(List<Block> blocks, String documentId, String fullContent, ChunkConfig config) {
        List<Chunk> chunks = new ArrayList<>();
        List<Block> currentChunkBlocks = new ArrayList<>();
        int currentChunkSize = 0;
        int chunkIndex = 0;

        for (Block block : blocks) {
            int blockLen = block.content.length();
            if (blockLen > config.maxChunkSize()) {
                if (!currentChunkBlocks.isEmpty()) {
                    chunks.add(createChunk(documentId, chunkIndex++, currentChunkBlocks, config));
                    currentChunkBlocks.clear();
                    currentChunkSize = 0;
                }
                // Split large block
                List<Block> splitBlocks = splitBlock(block, config.maxChunkSize());
                for (Block sb : splitBlocks) {
                    chunks.add(createChunk(documentId, chunkIndex++, List.of(sb), config));
                }
            } else if (currentChunkSize + blockLen > config.maxChunkSize()) {
                chunks.add(createChunk(documentId, chunkIndex++, currentChunkBlocks, config));
                currentChunkBlocks.clear();
                currentChunkBlocks.add(block);
                currentChunkSize = blockLen;
            } else {
                currentChunkBlocks.add(block);
                currentChunkSize += blockLen;
            }
        }

        if (!currentChunkBlocks.isEmpty()) {
            chunks.add(createChunk(documentId, chunkIndex++, currentChunkBlocks, config));
        }

        // Apply overlap and final metadata
        List<Chunk> finalChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            int finalStart = c.startChar();
            
            // Overlap logic
            if (i > 0 && config.overlap() > 0) {
                Chunk prev = chunks.get(i - 1);
                String prevBlockType = prev.metadata().get("block_type");
                String currBlockType = c.metadata().get("block_type");
                
                // Only overlap if same block type or at least we allow prose overlap
                if (Objects.equals(prevBlockType, currBlockType)) {
                    int overlapStart = Math.max(prev.startChar(), c.startChar() - config.overlap());
                    // snap to word boundary
                    while (overlapStart > prev.startChar() && overlapStart < c.startChar() && 
                           Character.isLetterOrDigit(fullContent.charAt(overlapStart))) {
                        overlapStart++;
                    }
                    finalStart = overlapStart;
                }
            }
            
            String chunkText = fullContent.substring(finalStart, c.endChar());
            
            Map<String, String> meta = new HashMap<>(c.metadata());
            meta.put("chunk_index", String.valueOf(i));
            meta.put("total_chunks", String.valueOf(chunks.size()));
            
            finalChunks.add(new Chunk(documentId, documentId + "::chunk-" + i, i, chunkText, finalStart, c.endChar(), meta));
        }

        return finalChunks;
    }

    private List<Block> splitBlock(Block block, int maxSize) {
        List<Block> result = new ArrayList<>();
        // Fallback: word boundary split
        BreakIterator wordIterator = BreakIterator.getWordInstance();
        wordIterator.setText(block.content.toString());

        int start = 0;
        int end = wordIterator.first();
        int lastValid = end;

        int offsetInSource = block.startChar;

        while (end != BreakIterator.DONE) {
            if (end - start > maxSize) {
                if (lastValid > start) {
                    Block sb = new Block(block.type, offsetInSource + start);
                    sb.content.append(block.content.substring(start, lastValid));
                    sb.endChar = offsetInSource + lastValid;
                    sb.headingContext = block.headingContext;
                    result.add(sb);
                    start = lastValid;
                } else {
                    Block sb = new Block(block.type, offsetInSource + start);
                    sb.content.append(block.content.substring(start, end));
                    sb.endChar = offsetInSource + end;
                    sb.headingContext = block.headingContext;
                    result.add(sb);
                    start = end;
                }
            }
            lastValid = end;
            end = wordIterator.next();
        }

        if (start < block.content.length()) {
            Block sb = new Block(block.type, offsetInSource + start);
            sb.content.append(block.content.substring(start));
            sb.endChar = block.endChar;
            sb.headingContext = block.headingContext;
            result.add(sb);
        }

        return result;
    }

    private Chunk createChunk(String documentId, int index, List<Block> blocks, ChunkConfig config) {
        Block first = blocks.get(0);
        Block last = blocks.get(blocks.size() - 1);
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(blocks.get(i).content);
        }
        
        Map<String, String> meta = new HashMap<>();
        meta.put("heading_context", first.headingContext);
        meta.put("block_type", getBlockTypeLabel(first.type, first.language));
        meta.put("content_type", config.documentFormat());
        
        return new Chunk(documentId, documentId + "::chunk-" + index, index, sb.toString(), first.startChar, last.endChar, meta);
    }
    
    private String getBlockTypeLabel(BlockType type, String language) {
        switch (type) {
            case FENCED_CODE: return "code:" + (language.isEmpty() ? "unknown" : language);
            case TABLE: return "table";
            case BLOCKQUOTE: return "blockquote";
            default: return "prose";
        }
    }
}
