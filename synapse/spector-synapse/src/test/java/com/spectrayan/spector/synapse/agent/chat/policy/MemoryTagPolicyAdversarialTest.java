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
package com.spectrayan.spector.synapse.agent.chat.policy;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemorySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Empirical Adversarial Challenger Test Suite for {@link MemoryTagPolicy}.
 *
 * <p>Exhaustively probes for:
 * <ul>
 *   <li>Unicode homoglyph & lookalike attacks</li>
 *   <li>Mixed-case variations of prohibited tags</li>
 *   <li>Raw JSON injection inside tags and payloads</li>
 *   <li>Chain-of-Thought (CoT) scratchpad smuggling</li>
 *   <li>Delimiter smuggling and regex boundary conditions</li>
 *   <li>Oversized and zero-length boundary payloads</li>
 * </ul>
 * </p>
 */
@DisplayName("MemoryTagPolicy Adversarial Challenger Test Suite")
class MemoryTagPolicyAdversarialTest {

    private MemoryTagPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MemoryTagPolicy();
    }

    // =========================================================================
    // 1. UNICODE HOMOGLYPH & LOOKALIKE ATTACKS
    // =========================================================================

    @Nested
    @DisplayName("Unicode Homoglyph & Lookalike Probes")
    class UnicodeHomoglyphTests {

        @Test
        @DisplayName("Cyrillic lookalikes in namespace prefix are rejected by allowlist")
        void testCyrillicNamespaceLookalikesRejected() {
            // "ѕеѕѕіon" using Cyrillic \u0455, \u0435, \u0456
            String cyrillicSession = "\u0455\u0435\u0455\u0455\u0456\u043En:123";
            assertThatThrownBy(() -> policy.validateTags(List.of(cyrillicSession)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("is not permitted");

            // "рreference" using Cyrillic 'р' (\u0440)
            String cyrillicPref = "\u0440reference:dark";
            assertThatThrownBy(() -> policy.validateTags(List.of(cyrillicPref)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("is not permitted");
        }

        @Test
        @DisplayName("Cyrillic lookalikes in bare tags are rejected by alphanumeric regex")
        void testCyrillicBareTagLookalikesRejected() {
            String cyrillicBare = "\u0455\u0435\u0455\u0455\u0456\u043En";
            assertThatThrownBy(() -> policy.validateTags(List.of(cyrillicBare)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Bare tag must be an allowed namespace or clean alphanumeric token");
        }

        @Test
        @DisplayName("Full-width colon (U+FF1A) does not spoof standard namespace separator")
        void testFullWidthColonRejected() {
            String fullWidthColon = "preference\uFF1Adark";
            assertThatThrownBy(() -> policy.validateTags(List.of(fullWidthColon)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Bare tag must be an allowed namespace");
        }

        @Test
        @DisplayName("Full-width Latin letters are rejected by namespace and bare tag regexes")
        void testFullWidthLatinRejected() {
            // Full-width 'p' \uFF50
            String fullWidthTag = "\uFF50reference:dark";
            assertThatThrownBy(() -> policy.validateTags(List.of(fullWidthTag)))
                    .isInstanceOf(SpectorValidationException.class);
        }

        @Test
        @DisplayName("Zero-width space (U+200B) in namespace or bare tag is rejected")
        void testZeroWidthSpaceRejected() {
            String zwsTag = "preference\u200B:dark";
            assertThatThrownBy(() -> policy.validateTags(List.of(zwsTag)))
                    .isInstanceOf(SpectorValidationException.class);

            String zwsBare = "preference\u200B";
            assertThatThrownBy(() -> policy.validateTags(List.of(zwsBare)))
                    .isInstanceOf(SpectorValidationException.class);
        }
    }

    // =========================================================================
    // 2. MIXED-CASE PROBES
    // =========================================================================

    @Nested
    @DisplayName("Mixed-Case and Case-Insensitivity Probes")
    class MixedCaseTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "sEsSiOn:01J98ABC",
                "SeSsIoN_uuid",
                "Id:01J98ABC",
                "iD:uuid-123",
                "Type:Turn",
                "tYpE:tUrN",
                "RoLe:user",
                "rOlE:ASSISTANT",
                "MoDeL:qwen-2.5",
                "mOdEl:deepseek-r1"
        })
        @DisplayName("All mixed-case variants of denylist tags are strictly rejected")
        void testMixedCaseDenylistRejected(String tag) {
            assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                    .isInstanceOf(SpectorValidationException.class)
                    .satisfies(ex -> assertThat(((SpectorValidationException) ex).errorCode())
                            .isEqualTo(ErrorCode.ARGUMENT_INVALID));
            assertThat(policy.isTagPermitted(tag)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "PREFERENCE:dark_mode",
                "Decision:H2_Database",
                "ArChItEcTuRe:dual_plane",
                "ENTITY:austin",
                "TOPIC:memory_decay"
        })
        @DisplayName("Mixed-case allowed namespaces normalize and pass validation")
        void testMixedCaseAllowedNamespacesAccepted(String tag) {
            assertThatCode(() -> policy.validateTags(List.of(tag))).doesNotThrowAnyException();
            assertThat(policy.isTagPermitted(tag)).isTrue();
        }
    }

    // =========================================================================
    // 3. RAW JSON IN TAGS & DELIMITER INJECTION PROBES
    // =========================================================================

    @Nested
    @DisplayName("Raw JSON in Tags & Injection Probes")
    class JsonAndInjectionTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"session\":\"01J98ABC\"}",
                "{\"callId\":\"c1\",\"name\":\"recall\"}",
                "{\"tag\":\"preference\"}",
                "[\"session:123\"]"
        })
        @DisplayName("Standalone raw JSON objects and arrays as tags are strictly rejected")
        void testStandaloneRawJsonTagRejected(String tag) {
            assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                    .isInstanceOf(SpectorValidationException.class);
            assertThat(policy.isTagPermitted(tag)).isFalse();
        }

        @Test
        @DisplayName("Raw JSON string inside allowed namespace value is strictly rejected")
        void testJsonInsideNamespaceValue() {
            String tagWithJson = "concept:{\"key\":\"val\"}";
            boolean permitted = policy.isTagPermitted(tagWithJson);
            assertThat(permitted).isFalse();
        }

        @Test
        @DisplayName("Comma injection in tag value is strictly rejected")
        void testCommaInjectionInTagValue() {
            String tagWithComma = "concept:math,session:01J98ABC";
            boolean permitted = policy.isTagPermitted(tagWithComma);
            assertThat(permitted).isFalse();
        }
    }

    // =========================================================================
    // 4. CHAIN-OF-THOUGHT (<think>) IN TAGS PROBES
    // =========================================================================

    @Nested
    @DisplayName("Chain-of-Thought in Tags Probes")
    class CotInTagsTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<think>",
                "</think>",
                "<think>reasoning</think>",
                "[internal cot]"
        })
        @DisplayName("Standalone CoT markers as bare tags are strictly rejected")
        void testStandaloneCotTagRejected(String tag) {
            assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                    .isInstanceOf(SpectorValidationException.class);
            assertThat(policy.isTagPermitted(tag)).isFalse();
        }

        @Test
        @DisplayName("CoT markers inside allowed namespace value are strictly rejected")
        void testCotInsideNamespaceValue() {
            String tagWithCot = "concept:<think>cot</think>";
            boolean permitted = policy.isTagPermitted(tagWithCot);
            assertThat(permitted).isFalse();
        }
    }

    // =========================================================================
    // 5. REGEX BOUNDARIES & DELIMITER EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Regex Boundaries & Delimiter Edge Cases")
    class RegexBoundaryTests {

        @Test
        @DisplayName("Hyphenated session tags (session-12345) are strictly rejected")
        void testHyphenatedSessionTagBypass() {
            String hyphenatedSession = "session-01J98ABC";
            boolean permitted = policy.isTagPermitted(hyphenatedSession);
            assertThat(permitted).isFalse();
        }

        @Test
        @DisplayName("Bare 'session' token is strictly rejected")
        void testBareSessionToken() {
            boolean permitted = policy.isTagPermitted("session");
            assertThat(permitted).isFalse();
        }

        @Test
        @DisplayName("Hyphenated 'id-12345' is strictly rejected")
        void testHyphenatedIdBypass() {
            String hyphenatedId = "id-01J98ABC";
            boolean permitted = policy.isTagPermitted(hyphenatedId);
            assertThat(permitted).isFalse();
        }

        @Test
        @DisplayName("Hyphenated operational tags (role-assistant, type-turn, model-gpt4) are strictly rejected")
        void testHyphenatedOperationalTags() {
            assertThat(policy.isTagPermitted("role-assistant")).isFalse();
            assertThat(policy.isTagPermitted("type-turn")).isFalse();
            assertThat(policy.isTagPermitted("model-gpt4")).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "  session:01J98ABC  ",
                "\tsession:01J98ABC\n",
                "   type:turn   ",
                "\nrole:user\t"
        })
        @DisplayName("Whitespace padding around prohibited tags is properly trimmed and rejected")
        void testWhitespacePaddedProhibitedTagsRejected(String tag) {
            assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                    .isInstanceOf(SpectorValidationException.class);
            assertThat(policy.isTagPermitted(tag)).isFalse();
        }

        @Test
        @DisplayName("Tag with colon at index 0 is treated as bare tag and rejected")
        void testLeadingColonRejected() {
            String leadingColon = ":preference";
            assertThatThrownBy(() -> policy.validateTags(List.of(leadingColon)))
                    .isInstanceOf(SpectorValidationException.class);
        }

        @Test
        @DisplayName("Tag with trailing colon has blank value and is rejected")
        void testTrailingColonRejected() {
            String trailingColon = "preference:";
            assertThatThrownBy(() -> policy.validateTags(List.of(trailingColon)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("must not be blank");
        }
    }

    // =========================================================================
    // 6. PAYLOAD BOUNDARY STRESS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Payload Boundary Stress Tests")
    class PayloadBoundaryTests {

        @Test
        @DisplayName("Tag at exactly MAX_TAG_LENGTH (64 chars) is accepted")
        void testTagMaxLengthAccepted() {
            // "preference:" is 11 chars, so 53 'a's makes 64 chars
            String tag64 = "preference:" + "a".repeat(53);
            assertThat(tag64.length()).isEqualTo(64);
            assertThatCode(() -> policy.validateTags(List.of(tag64))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Tag at MAX_TAG_LENGTH + 1 (65 chars) is rejected")
        void testTagOverMaxLengthRejected() {
            String tag65 = "preference:" + "a".repeat(54);
            assertThat(tag65.length()).isEqualTo(65);
            assertThatThrownBy(() -> policy.validateTags(List.of(tag65)))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("exceeds maximum length of 64");
        }

        @Test
        @DisplayName("Massive tag payload (10,000 chars) is rejected without memory exhaustion")
        void testMassiveTagRejected() {
            String massiveTag = "preference:" + "x".repeat(10_000);
            assertThatThrownBy(() -> policy.validateTags(List.of(massiveTag)))
                    .isInstanceOf(SpectorValidationException.class);
        }

        @Test
        @DisplayName("Content at exactly MAX_CONTENT_LENGTH (8192 chars) is accepted")
        void testContentMaxLengthAccepted() {
            String content8192 = "User stated: " + "a".repeat(8192 - 13);
            assertThat(content8192.length()).isEqualTo(8192);
            assertThatCode(() -> policy.validateContent(content8192)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Content at MAX_CONTENT_LENGTH + 1 (8193 chars) is rejected")
        void testContentOverMaxLengthRejected() {
            String content8193 = "User stated: " + "a".repeat(8193 - 13);
            assertThat(content8193.length()).isEqualTo(8193);
            assertThatThrownBy(() -> policy.validateContent(content8193))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("exceeds maximum allowed length of 8192");
        }

        @Test
        @DisplayName("Huge content payload (100,000 chars) is rejected cleanly")
        void testHugeContentRejected() {
            String hugeContent = "Fact: " + "f".repeat(100_000);
            assertThatThrownBy(() -> policy.validateContent(hugeContent))
                    .isInstanceOf(SpectorValidationException.class);
        }
    }

    // =========================================================================
    // 7. CONTENT ADVERSARIAL ATTACKS
    // =========================================================================

    @Nested
    @DisplayName("Content Adversarial Attacks")
    class ContentAdversarialTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<think>Hidden reasoning</think> User likes coffee.",
                "<THINK>Uppercase reasoning</THINK>",
                "<tHiNk>Mixed case reasoning</tHiNk>",
                "Thinking process: first analyze the facts...",
                "THINKING PROCESS: examine everything...",
                "Prefix [internal cot] suffix"
        })
        @DisplayName("CoT reasoning variants in content are strictly rejected")
        void testCotContentVariantsRejected(String content) {
            assertThatThrownBy(() -> policy.validateContent(content))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("Chain-of-Thought");
        }

        @Test
        @DisplayName("Raw tool JSON object with arguments and status is rejected")
        void testRawToolJsonObjectRejected() {
            String toolJson = "{\"callId\":\"c_123\",\"toolName\":\"recall\",\"status\":\"success\",\"arguments\":{\"q\":\"test\"}}";
            assertThatThrownBy(() -> policy.validateContent(toolJson))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("raw tool execution JSON");
        }

        @Test
        @DisplayName("EMPIRICAL FINDING: Tool JSON preceded by conversational prefix bypasses startsWith('{') check")
        void testPrefixedToolJsonBypass() {
            // When tool JSON is preceded by text, trimmed.startsWith("{") is false!
            String prefixedToolJson = "Execution result was: {\"callId\":\"c_123\",\"toolName\":\"recall\",\"status\":\"success\",\"arguments\":{}}";
            // Empirically verify whether validateContent catches this
            assertThatCode(() -> policy.validateContent(prefixedToolJson)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Markdown-fenced raw tool JSON is strictly rejected")
        void testMarkdownFencedToolJsonRejected() {
            String fencedToolJson = "```json\n{\"callId\":\"c_123\",\"toolName\":\"recall\",\"status\":\"success\",\"arguments\":{}}\n```";
            assertThatThrownBy(() -> policy.validateContent(fencedToolJson))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("raw tool execution JSON");
            assertThat(policy.isProhibitedContent(fencedToolJson)).isTrue();
        }

        @Test
        @DisplayName("EMPIRICAL FINDING: Mid-string 'thinking process:' bypasses startsWith check")
        void testMidStringThinkingProcessBypass() {
            String midStringCot = "Based on my thinking process: I determined the vectors should be aligned.";
            assertThatCode(() -> policy.validateContent(midStringCot)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("EMPIRICAL FINDING: Alternate reasoning tags like <thought> bypass <think> check")
        void testAlternateReasoningTagsBypass() {
            String thoughtTag = "<thought>Internal candidate memory derivation</thought> Final distilled fact.";
            assertThatCode(() -> policy.validateContent(thoughtTag)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("EMPIRICAL FINDING: Delimiter-less session tag 'session123' bypasses denylist and passes alphanumeric bare tag check")
        void testDelimiterlessSessionTagBypass() {
            // "session:123", "session_123", "session-123" are rejected, but "session123" passes
            assertThat(policy.isTagPermitted("session123")).isTrue();
            assertThat(policy.isTagPermitted("id12345")).isTrue();
        }

        @Test
        @DisplayName("Multi-turn conversational transcript is strictly rejected")
        void testMultiTurnTranscriptRejected() {
            String transcript = "User: Where do I live?\nAssistant: You live in Seattle.";
            assertThatThrownBy(() -> policy.validateContent(transcript))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("raw unsummarized conversation transcript");
        }

        @Test
        @DisplayName("LangGraph checkpoint markers in content are strictly rejected")
        void testLangGraphCheckpointRejected() {
            String checkpoint = "Saved state: {\"checkpoint_id\":\"chk_01\",\"channelValues\":{}}";
            assertThatThrownBy(() -> policy.validateContent(checkpoint))
                    .isInstanceOf(SpectorValidationException.class)
                    .hasMessageContaining("serialized LangGraph checkpoint");
        }
    }
}
