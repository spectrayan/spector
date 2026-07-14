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
package com.spectrayan.spector.synapse.provider;

import com.spectrayan.spector.embed.GenerationOptions;
import com.spectrayan.spector.embed.TextGenerationProvider;

/**
 * Delegating implementation of {@link TextGenerationProvider} that routes requests
 * to the dynamically active provider registered in the {@link ProviderRegistry}.
 *
 * This allows the statically defined SpectorMemory bean to leverage dynamic provider
 * switching in the Synapse container.
 */
public class DelegatingTextGenerationProvider implements TextGenerationProvider {

    private final ProviderRegistry providerRegistry;

    public DelegatingTextGenerationProvider(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    private TextGenerationProvider getActive() {
        return providerRegistry.activeGeneration()
                .orElseThrow(() -> new TextGenerationProvider.GenerationException(
                        "No active text generation provider registered in the ProviderRegistry"));
    }

    @Override
    public String generate(String prompt) {
        return getActive().generate(prompt);
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        return getActive().generate(prompt, options);
    }

    @Override
    public String modelName() {
        return providerRegistry.activeGeneration()
                .map(TextGenerationProvider::modelName)
                .orElse("none");
    }

    @Override
    public boolean isAvailable() {
        return providerRegistry.activeGeneration()
                .map(TextGenerationProvider::isAvailable)
                .orElse(false);
    }
}
