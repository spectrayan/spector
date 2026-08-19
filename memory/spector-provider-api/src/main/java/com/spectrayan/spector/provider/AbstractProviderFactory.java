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

import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;

import java.util.Optional;

/**
 * Base implementation of {@link ProviderFactory} that automatically applies cross-cutting
 * concerns such as {@link CachingEmbeddingProvider} decoration when a {@link SpectorCacheManager}
 * is provided and caching is enabled.
 *
 * <p>Uses the Template Method pattern: concrete subclasses implement
 * {@link #createRawEmbeddingProvider(ProviderConfig)} and/or
 * {@link #createGenerationProvider(ProviderConfig)}.</p>
 *
 * @see ProviderFactory
 * @see CachingEmbeddingProvider
 */
public abstract class AbstractProviderFactory implements ProviderFactory {

    protected final SpectorCacheManager cacheManager;

    protected AbstractProviderFactory() {
        this(null);
    }

    protected AbstractProviderFactory(SpectorCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public final Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        if (!supportsEmbedding()) {
            return Optional.empty();
        }
        return createRawEmbeddingProvider(config)
                .map(raw -> {
                    if (cacheManager != null && config.embeddingCacheConfig().enabled()) {
                        return CachingEmbeddingProvider.wrap(raw, cacheManager);
                    }
                    return raw;
                });
    }

    /**
     * Creates the raw, un-decorated {@link EmbeddingProvider} instance for this backend.
     *
     * <p>Subclasses that support embedding must override this method. The default implementation
     * returns {@link Optional#empty()}.</p>
     *
     * @param config provider configuration
     * @return raw embedding provider or empty
     */
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        return Optional.empty();
    }

    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        return Optional.empty();
    }
}
