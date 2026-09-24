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

import java.util.List;
import java.util.Optional;

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * Test provider factory that produces embedders at whatever width the configuration asks for.
 *
 * <p>Registered through {@code META-INF/services} so it is discoverable by the {@code ServiceLoader} lookup
 * in {@code NamespaceResolver.createEmbeddingProvider}. Without a registered factory there is no way to
 * exercise that path at all, which is why the two-dimensionality claim went unasserted when the per-namespace
 * resolution work first landed.</p>
 *
 * <p>Vectors are deterministic and carry the configured width in their first element, so a test can tell
 * which provider produced a given vector rather than merely that one was produced.</p>
 */
public final class FixedWidthTestProviderFactory extends AbstractProviderFactory {

    /** Provider type this factory answers to, matched case-insensitively by the resolver. */
    public static final String TYPE = "fixed-width-test";

    @Override
    public String name() {
        return TYPE;
    }

    @Override
    public String displayName() {
        return "Fixed-Width Test Embedder";
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
    }

    @Override
    public boolean supportsGeneration() {
        return false;
    }

    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        int dims = config.dimensions() > 0 ? config.dimensions() : 8;
        return Optional.of(new FixedWidthEmbedder(config.model(), dims));
    }

    /** Embedder whose reported model and vector width come from configuration. */
    public static final class FixedWidthEmbedder implements EmbeddingProvider {
        private final String model;
        private final int dims;

        FixedWidthEmbedder(String model, int dims) {
            this.model = model == null || model.isBlank() ? "fixed-width" : model;
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] vector = new float[dims];
            // First element identifies the width, the rest derive from the text — so two providers at
            // different widths cannot produce an equal vector even for identical input.
            vector[0] = dims;
            for (int i = 1; i < dims; i++) {
                vector[i] = ((text.hashCode() >>> (i % 16)) & 0xFF) / 255.0f;
            }
            return new EmbeddingResult(vector, text.length(), model);
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public int maxTokens() {
            return 8192;
        }

        @Override
        public void close() {
            // no resources
        }
    }
}
