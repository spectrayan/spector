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
package com.spectrayan.spector.provider;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import java.util.Objects;

/**
 * Delegating implementation of {@link LlmProvider} that routes requests
 * to the dynamically active provider registered in the {@link ProviderRegistry}.
 *
 * <p>This allows statically constructed {@code SpectorMemory} instances to leverage dynamic provider
 * switching.</p>
 */
public class DelegatingLlmProvider implements LlmProvider {

    private final ProviderRegistry providerRegistry;

    public DelegatingLlmProvider(ProviderRegistry providerRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
    }

    private LlmProvider getActive() {
        return providerRegistry.activeGeneration()
                .orElseThrow(() -> new LlmProvider.GenerationException(
                        "No active text generation provider registered in the ProviderRegistry"));
    }

    @Override
    public LlmResponse generate(LlmRequest request, GenerationOptions options) {
        return getActive().generate(request, options);
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
                .map(LlmProvider::modelName)
                .orElse("none");
    }

    @Override
    public boolean isAvailable() {
        return providerRegistry.activeGeneration()
                .map(LlmProvider::isAvailable)
                .orElse(false);
    }
}
