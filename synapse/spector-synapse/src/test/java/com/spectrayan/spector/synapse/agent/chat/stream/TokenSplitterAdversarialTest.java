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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial empirical test suite for {@link TokenSplitter} stream isolation,
 * boundary splitting, false tag prefixes, unclosed thinking blocks, and raw tool JSON suppression.
 */
@DisplayName("TokenSplitter Adversarial & Empirical Challenge Suite")
class TokenSplitterAdversarialTest {

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

    // ═══════════════════════════════════════════════════════════════
    // Category 1: Split Tags Across Chunk Boundaries
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Split Tags Across Chunk Boundaries")
    class SplitTagsAcrossChunkBoundaries {

        @Test
        @DisplayName("Case 1.1: Open tag split <th + ink>")
        void testOpenTagSplit_th_ink() {
            splitter.onTextChunk("<th");
            splitter.onTextChunk("ink>Internal reasoning</think>Visible answer");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Internal reasoning");
            assertThat(tokenOutput.toString()).isEqualTo("Visible answer");
            assertThat(tokenOutput.toString()).doesNotContain("<think>", "</think>", "Internal reasoning");
        }

        @Test
        @DisplayName("Case 1.2: Close tag split </th + ink>")
        void testCloseTagSplit_th_ink() {
            splitter.onTextChunk("<think>Internal reasoning</th");
            splitter.onTextChunk("ink>Visible answer");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Internal reasoning");
            assertThat(tokenOutput.toString()).isEqualTo("Visible answer");
            assertThat(thinkingOutput.toString()).doesNotContain("</th", "</think>");
            assertThat(tokenOutput.toString()).doesNotContain("Internal reasoning", "<think>", "</think>");
        }

        @Test
        @DisplayName("Case 1.3: Open tag split 1 char each (<, t, h, i, n, k, >)")
        void testOpenTagSplit_1char_each() {
            for (char c : "<think>".toCharArray()) {
                splitter.onTextChunk(String.valueOf(c));
            }
            splitter.onTextChunk("CoT content</think>Visible");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("CoT content");
            assertThat(tokenOutput.toString()).isEqualTo("Visible");
            assertThat(tokenOutput.toString()).doesNotContain("CoT content", "<think>");
        }

        @Test
        @DisplayName("Case 1.4: Close tag split 1 char each (<, /, t, h, i, n, k, >)")
        void testCloseTagSplit_1char_each() {
            splitter.onTextChunk("<think>CoT content");
            for (char c : "</think>".toCharArray()) {
                splitter.onTextChunk(String.valueOf(c));
            }
            splitter.onTextChunk("Visible");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("CoT content");
            assertThat(tokenOutput.toString()).isEqualTo("Visible");
            assertThat(thinkingOutput.toString()).doesNotContain("</think>");
            assertThat(tokenOutput.toString()).doesNotContain("CoT content");
        }

        @Test
        @DisplayName("Case 1.5: Tag split at boundary: <think + > and </think + >")
        void testTagSplitAtLastBracket() {
            splitter.onTextChunk("<think");
            splitter.onTextChunk(">CoT text</think");
            splitter.onTextChunk(">Answer text");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("CoT text");
            assertThat(tokenOutput.toString()).isEqualTo("Answer text");
            assertThat(tokenOutput.toString()).doesNotContain("CoT text");
        }

        @Test
        @DisplayName("Case 1.6: Split tags with surrounding visible text")
        void testSplitTagsWithSurroundingVisibleText() {
            splitter.onTextChunk("Prefix text <th");
            splitter.onTextChunk("ink>CoT trace</th");
            splitter.onTextChunk("ink> Suffix text");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("CoT trace");
            assertThat(tokenOutput.toString()).isEqualTo("Prefix text  Suffix text");
            assertThat(tokenOutput.toString()).doesNotContain("CoT trace", "<think>", "</think>");
        }

        @Test
        @DisplayName("Case 1.7: Multiple split tags in sequence: A<th + ink>1</th + ink>B<th + ink>2</th + ink>C")
        void testMultipleSplitTagsInSeries() {
            splitter.onTextChunk("A<th");
            splitter.onTextChunk("ink>Thought 1</th");
            splitter.onTextChunk("ink>B<th");
            splitter.onTextChunk("ink>Thought 2</th");
            splitter.onTextChunk("ink>C");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Thought 1Thought 2");
            assertThat(tokenOutput.toString()).isEqualTo("ABC");
            assertThat(tokenOutput.toString()).doesNotContain("Thought 1", "Thought 2");
        }

        @Test
        @DisplayName("Case 1.8: Exhaustive split points for <think>")
        void testAllPossibleSplitPointsForOpenTag() {
            String tag = "<think>";
            for (int splitIdx = 1; splitIdx < tag.length(); splitIdx++) {
                String chunk1 = tag.substring(0, splitIdx);
                String chunk2 = tag.substring(splitIdx) + "Reasoning " + splitIdx + "</think>Answer " + splitIdx;

                StringBuilder thOut = new StringBuilder();
                StringBuilder tokOut = new StringBuilder();
                TokenSplitter sp = new TokenSplitter((t, ms) -> thOut.append(t), tokOut::append);

                sp.onTextChunk(chunk1);
                sp.onTextChunk(chunk2);
                sp.flush();

                assertThat(thOut.toString())
                        .as("Thinking at split point " + splitIdx)
                        .isEqualTo("Reasoning " + splitIdx);
                assertThat(tokOut.toString())
                        .as("Token at split point " + splitIdx)
                        .isEqualTo("Answer " + splitIdx);
            }
        }

        @Test
        @DisplayName("Case 1.9: Exhaustive split points for </think>")
        void testAllPossibleSplitPointsForCloseTag() {
            String tag = "</think>";
            for (int splitIdx = 1; splitIdx < tag.length(); splitIdx++) {
                String prefix = "<think>Reasoning " + splitIdx;
                String chunk1 = prefix + tag.substring(0, splitIdx);
                String chunk2 = tag.substring(splitIdx) + "Answer " + splitIdx;

                StringBuilder thOut = new StringBuilder();
                StringBuilder tokOut = new StringBuilder();
                TokenSplitter sp = new TokenSplitter((t, ms) -> thOut.append(t), tokOut::append);

                sp.onTextChunk(chunk1);
                sp.onTextChunk(chunk2);
                sp.flush();

                assertThat(thOut.toString())
                        .as("Thinking at close split point " + splitIdx)
                        .isEqualTo("Reasoning " + splitIdx);
                assertThat(tokOut.toString())
                        .as("Token at close split point " + splitIdx)
                        .isEqualTo("Answer " + splitIdx);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Category 2: False Tag Prefixes & Divergence
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. False Tag Prefixes & Divergence")
    class FalseTagPrefixesAndDivergence {

        @Test
        @DisplayName("Case 2.1: Double open bracket <<think>")
        void testDoubleOpenBracket() {
            splitter.onTextChunk("<<think>Internal thought</think>Visible answer");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("<Visible answer");
            assertThat(thinkingOutput.toString()).isEqualTo("Internal thought");
            assertThat(tokenOutput.toString()).doesNotContain("Internal thought");
        }

        @Test
        @DisplayName("Case 2.2: Triple open bracket <<<think>")
        void testTripleOpenBracket() {
            splitter.onTextChunk("<<<think>Thought</think>Done");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("<<Done");
            assertThat(thinkingOutput.toString()).isEqualTo("Thought");
        }

        @Test
        @DisplayName("Case 2.3: Math expression with < and > (5 < 10)")
        void testMathExpressionLessThan() {
            splitter.onTextChunk("Condition: 5 < 10 and 10 > 5 is true.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Condition: 5 < 10 and 10 > 5 is true.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.4: Math expression split across chunk at < boundary")
        void testMathExpressionSplitAcrossChunk() {
            splitter.onTextChunk("Result: 5 <");
            splitter.onTextChunk(" 10 is correct.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Result: 5 < 10 is correct.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.5: Chained comparisons x < y < z")
        void testChainedComparisons() {
            splitter.onTextChunk("For any valid sort: a < b < c < d.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("For any valid sort: a < b < c < d.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.6: False tag <thinking>")
        void testFalseTagThinking() {
            splitter.onTextChunk("We should start <thinking> about architecture.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("We should start <thinking> about architecture.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.7: False tag <thinking> split across chunks: <think + ing>")
        void testFalseTagThinkingSplitAcrossChunks() {
            splitter.onTextChunk("Process: <think");
            splitter.onTextChunk("ing> completed.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Process: <thinking> completed.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.8: False close tag </thought> in visible text")
        void testFalseCloseTagThoughtInVisibleText() {
            splitter.onTextChunk("End of discussion </thought> point.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("End of discussion </thought> point.");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 2.9: False close tag </thought> inside thinking block")
        void testFalseCloseTagThoughtInsideThinking() {
            splitter.onTextChunk("<think>Here is a pseudo tag </thought> that should not exit thinking</think>Visible");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Here is a pseudo tag </thought> that should not exit thinking");
            assertThat(tokenOutput.toString()).isEqualTo("Visible");
            assertThat(tokenOutput.toString()).doesNotContain("pseudo tag", "</thought>");
        }

        @Test
        @DisplayName("Case 2.10: False close tag </thought> split across chunks inside thinking")
        void testFalseCloseTagThoughtSplitInsideThinking() {
            splitter.onTextChunk("<think>Inner reasoning </th");
            splitter.onTextChunk("ought> continuing reasoning</think>Answer");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Inner reasoning </thought> continuing reasoning");
            assertThat(tokenOutput.toString()).isEqualTo("Answer");
            assertThat(tokenOutput.toString()).doesNotContain("Inner reasoning");
        }

        @Test
        @DisplayName("Case 2.11: Prefix collision immediately preceding real tag: <th<think>")
        void testPrefixCollisionPrecedingRealTag() {
            splitter.onTextChunk("<th<think>Real thought</think>Real answer");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("<thReal answer");
            assertThat(thinkingOutput.toString()).isEqualTo("Real thought");
        }

        @Test
        @DisplayName("Case 2.12: Close prefix collision inside thinking: </th</think>")
        void testClosePrefixCollisionInsideThinking() {
            splitter.onTextChunk("<think>CoT thought </th</think>Answer");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("CoT thought </th");
            assertThat(tokenOutput.toString()).isEqualTo("Answer");
            assertThat(tokenOutput.toString()).doesNotContain("CoT thought");
        }

        @Test
        @DisplayName("Case 2.13: Nested false tags <thinking> and <thought> inside thinking")
        void testNestedFalseTagsInsideThinking() {
            splitter.onTextChunk("<think>Considering <thinking> and <thought> patterns</think>Outcome");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Considering <thinking> and <thought> patterns");
            assertThat(tokenOutput.toString()).isEqualTo("Outcome");
        }

        @Test
        @DisplayName("Case 2.14: Multiple repeated open brackets <<<<<<think>")
        void testMultipleRepeatedOpenBrackets() {
            splitter.onTextChunk("<<<<<<think>Thought</think>End");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("<<<<<End");
            assertThat(thinkingOutput.toString()).isEqualTo("Thought");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Category 3: Unclosed <think> Blocks at EOF
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Unclosed <think> Blocks at EOF")
    class UnclosedThinkingBlocksAtEof {

        @Test
        @DisplayName("Case 3.1: Unclosed <think> block routes all text to thinking, zero to visible tokens")
        void testUnclosedThinkingRoutesAllToThinking() {
            splitter.onTextChunk("<think>This is a runaway reasoning trace that never closes");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("This is a runaway reasoning trace that never closes");
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.2: Unclosed <think> block ending with partial close tag </th at EOF")
        void testUnclosedThinkingEndingWithPartialCloseTag() {
            splitter.onTextChunk("<think>Reasoning interrupted right here: </th");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Reasoning interrupted right here: </th");
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.3: Unclosed <think> block ending with partial </ at EOF")
        void testUnclosedThinkingEndingWithPartialSlash() {
            splitter.onTextChunk("<think>Reasoning ending with partial </");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Reasoning ending with partial </");
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.4: Unclosed <think> block ending with < at EOF")
        void testUnclosedThinkingEndingWithBracket() {
            splitter.onTextChunk("<think>Reasoning ending with bracket <");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Reasoning ending with bracket <");
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.5: Bare <think> at EOF emits zero tokens and zero thinking")
        void testBareOpenTagAtEof() {
            splitter.onTextChunk("<think>");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEmpty();
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.6: Unclosed nested thinking blocks at EOF")
        void testUnclosedNestedThinkingBlocksAtEof() {
            splitter.onTextChunk("<think>Outer thought <think>Inner thought without closing");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Outer thought Inner thought without closing");
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 3.7: Interleaved thinking where second block is unclosed")
        void testInterleavedThinkingWithSecondBlockUnclosed() {
            splitter.onTextChunk("<think>Thought 1</think>Answer 1. <think>Thought 2 unclosed");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Thought 1Thought 2 unclosed");
            assertThat(tokenOutput.toString()).isEqualTo("Answer 1. ");
            assertThat(tokenOutput.toString()).doesNotContain("Thought 1", "Thought 2");
        }

        @Test
        @DisplayName("Case 3.8: Invariant check: tokenOutput is strictly empty when stream ends inside thinking")
        void testInvariantTokenOutputEmptyWhenInterruptedInThinking() {
            String[] chunks = {
                    "<think>",
                    "Analyzing query...",
                    "Checking memory graph...",
                    "Formulating hypothesis...",
                    "Evaluating constraints...",
                    "Abrupt stream interruption"
            };

            for (String chunk : chunks) {
                splitter.onTextChunk(chunk);
            }
            splitter.flush();

            assertThat(tokenOutput.toString()).isEmpty();
            assertThat(thinkingOutput.toString()).contains("Analyzing query...", "Abrupt stream interruption");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Category 4: Raw Tool JSON Dumps & State Isolation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Raw Tool JSON Dumps & Stream Isolation")
    class RawToolJsonDumpsAndStreamIsolation {

        @Test
        @DisplayName("Case 4.1: Single tool call suppresses raw JSON dump completely")
        void testToolCallSuppressesSingleJsonDump() {
            splitter.onTextChunk("Let me search for that. ");
            splitter.onToolCall();
            splitter.onTextChunk("{\"callId\":\"call_abc123\",\"name\":\"memory_recall\",\"arguments\":{\"query\":\"test\",\"limit\":5}}");
            splitter.onToolResume();
            splitter.onTextChunk("I found 5 results.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Let me search for that. I found 5 results.");
            assertThat(tokenOutput.toString()).doesNotContain("call_abc123", "memory_recall", "query", "arguments");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 4.2: Tool call suppresses raw JSON split across 50 micro-chunks")
        void testToolCallSuppresses50MicroChunks() {
            splitter.onTextChunk("Running analysis... ");
            splitter.onToolCall();

            String jsonPayload = "{\"tool\":\"spector_inspect\",\"status\":\"executing\",\"parameters\":{\"depth\":10,\"filter\":\"salience>0.8\"}}";
            for (int i = 0; i < jsonPayload.length(); i += 2) {
                int end = Math.min(i + 2, jsonPayload.length());
                splitter.onTextChunk(jsonPayload.substring(i, end));
            }

            splitter.onToolResume();
            splitter.onTextChunk("Analysis complete.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Running analysis... Analysis complete.");
            assertThat(tokenOutput.toString()).doesNotContain("spector_inspect", "parameters", "salience");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 4.3: Tool call suppresses massive 64 KiB JSON dump")
        void testToolCallSuppressesMassive64KiBJsonDump() {
            splitter.onTextChunk("Fetching large dataset... ");
            splitter.onToolCall();

            StringBuilder massiveJson = new StringBuilder();
            massiveJson.append("{\"records\":[");
            for (int i = 0; i < 500; i++) {
                if (i > 0) massiveJson.append(",");
                massiveJson.append("{\"id\":").append(i).append(",\"data\":\"payload_").append(i).append("\"}");
            }
            massiveJson.append("]}");

            splitter.onTextChunk(massiveJson.toString());
            splitter.onToolResume();
            splitter.onTextChunk("Dataset processed successfully.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Fetching large dataset... Dataset processed successfully.");
            assertThat(tokenOutput.toString()).doesNotContain("payload_0", "payload_499", "records");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 4.4: Tool call interleaved between thinking block and answer tokens")
        void testToolCallInterleavedBetweenThinkingAndAnswer() {
            splitter.onTextChunk("<think>Step 1: Decide to call tool spector_remember.</think>");
            splitter.onToolCall();
            splitter.onTextChunk("{\"name\":\"spector_remember\",\"arguments\":{\"fact\":\"User preference recorded\"}}");
            splitter.onToolResume();
            splitter.onTextChunk("I have saved your preference.");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("Step 1: Decide to call tool spector_remember.");
            assertThat(tokenOutput.toString()).isEqualTo("I have saved your preference.");
            assertThat(tokenOutput.toString()).doesNotContain("Step 1", "spector_remember", "preference recorded");
        }

        @Test
        @DisplayName("Case 4.5: Multiple sequential tool calls with intervening text")
        void testMultipleSequentialToolCalls() {
            splitter.onTextChunk("First query: ");
            splitter.onToolCall();
            splitter.onTextChunk("{\"tool\":\"query1\"}");
            splitter.onToolResume();
            splitter.onTextChunk("Got query 1. Second query: ");
            splitter.onToolCall();
            splitter.onTextChunk("{\"tool\":\"query2\"}");
            splitter.onToolResume();
            splitter.onTextChunk("All queries resolved.");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("First query: Got query 1. Second query: All queries resolved.");
            assertThat(tokenOutput.toString()).doesNotContain("query1", "query2");
        }

        @Test
        @DisplayName("Case 4.6: Flush during TOOL_PENDING does not leak tool JSON to tokens")
        void testFlushDuringToolPending() {
            splitter.onTextChunk("Starting tool... ");
            splitter.onToolCall();
            splitter.onTextChunk("{\"partial_tool_json\":true");
            splitter.flush();

            assertThat(tokenOutput.toString()).isEqualTo("Starting tool... ");
            assertThat(tokenOutput.toString()).doesNotContain("partial_tool_json");
            assertThat(thinkingOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 4.7: HARDENED: Tool call initiated inside unclosed <think> block preserves thinking state and prevents reasoning leak")
        void testToolCallInsideUnclosedThinkingBlock() {
            splitter.onTextChunk("<think>Internal reasoning step 1 before tool call");
            splitter.onToolCall();
            splitter.onTextChunk("{\"name\":\"calc\",\"arguments\":{}}");
            splitter.onToolResume();
            // On tool resume, stateBeforeTool restores State.THINKING:
            splitter.onTextChunk("Step 2 after tool call</think>Visible answer");
            splitter.flush();

            // Verified hardening: onToolResume() restores State.THINKING, correctly routing post-tool
            // reasoning to thinkingOutput and visible answer to tokenOutput without leakage.
            assertThat(thinkingOutput.toString()).isEqualTo("Internal reasoning step 1 before tool callStep 2 after tool call");
            assertThat(tokenOutput.toString()).isEqualTo("Visible answer");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Category 5: Property-Based & Fuzzing Stream Decomposition
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Fuzzing & Property-Based Chunk Invariance")
    class FuzzingAndPropertyBasedChunkInvariance {

        private static final String CANONICAL_TEST_STREAM =
                "Preamble text. " +
                "<think>Reasoning step 1: Check 5 < 10 && 10 > 5. " +
                "<think>Nested: sub-evaluation of <thinking> concept.</think> " +
                "Reasoning step 2: false tag </thought> inside CoT. " +
                "<<think>edge case</think>" +
                "</think>" +
                "Conclusion: 5 < 10 is true and <thinking> was evaluated.";

        private String oracleThinking;
        private String oracleToken;

        @BeforeEach
        void computeOracle() {
            StringBuilder th = new StringBuilder();
            StringBuilder tok = new StringBuilder();
            TokenSplitter sp = new TokenSplitter((t, ms) -> th.append(t), tok::append);
            sp.onTextChunk(CANONICAL_TEST_STREAM);
            sp.flush();
            oracleThinking = th.toString();
            oracleToken = tok.toString();
        }

        @Test
        @DisplayName("Case 5.1: 1-character micro-chunk streaming matches oracle exactly")
        void testOneCharMicroChunksMatchesOracle() {
            StringBuilder th = new StringBuilder();
            StringBuilder tok = new StringBuilder();
            TokenSplitter sp = new TokenSplitter((t, ms) -> th.append(t), tok::append);

            for (char c : CANONICAL_TEST_STREAM.toCharArray()) {
                sp.onTextChunk(String.valueOf(c));
            }
            sp.flush();

            assertThat(th.toString()).isEqualTo(oracleThinking);
            assertThat(tok.toString()).isEqualTo(oracleToken);
        }

        @Test
        @DisplayName("Case 5.2: 2, 3, 4, 7, 8 byte fixed-width chunk sizes match oracle exactly")
        void testFixedWidthChunkSizesMatchOracle() {
            int[] chunkSizes = {2, 3, 4, 5, 7, 8, 11, 13, 16};

            for (int size : chunkSizes) {
                StringBuilder th = new StringBuilder();
                StringBuilder tok = new StringBuilder();
                TokenSplitter sp = new TokenSplitter((t, ms) -> th.append(t), tok::append);

                for (int i = 0; i < CANONICAL_TEST_STREAM.length(); i += size) {
                    int end = Math.min(i + size, CANONICAL_TEST_STREAM.length());
                    sp.onTextChunk(CANONICAL_TEST_STREAM.substring(i, end));
                }
                sp.flush();

                assertThat(th.toString())
                        .as("Thinking mismatch at chunk size " + size)
                        .isEqualTo(oracleThinking);
                assertThat(tok.toString())
                        .as("Token mismatch at chunk size " + size)
                        .isEqualTo(oracleToken);
            }
        }

        @Test
        @DisplayName("Case 5.3: 100 random partitionings match oracle byte-for-byte with zero CoT leak")
        void test100RandomPartitioningsMatchOracle() {
            Random rng = new Random(42);

            for (int run = 0; run < 100; run++) {
                StringBuilder th = new StringBuilder();
                StringBuilder tok = new StringBuilder();
                TokenSplitter sp = new TokenSplitter((t, ms) -> th.append(t), tok::append);

                int idx = 0;
                while (idx < CANONICAL_TEST_STREAM.length()) {
                    int chunkSize = 1 + rng.nextInt(12);
                    int end = Math.min(idx + chunkSize, CANONICAL_TEST_STREAM.length());
                    sp.onTextChunk(CANONICAL_TEST_STREAM.substring(idx, end));
                    idx = end;
                }
                sp.flush();

                assertThat(th.toString())
                        .as("Thinking mismatch in random run " + run)
                        .isEqualTo(oracleThinking);
                assertThat(tok.toString())
                        .as("Token mismatch in random run " + run)
                        .isEqualTo(oracleToken);

                // Invariant: zero CoT leakage
                assertThat(tok.toString()).doesNotContain("Reasoning step 1", "Nested: sub-evaluation", "Reasoning step 2");
                assertThat(tok.toString()).doesNotContain("<think>", "</think>");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Category 6: Stress, Unicode & Deep Nesting
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. Stress, Unicode & Deep Nesting")
    class StressUnicodeAndDeepNesting {

        @Test
        @DisplayName("Case 6.1: 5-level deep nested thinking blocks correctly tracked and suppressed")
        void testFiveLevelDeepNestedThinking() {
            splitter.onTextChunk("<think>L1 <think>L2 <think>L3 <think>L4 <think>L5</think> L4</think> L3</think> L2</think> L1</think>Done");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("L1 L2 L3 L4 L5 L4 L3 L2 L1");
            assertThat(tokenOutput.toString()).isEqualTo("Done");
            assertThat(tokenOutput.toString()).doesNotContain("L1", "L2", "L3", "L4", "L5");
        }

        @Test
        @DisplayName("Case 6.2: Unicode and Emojis adjacent to tags and inside thinking")
        void testUnicodeAndEmojisAdjacentToTags() {
            splitter.onTextChunk("🧠<think>💡 Deep thought: 日本語 and 中文 🚀</think>✨ Visible response 🎉");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEqualTo("💡 Deep thought: 日本語 and 中文 🚀");
            assertThat(tokenOutput.toString()).isEqualTo("🧠✨ Visible response 🎉");
        }

        @Test
        @DisplayName("Case 6.3: High-volume throughput: 100,000 characters stream processed in sub-100ms")
        void testHighVolumeThroughput() {
            StringBuilder largeStream = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largeStream.append("<think>Thinking cycle ").append(i).append("</think>Token ").append(i).append(" ");
            }

            long start = System.nanoTime();
            // Process in realistic 20-character chunks
            String fullText = largeStream.toString();
            for (int i = 0; i < fullText.length(); i += 20) {
                int end = Math.min(i + 20, fullText.length());
                splitter.onTextChunk(fullText.substring(i, end));
            }
            splitter.flush();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertThat(elapsedMs).as("Processing 100k chars should complete under 1000ms").isLessThan(1000L);
            assertThat(tokenOutput.toString()).contains("Token 0 ", "Token 999 ");
            assertThat(tokenOutput.toString()).doesNotContain("<think>", "</think>", "Thinking cycle");
            assertThat(thinkingOutput.toString()).contains("Thinking cycle 0", "Thinking cycle 999");
        }

        @Test
        @DisplayName("Case 6.4: Null and empty string boundary safety")
        void testNullAndEmptyHandling() {
            splitter.onTextChunk(null);
            splitter.onTextChunk("");
            splitter.onNativeThinking(null);
            splitter.onNativeThinking("");
            splitter.flush();

            assertThat(thinkingOutput.toString()).isEmpty();
            assertThat(tokenOutput.toString()).isEmpty();
        }

        @Test
        @DisplayName("Case 6.5: Mixed native thinking and tag thinking interleaved across 50 iterations")
        void testInterleavedNativeAndTagThinkingFiftyRounds() {
            for (int i = 0; i < 50; i++) {
                splitter.onNativeThinking("native_" + i + " ");
                splitter.onTextChunk("<think>tag_" + i + " </think>");
                splitter.onTextChunk("answer_" + i + " ");
            }
            splitter.flush();

            for (int i = 0; i < 50; i++) {
                assertThat(thinkingOutput.toString()).contains("native_" + i, "tag_" + i);
                assertThat(tokenOutput.toString()).contains("answer_" + i);
            }
            assertThat(tokenOutput.toString()).doesNotContain("native_", "tag_", "<think>", "</think>");
        }
    }
}

