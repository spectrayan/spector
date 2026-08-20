/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.graph.nodes;

import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.bridge.LlmBridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ReflectionNode Self-Correction Tests")
class ReflectionNodeTest {

    private LlmBridge llmBridge;
    private ReflectionNode reflectionNode;

    @BeforeEach
    void setUp() {
        llmBridge = mock(LlmBridge.class);
        reflectionNode = new ReflectionNode(llmBridge, 3, true);
    }

    @Test
    @DisplayName("Should accept answer when LLM evaluates quality as ACCEPT")
    void testAcceptDecision() {
        when(llmBridge.generate(anyString())).thenReturn("""
                DECISION: ACCEPT
                REASON: The answer is complete, factual, grounded in context, and tools succeeded.
                """);

        CognitiveState state = new CognitiveState(Map.of(
                "query", "What is Spector Synapse?",
                "original_query", "What is Spector Synapse?",
                "context", List.of("[memory] Synapse is the agentic orchestration layer."),
                "answer", "Spector Synapse is the agentic orchestration layer."
        ));

        Map<String, Object> result = reflectionNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "ACCEPT");
    }

    @Test
    @DisplayName("Should trigger RETRY_RETRIEVE when missing facts are detected")
    void testRetryRetrieveDecision() {
        when(llmBridge.generate(anyString())).thenReturn("""
                DECISION: RETRY_RETRIEVE
                REFINED_QUERY: Spector Synapse architecture BSL license
                CRITIQUE: The answer lacks details on the BSL 1.1 license change date.
                REASON: Missing license facts in retrieved context.
                """);

        CognitiveState state = new CognitiveState(Map.of(
                "query", "What is Spector Synapse license?",
                "original_query", "What is Spector Synapse license?",
                "context", List.of("[memory] Synapse is an agent framework."),
                "answer", "Synapse is open source.",
                "retry_count", 0
        ));

        Map<String, Object> result = reflectionNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "RETRY_RETRIEVE");
        assertThat(result).containsEntry("query", "Spector Synapse architecture BSL license");
        assertThat(result).containsEntry("retry_count", 1);
        @SuppressWarnings("unchecked")
        List<String> critiques = (List<String>) result.get("critique");
        assertThat(critiques).hasSize(1);
        assertThat(critiques.getFirst()).contains("BSL 1.1 license change date");
    }

    @Test
    @DisplayName("Should trigger RETRY_GENERATE when synthesis or formatting is poor")
    void testRetryGenerateDecision() {
        when(llmBridge.generate(anyString())).thenReturn("""
                DECISION: RETRY_GENERATE
                CRITIQUE: The response is overly brief and misses the key architectural components mentioned in context.
                REASON: Context has enough data but answer was poorly formulated.
                """);

        CognitiveState state = new CognitiveState(Map.of(
                "query", "Explain Spector architecture",
                "original_query", "Explain Spector architecture",
                "context", List.of("[memory] Nucleus, Memory, Synapse, Cortex"),
                "answer", "It has some parts.",
                "retry_count", 1
        ));

        Map<String, Object> result = reflectionNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "RETRY_GENERATE");
        assertThat(result).containsEntry("retry_count", 2);
        @SuppressWarnings("unchecked")
        List<String> critiques = (List<String>) result.get("critique");
        assertThat(critiques).hasSize(1);
        assertThat(critiques.getFirst()).contains("overly brief");
    }

    @Test
    @DisplayName("Should force ACCEPT with caveat note when max retries are reached")
    void testMaxRetriesReached() {
        CognitiveState state = new CognitiveState(Map.of(
                "query", "Difficult question",
                "original_query", "Difficult question",
                "context", List.of(),
                "answer", "Best effort response.",
                "retry_count", 3
        ));

        Map<String, Object> result = reflectionNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "ACCEPT");
        assertThat((String) result.get("answer"))
                .contains("Best effort response.")
                .contains("*(Note: Generated with quality caveats after 3 reflection attempts)*");

        verifyNoInteractions(llmBridge);
    }

    @Test
    @DisplayName("Should bypass reflection when disabled")
    void testDisabledReflection() {
        ReflectionNode disabledNode = new ReflectionNode(llmBridge, 3, false);

        CognitiveState state = new CognitiveState(Map.of(
                "query", "Query",
                "original_query", "Query",
                "answer", "Some answer"
        ));

        Map<String, Object> result = disabledNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "ACCEPT");
        verifyNoInteractions(llmBridge);
    }

    @Test
    @DisplayName("Should trigger RETRY_GENERATE when answer is blank")
    void testBlankAnswerHandling() {
        CognitiveState state = new CognitiveState(Map.of(
                "query", "Query",
                "original_query", "Query",
                "answer", "   "
        ));

        Map<String, Object> result = reflectionNode.apply(state);

        assertThat(result).containsEntry("reflection_decision", "RETRY_GENERATE");
        assertThat(result).containsEntry("retry_count", 1);
        verifyNoInteractions(llmBridge);
    }
}
