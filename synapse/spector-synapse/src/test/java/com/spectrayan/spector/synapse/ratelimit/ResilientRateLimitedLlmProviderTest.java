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
package com.spectrayan.spector.synapse.ratelimit;

import com.spectrayan.spector.config.properties.RateLimitProperties.LlmProviderPolicy;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.provider.ResilientRateLimitedLlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResilientRateLimitedLlmProviderTest {

    private LlmProvider delegate;
    private SynapseProperties properties;
    private ResilientRateLimitedLlmProvider resilientProvider;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(LlmProvider.class);
        when(delegate.modelName()).thenReturn("mock-gpt");
        when(delegate.isAvailable()).thenReturn(true);

        properties = new SynapseProperties();
        var llmConfig = properties.getRateLimit().getLlm();
        llmConfig.setEnabled(true);
        llmConfig.setQueueTimeoutMs(50); // fast timeout for unit tests
        llmConfig.setMaxRetries(1);

        // Configure strict policy for testing
        llmConfig.getProviders().put("mock-gpt", new LlmProviderPolicy(2, 2000, 2));

        resilientProvider = new ResilientRateLimitedLlmProvider(delegate, properties);
    }

    @Test
    @DisplayName("Should successfully generate within RPM and TPM bounds")
    void testSuccessfulGeneration() {
        when(delegate.generate(any(LlmRequest.class), any(GenerationOptions.class)))
                .thenReturn(new LlmResponse("Hello from mock LLM", 50, 50, "mock-gpt"));

        String response = resilientProvider.generate("Say hello");
        assertThat(response).isEqualTo("Hello from mock LLM");
    }

    @Test
    @DisplayName("Should reject requests when RPM limit is exceeded and queue timeout expires")
    void testRpmExhaustion() {
        when(delegate.generate(any(LlmRequest.class), any(GenerationOptions.class)))
                .thenReturn(new LlmResponse("Response", 10, 10, "mock-gpt"));

        // First 2 calls succeed (RPM limit = 2)
        resilientProvider.generate("Prompt 1");
        resilientProvider.generate("Prompt 2");

        // 3rd call should exceed RPM limit within 50ms timeout
        assertThatThrownBy(() -> resilientProvider.generate("Prompt 3"))
                .isInstanceOf(LlmProvider.GenerationException.class)
                .hasMessageContaining("LLM RPM rate limit exceeded");
    }

    @Test
    @DisplayName("Should retry when delegate encounters transient failure")
    void testRetryOnFailure() {
        when(delegate.generate(any(LlmRequest.class), any(GenerationOptions.class)))
                .thenThrow(new RuntimeException("Transient 503"))
                .thenReturn(new LlmResponse("Recovered response", 20, 20, "mock-gpt"));

        String response = resilientProvider.generate("Test prompt");
        assertThat(response).isEqualTo("Recovered response");
    }
}
