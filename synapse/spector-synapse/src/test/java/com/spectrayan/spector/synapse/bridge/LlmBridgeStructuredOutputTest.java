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
package com.spectrayan.spector.synapse.bridge;

import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.synapse.bridge.structured.StructuredOutputException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageEvent;
import com.spectrayan.spector.synapse.provider.usage.TokenUsageTracker;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmBridgeStructuredOutputTest {

    record CustomerRecord(String name, int ordersCount, boolean vip) {}

    private ChatModel chatModel;
    private ProviderRegistry providerRegistry;
    private TokenUsageTracker tokenUsageTracker;
    private LlmBridge llmBridge;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        providerRegistry = mock(ProviderRegistry.class);
        tokenUsageTracker = mock(TokenUsageTracker.class);

        LangChain4jGenerationAdapter adapter = new LangChain4jGenerationAdapter(chatModel, "test-llm");
        when(providerRegistry.activeGeneration()).thenReturn(Optional.of(adapter));

        SynapseProperties props = new SynapseProperties();
        props.getProvider().getGeneration().setModel("test-llm");

        llmBridge = new LlmBridge(props, providerRegistry, null, tokenUsageTracker);
    }

    @Test
    @DisplayName("Successfully generates structured output into Java record")
    void testGenerateStructuredSuccess() {
        String jsonResponse = "{\"name\":\"Alice\",\"ordersCount\":12,\"vip\":true}";
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(jsonResponse))
                .tokenUsage(new TokenUsage(100, 50))
                .build();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        CustomerRecord result = llmBridge.generateStructured("Extract user info", CustomerRecord.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.ordersCount()).isEqualTo(12);
        assertThat(result.vip()).isTrue();

        verify(tokenUsageTracker, atLeastOnce()).record(any(TokenUsageEvent.class));
    }

    @Test
    @DisplayName("Self-healing retry recovers when first response is malformed JSON")
    void testSelfHealingRetryRecovery() {
        ChatResponse invalidResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Sorry, I cannot provide JSON format."))
                .tokenUsage(new TokenUsage(50, 20))
                .build();

        String validJson = "```json\n{\"name\":\"Bob\",\"ordersCount\":3,\"vip\":false}\n```";
        ChatResponse validResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(validJson))
                .tokenUsage(new TokenUsage(80, 40))
                .build();

        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(invalidResponse)
                .thenReturn(validResponse);

        CustomerRecord result = llmBridge.generateStructured("Extract user", CustomerRecord.class, 2);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Bob");
        assertThat(result.ordersCount()).isEqualTo(3);
        assertThat(result.vip()).isFalse();

        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    @DisplayName("Exhausted retries throw StructuredOutputException")
    void testExhaustedRetriesThrows() {
        ChatResponse invalidResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("Invalid output forever"))
                .tokenUsage(new TokenUsage(50, 20))
                .build();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(invalidResponse);

        assertThatThrownBy(() -> llmBridge.generateStructured("Extract user", CustomerRecord.class, 1))
                .isInstanceOf(StructuredOutputException.class)
                .hasMessageContaining("Structured generation failed after 2 attempts");

        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }
}
