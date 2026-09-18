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
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("GenerateNode Critique Integration Tests")
class GenerateNodeCritiqueTest {

    @Test
    @DisplayName("Should inject previous critiques into generation prompt when present")
    void testCritiqueInjection() {
        LlmBridge llmBridge = mock(LlmBridge.class);
        when(llmBridge.generate(anyString())).thenReturn("Revised well-grounded response.");

        GenerateNode generateNode = new GenerateNode(llmBridge);

        CognitiveState state = new CognitiveState(Map.of(
                "query", "What is Spector?",
                "original_query", "What is Spector?",
                "context", List.of("[memory] Spector is cognitive memory."),
                "critique", List.of("[Reflection Critique] Add detail on architecture layers.")
        ));

        Map<String, Object> result = generateNode.apply(state);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmBridge).generate(promptCaptor.capture());

        String generatedPrompt = promptCaptor.getValue();
        assertThat(generatedPrompt).contains("CRITIQUE FROM PREVIOUS ATTEMPTS");
        assertThat(generatedPrompt).contains("Add detail on architecture layers.");
        assertThat(result).containsEntry("answer", "Revised well-grounded response.");
    }
}
