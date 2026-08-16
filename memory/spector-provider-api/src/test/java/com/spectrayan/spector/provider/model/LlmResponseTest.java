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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResponseTest {

    @Test
    @DisplayName("should calculate totalTokens accurately")
    void totalTokensCalculation() {
        LlmResponse resp = new LlmResponse("hello world", 120, 45, "gpt-4o-mini");
        assertThat(resp.totalTokens()).isEqualTo(165);
        assertThat(resp.hasTokenUsage()).isTrue();
    }

    @Test
    @DisplayName("should report hasTokenUsage false when tokens are zero")
    void hasTokenUsageZero() {
        LlmResponse resp = new LlmResponse("hello world", 0, 0, "test-model");
        assertThat(resp.totalTokens()).isZero();
        assertThat(resp.hasTokenUsage()).isFalse();
    }

    @Test
    @DisplayName("should validate non-null constructor constraints")
    void nullValidation() {
        assertThatThrownBy(() -> new LlmResponse(null, 10, 10, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LlmResponse("txt", 10, 10, null))
                .isInstanceOf(NullPointerException.class);
    }
}
