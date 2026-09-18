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
package com.spectrayan.spector.synapse.agent.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CognitiveState Schema & Channels Test")
class CognitiveStateTest {

    @Test
    @DisplayName("Should initialize defaults for reflection channels")
    void testReflectionDefaults() {
        CognitiveState state = new CognitiveState(Map.of(
                "query", "What is Spector?",
                "original_query", "What is Spector?"
        ));

        assertThat(state.query()).isEqualTo("What is Spector?");
        assertThat(state.reflectionDecision()).isEqualTo("ACCEPT");
        assertThat(state.critiques()).isEmpty();
        assertThat(state.retryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should expose typed accessors for reflection data")
    void testReflectionAccessors() {
        CognitiveState state = new CognitiveState(Map.of(
                "query", "What is Spector?",
                "original_query", "What is Spector?",
                "answer", "Spector is an AI system.",
                "reflection_decision", "RETRY_GENERATE",
                "critique", List.of("Add architecture details", "Include performance benchmarks"),
                "retry_count", 2
        ));

        assertThat(state.answer()).contains("Spector is an AI system.");
        assertThat(state.reflectionDecision()).isEqualTo("RETRY_GENERATE");
        assertThat(state.critiques()).containsExactly("Add architecture details", "Include performance benchmarks");
        assertThat(state.retryCount()).isEqualTo(2);
    }
}
