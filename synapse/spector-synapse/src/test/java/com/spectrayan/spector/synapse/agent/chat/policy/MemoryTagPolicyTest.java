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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemoryTagPolicy Dual-Plane Hygiene Tests")
class MemoryTagPolicyTest {

    private MemoryTagPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MemoryTagPolicy();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "session:01J98ABCDF12345",
            "session_01J98ABCDF12345",
            "session:chat-thread-1",
            "SESSION:uppercase_id",
            "id:01J98ABCDF12345",
            "id:sess-99",
            "ID:UUID-HERE",
            "type:turn",
            "TYPE:TURN",
            "role:user",
            "role:assistant",
            "role:system",
            "role:tool",
            "model:qwen3.5:latest",
            "model:gpt-4o"
    })
    @DisplayName("Strictly rejects all denylist tag patterns")
    void testDenylistTagRejection(String tag) {
        assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                .isInstanceOf(SpectorValidationException.class)
                .satisfies(ex -> {
                    SpectorValidationException sve = (SpectorValidationException) ex;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID);
                })
                .hasMessageContaining("SPE-100-013");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "preference:theme_dark",
            "people:alice",
            "decision:h2_database",
            "project:spector",
            "entity:austin",
            "architecture:dual_plane",
            "concept:working_memory",
            "preference",
            "architecture"
    })
    @DisplayName("Accepts allowed domain namespaces and bare domain tags")
    void testAllowedTagAcceptance(String tag) {
        assertThatCode(() -> policy.validateTags(List.of(tag)))
                .doesNotThrowAnyException();
        assertThat(policy.isTagPermitted(tag)).isTrue();
        assertThat(policy.isForbiddenTag(tag)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid_namespace:value",
            "custom:something",
            "temp:cache",
            "foo:bar",
            "preference:" // empty value after colon
    })
    @DisplayName("Rejects unapproved namespaces or empty values")
    void testUnapprovedNamespaces(String tag) {
        assertThatThrownBy(() -> policy.validateTags(List.of(tag)))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("SPE-100-013");
        assertThat(policy.isTagPermitted(tag)).isFalse();
        assertThat(policy.isForbiddenTag(tag)).isTrue();
    }

    @Test
    @DisplayName("Rejects null, blank, or oversized tags")
    void testInvalidTagTokens() {
        assertThatThrownBy(() -> policy.validateTags(List.of("   ")))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> policy.validateTags(List.of("a".repeat(65))))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    @DisplayName("Rejects raw tool execution JSON payloads")
    void testRejectRawToolJson() {
        String toolJson = "{\"callId\":\"call_1\",\"name\":\"memory_recall\",\"arguments\":{\"query\":\"test\"}}";
        assertThatThrownBy(() -> policy.validateContent(toolJson))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("raw tool execution JSON");
    }

    @Test
    @DisplayName("Rejects Chain-of-Thought <think> scratchpads")
    void testRejectCoTScratchpad() {
        String cot = "<think>Analyzing user preferences for relocation...</think> User wants to move.";
        assertThatThrownBy(() -> policy.validateContent(cot))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("Chain-of-Thought");
    }

    @Test
    @DisplayName("Rejects unsummarized multi-turn transcripts")
    void testRejectRawTranscripts() {
        String transcript = "User: What is my lease date?\nAssistant: Your lease expires in August.";
        assertThatThrownBy(() -> policy.validateContent(transcript))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("raw unsummarized conversation transcript");
    }

    @Test
    @DisplayName("Rejects serialized LangGraph checkpoint data")
    void testRejectCheckpointBlobs() {
        String checkpointData = "{\"checkpoint_id\":\"chk_123\",\"channelValues\":{\"messages\":[]}}";
        assertThatThrownBy(() -> policy.validateContent(checkpointData))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("serialized LangGraph checkpoint");
    }

    @Test
    @DisplayName("Accepts clean synthesized factual propositions")
    void testAcceptCleanFactualContent() {
        String fact = "User plans relocation to Austin, Texas in August 2026.";
        assertThatCode(() -> policy.validateContent(fact))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Mandates MemorySource enum validation")
    void testMemorySourceValidation() {
        assertThatCode(() -> policy.validateSource(MemorySource.USER_STATED)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSource(MemorySource.OBSERVED)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSource(MemorySource.INFERRED)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateSource(MemorySource.REFLECTED)).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.validateSource(null))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("MemorySource must not be null");

        assertThatThrownBy(() -> policy.validateSource(MemorySource.DREAMED))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("not permitted for conversational cognitive memory ingestion");
    }

    @Test
    @DisplayName("Composite validate() succeeds for completely clean input")
    void testCompositeValidationSuccess() {
        assertThatCode(() -> policy.validate(
                "User prefers dark mode for IDE theme",
                List.of("preference:ui_theme", "project:spector"),
                MemorySource.USER_STATED
        )).doesNotThrowAnyException();

        assertThatCode(() -> policy.validateSalientMemory(
                "User prefers dark mode for IDE theme",
                List.of("preference:ui_theme", "project:spector"),
                MemorySource.USER_STATED
        )).doesNotThrowAnyException();
    }
}
