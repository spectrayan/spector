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
package com.spectrayan.spector.config.properties;

import java.io.Serializable;

/**
 * Top-level configuration container for provider settings.
 *
 * <p>Maps to {@code spector.provider.*} namespace.</p>
 */
public class ProviderProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private EmbeddingProperties embedding = new EmbeddingProperties();
    private GenerationProperties generation = new GenerationProperties();
    private boolean sslInsecure = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_PROVIDER_SSL_INSECURE;

    public ProviderProperties() {}

    public EmbeddingProperties getEmbedding() { return embedding; }
    public void setEmbedding(EmbeddingProperties embedding) {
        if (embedding != null) this.embedding = embedding;
    }

    public GenerationProperties getGeneration() { return generation; }
    public void setGeneration(GenerationProperties generation) {
        if (generation != null) this.generation = generation;
    }

    public boolean isSslInsecure() { return sslInsecure; }
    public void setSslInsecure(boolean sslInsecure) { this.sslInsecure = sslInsecure; }

    // Record-style accessors
    public EmbeddingProperties embedding() { return getEmbedding(); }
    public GenerationProperties generation() { return getGeneration(); }
    public boolean sslInsecure() { return isSslInsecure(); }

    /**
     * Creates a full deep copy of this {@link ProviderProperties} instance.
     */
    public ProviderProperties copy() {
        ProviderProperties cp = new ProviderProperties();
        cp.setEmbedding(this.embedding != null ? this.embedding.copy() : new EmbeddingProperties());
        cp.setGeneration(this.generation);
        cp.setSslInsecure(this.sslInsecure);
        return cp;
    }
}
