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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.provider.generation.LlmProvider;

/**
 * Reference-counted pool of LLM providers, keyed by configuration fingerprint.
 *
 * <p>Exists because the process-wide {@code ProviderRegistry} is the wrong mechanism for a scoped override:
 * it keeps a single {@code volatile} active-generation name, so activating a tenant's model there changed the
 * LLM for every namespace in the process, including other tenants'. The registry remains correct for a
 * system-wide default, which embedded and single-tenant deployments rely on.</p>
 *
 * <p>All mechanics live in {@link ReferenceCountedProviderPool}.</p>
 */
public final class LlmProviderPool extends ReferenceCountedProviderPool<LlmProvider> {

    /**
     * Creates a pool with the default configuration cap.
     */
    public LlmProviderPool() {
        this(DEFAULT_MAX_CONFIGURATIONS);
    }

    /**
     * Creates a pool with an explicit cap on distinct live configurations.
     *
     * @param maxConfigurations the cap; values below 1 are raised to 1
     */
    public LlmProviderPool(int maxConfigurations) {
        super("LLM provider", maxConfigurations);
    }
}
