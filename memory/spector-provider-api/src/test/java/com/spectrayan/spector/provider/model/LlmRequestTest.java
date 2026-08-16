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
package com.spectrayan.spector.provider.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRequestTest {

    @Test
    @DisplayName("Create LlmRequest from prompt and schema")
    void testFromPromptWithSchema() {
        String schema = "{\"type\":\"object\"}";
        LlmRequest req = LlmRequest.fromPrompt("Hello", schema);

        assertThat(req.messages()).hasSize(1);
        assertThat(req.responseJsonSchema()).isEqualTo(schema);
        assertThat(req.hasJsonSchema()).isTrue();
    }

    @Test
    @DisplayName("withJsonSchema returns new instance with schema attached")
    void testWithJsonSchema() {
        LlmRequest req1 = LlmRequest.fromPrompt("Summarize");
        assertThat(req1.hasJsonSchema()).isFalse();

        LlmRequest req2 = req1.withJsonSchema("{\"type\":\"array\"}");
        assertThat(req2.hasJsonSchema()).isTrue();
        assertThat(req2.responseJsonSchema()).isEqualTo("{\"type\":\"array\"}");
        assertThat(req1.hasJsonSchema()).isFalse(); // immutability check
    }

    @Test
    @DisplayName("LlmRequest throws IllegalArgumentException on empty messages")
    void testEmptyMessagesThrows() {
        assertThatThrownBy(() -> new LlmRequest(List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
