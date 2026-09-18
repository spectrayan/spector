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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.kernel.id.TsidGenerator;
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

    @Bean
    @ConditionalOnMissingBean(com.spectrayan.spector.memory.aisme.enactment.EnactmentConfig.class)
    public com.spectrayan.spector.memory.aisme.enactment.EnactmentConfig enactmentConfig() {
        return com.spectrayan.spector.memory.aisme.enactment.EnactmentConfig.defaultConfig();
    }
}
