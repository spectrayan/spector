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
package com.spectrayan.spector.synapse.agent.chat.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenSplitter State Machine & CoT / Tool Leakage Verification Suite")
class TokenSplitterTest {

    private StringBuilder thinkingOutput;
    private StringBuilder tokenOutput;
    private List<Long> thinkingElapsedRecords;
    private TokenSplitter splitter;

    @BeforeEach
    void setUp() {
        thinkingOutput = new StringBuilder();
        tokenOutput = new StringBuilder();
        thinkingElapsedRecords = new ArrayList<>();

        splitter = new TokenSplitter(
                (text, elapsedMs) -> {
                    thinkingOutput.append(text);
                    thinkingElapsedRecords.add(elapsedMs);
                },
                tokenOutput::append
        );
    }

    // ── Category 1: Split Tags Across Chunk Boundaries ─────────────

    @Test
    @DisplayName("Case 1: Open <think> tag split across two chunks")
    void testSplitOpenTagAcrossTwoChunks() {
        splitter.onTextChunk("<th");
        splitter.onTextChunk("ink>Reasoning trace</think>Visible answer");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("Reasoning trace");
        assertThat(tokenOutput.toString()).isEqualTo("Visible answer");
        assertThat(tokenOutput.toString()).doesNotContain("<think>", "</think>");
    }

    @Test
    @DisplayName("Case 2: Close </think> tag split across two chunks")
    void testSplitCloseTagAcrossTwoChunks() {
        splitter.onTextChunk("<think>Reasoning trace</th");
        splitter.onTextChunk("ink>Visible answer");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("Reasoning trace");
        assertThat(tokenOutput.toString()).isEqualTo("Visible answer");
        assertThat(thinkingOutput.toString()).doesNotContain("</th");
    }

    @Test
    @DisplayName("Case 3: Tags split across multiple tiny chunks")
    void testSplitTagsAcrossMultipleTinyChunks() {
        String[] chunks = {"Hello ", "<", "th", "in", "k>", "step 1", "</", "th", "in", "k>", " world"};
        for (String chunk : chunks) {
            splitter.onTextChunk(chunk);
        }
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("step 1");
        assertThat(tokenOutput.toString()).isEqualTo("Hello  world");
    }

    @Test
    @DisplayName("Case 4: Extreme 1-character micro-chunk streaming")
    void testExtremeSingleCharacterStream() {
        String input = "<think>abc</think>xyz";
        for (char c : input.toCharArray()) {
            splitter.onTextChunk(String.valueOf(c));
        }
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("abc");
        assertThat(tokenOutput.toString()).isEqualTo("xyz");
    }

    // ── Category 2: Partial & False Buffers (Divergence) ───────────

    @Test
    @DisplayName("Case 5: Divergent buffer in math expressions with < and >")
    void testDivergentBufferMathExpression() {
        splitter.onTextChunk("Check: if 5 < 10 && 10 > 5 then valid");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("Check: if 5 < 10 && 10 > 5 then valid");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    @Test
    @DisplayName("Case 6: Divergent buffer in standard HTML/XML tags")
    void testDivergentBufferHtmlTags() {
        splitter.onTextChunk("<table><tr><td>data</td></tr></table>");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("<table><tr><td>data</td></tr></table>");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    @Test
    @DisplayName("Case 7: Divergent buffer with repeated back-to-back '<'")
    void testDivergentBufferRepeatedLessThan() {
        splitter.onTextChunk("<<think>Internal thought</think>Final");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("<Final");
        assertThat(thinkingOutput.toString()).isEqualTo("Internal thought");
    }

    @Test
    @DisplayName("Case 8: Divergent buffer with prefix immediately preceding real tag")
    void testDivergentBufferPrefixThenRealTag() {
        splitter.onTextChunk("<th<think>Thought</think>Answer");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("<thAnswer");
        assertThat(thinkingOutput.toString()).isEqualTo("Thought");
    }

    // ── Category 3: Nested Thinking Tags ───────────────────────────

    @Test
    @DisplayName("Case 9: Nested <think> tags do not prematurely exit on first </think>")
    void testNestedThinkingTags() {
        splitter.onTextChunk("<think>Outer <think>Inner</think> Still outer</think>Final answer");
        splitter.flush();

        assertThat(thinkingOutput.toString()).contains("Outer ", "Inner", " Still outer");
        assertThat(tokenOutput.toString()).isEqualTo("Final answer");
    }

    // ── Category 4: Raw Tokens Only ────────────────────────────────

    @Test
    @DisplayName("Case 10: Plain text with visible tokens only")
    void testDirectVisibleTokensOnly() {
        splitter.onTextChunk("The quick brown ");
        splitter.onTextChunk("fox jumps over ");
        splitter.onTextChunk("the lazy dog.");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("The quick brown fox jumps over the lazy dog.");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    @Test
    @DisplayName("Case 11: Leading newlines and indentation preserved")
    void testLeadingWhitespaceBeforeTokens() {
        splitter.onTextChunk("\n\n  Hello world!");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("\n\n  Hello world!");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    // ── Category 5: Empty Thinking Blocks ──────────────────────────

    @Test
    @DisplayName("Case 12: Empty thinking block emitted immediately")
    void testEmptyThinkingBlockImmediate() {
        splitter.onTextChunk("<think></think>Direct answer");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("Direct answer");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    @Test
    @DisplayName("Case 13: Empty thinking block split across separate chunks")
    void testEmptyThinkingBlockSplitAcrossChunks() {
        splitter.onTextChunk("<think>");
        splitter.onTextChunk("</think>");
        splitter.onTextChunk("Direct answer");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("Direct answer");
        assertThat(thinkingOutput.toString()).isEmpty();
    }

    // ── Category 6: Multi-Chunk Realistic Reasoning ────────────────

    @Test
    @DisplayName("Case 14: Multi-chunk reasoning trace with elapsed time tracking")
    void testMultiChunkReasoningTrace() {
        splitter.onTextChunk("<think>");
        splitter.onTextChunk("Step 1: check facts.\n");
        splitter.onTextChunk("Step 2: formulate hypothesis.\n");
        splitter.onTextChunk("Step 3: verify math.\n");
        splitter.onTextChunk("</think>");
        splitter.onTextChunk("The conclusion is 42.");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo(
                "Step 1: check facts.\nStep 2: formulate hypothesis.\nStep 3: verify math.\n");
        assertThat(tokenOutput.toString()).isEqualTo("The conclusion is 42.");
        assertThat(thinkingElapsedRecords).allMatch(elapsed -> elapsed >= 0);
    }

    // ── Category 7: EOF / Interrupted Stream Protection ────────────

    @Test
    @DisplayName("Case 15: Unclosed <think> tag at EOF never leaks CoT into visible tokens")
    void testUnclosedThinkingAtEof() {
        splitter.onTextChunk("<think>I am reasoning and then stream terminates unexpectedly");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("I am reasoning and then stream terminates unexpectedly");
        assertThat(tokenOutput.toString()).isEmpty();
    }

    @Test
    @DisplayName("Case 16: Unclosed tag buffer in visible text flushed to token on EOF")
    void testUnclosedTagBufferAtEofInVisibleText() {
        splitter.onTextChunk("Expression ending with <");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("Expression ending with <");
    }

    // ── Category 8: Native Reasoning Channels ──────────────────────

    @Test
    @DisplayName("Case 17: Native reasoning channel via onNativeThinking only")
    void testNativeThinkingOnly() {
        splitter.onNativeThinking("Native thought 1; ");
        splitter.onNativeThinking("Native thought 2.");
        splitter.onTextChunk("Visible answer.");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("Native thought 1; Native thought 2.");
        assertThat(tokenOutput.toString()).isEqualTo("Visible answer.");
    }

    @Test
    @DisplayName("Case 18: Native reasoning interleaved with fallback delimiter tags")
    void testNativeThinkingInterleavedWithFallbackTags() {
        splitter.onNativeThinking("Native 1; ");
        splitter.onTextChunk("<think>Fallback 2</think>Answer");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("Native 1; Fallback 2");
        assertThat(tokenOutput.toString()).isEqualTo("Answer");
    }

    // ── Category 9: Tool Call Guard & JSON Suppression ─────────────

    @Test
    @DisplayName("Case 19: Tool call suppresses raw JSON fragments from visible token stream")
    void testToolCallSuppressionAndJsonLeakage() {
        splitter.onTextChunk("Analyzing requirements... ");
        splitter.onToolCall();
        splitter.onTextChunk("{\"callId\":\"call_123\",\"arguments\":{\"query\":\"test\"}}");
        splitter.onToolResume();
        splitter.onTextChunk("Here are the search results.");
        splitter.flush();

        assertThat(tokenOutput.toString()).isEqualTo("Analyzing requirements... Here are the search results.");
        assertThat(tokenOutput.toString()).doesNotContain("call_123", "arguments", "query");
    }

    // ── Category 10: Multi-Stage Interleaved Reasoning ─────────────

    @Test
    @DisplayName("Case 20: Multi-stage reasoning blocks interleaved with visible text")
    void testInterleavedMultiStageThinking() {
        splitter.onTextChunk("<think>Thought A</think>Partial 1. <think>Thought B</think>Partial 2.");
        splitter.flush();

        assertThat(thinkingOutput.toString()).isEqualTo("Thought AThought B");
        assertThat(tokenOutput.toString()).isEqualTo("Partial 1. Partial 2.");
        assertThat(tokenOutput.toString()).doesNotContain("<think>", "</think>");
        assertThat(thinkingOutput.toString()).doesNotContain("<think>", "</think>");
    }
}
