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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.memory.kernel.id.TsidGenerator;
import com.spectrayan.spector.provider.DefaultProviderRegistry;
import com.spectrayan.spector.provider.DelegatingLlmProvider;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing default provider registry, delegating LLM provider,
 * and ID generator beans for Synapse.
 */
@Configuration
public class SynapseProviderConfig {

    @Bean
    @ConditionalOnMissingBean(ProviderRegistry.class)
    public ProviderRegistry providerRegistry() {
        return new DefaultProviderRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(LlmProvider.class)
    public LlmProvider llmProvider(ProviderRegistry providerRegistry) {
        return new DelegatingLlmProvider(providerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(TsidGenerator.class)
    public TsidGenerator tsidGenerator() {
        return new TsidGenerator();
    }
}
