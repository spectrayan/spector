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
package com.spectrayan.spector.config;

import com.spectrayan.spector.config.properties.HnswProperties;
import com.spectrayan.spector.config.properties.IngestionProperties;
import com.spectrayan.spector.config.properties.IvfProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.ProviderProperties;
import com.spectrayan.spector.config.properties.SpectrumProperties;

import java.io.Serializable;
import java.nio.file.Path;

/**
 * Aggregate root configuration POJO for Spector.
 *
 * <p>Holds all typed sub-domain configuration objects as an immutable tree.
 * This is the single entry point that downstream modules (memory builders,
 * auto-configurations, CLI commands) should accept for full configuration.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Load from YAML/env/system properties (auto-detect)
 *   SpectorProperties props = SpectorProperties.load();
 *
 *   // Access typed sub-domains
 *   int dims = props.memory().getDimensions();
 *   String embModel = props.provider().getEmbedding().getModel();
 *
 *   // Raw key lookup (escape hatch for unmapped keys)
 *   String raw = props.source().getString("spector.some.unmapped.key");
 *
 *   // With profile
 *   SpectorProperties prodProps = SpectorProperties.load("production");
 *
 *   // From explicit file
 *   SpectorProperties fileProps = SpectorProperties.load(Path.of("/etc/spector/spector.yml"));
 * }</pre>
 *
 * <p>Instances are constructed exclusively by {@link SpectorConfigFactory#spectorProperties(SpectorConfigSource)}.
 * The convenience static factory methods on this class delegate to that path.</p>
 *
 * @see SpectorConfigSource
 * @see SpectorConfigFactory
 */
public final class SpectorProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private final MemoryProperties memory;
    private final ProviderProperties provider;
    private final IngestionProperties ingestion;
    private final HnswProperties hnsw;
    private final IvfProperties ivf;
    private final SpectrumProperties spectrum;
    private final transient SpectorConfigSource source;

    /**
     * Package-private constructor — only {@link SpectorConfigFactory} should call this.
     */
    SpectorProperties(MemoryProperties memory,
                      ProviderProperties provider,
                      IngestionProperties ingestion,
                      HnswProperties hnsw,
                      IvfProperties ivf,
                      SpectrumProperties spectrum,
                      SpectorConfigSource source) {
        this.memory = memory != null ? memory : new MemoryProperties();
        this.provider = provider != null ? provider : new ProviderProperties();
        this.ingestion = ingestion != null ? ingestion : new IngestionProperties();
        this.hnsw = hnsw;
        this.ivf = ivf;
        this.spectrum = spectrum;
        this.source = source;
    }

    // ─────────────── Static Factory Methods ───────────────

    /**
     * Loads configuration with auto-detection and returns a fully hydrated
     * {@code SpectorProperties} aggregate.
     *
     * <p>Checks for a {@code spector.profile} system property or
     * {@code SPECTOR_PROFILE} environment variable to determine the active profile.</p>
     */
    public static SpectorProperties load() {
        SpectorConfigSource source = SpectorConfigSource.load();
        return SpectorConfigFactory.spectorProperties(source);
    }

    /**
     * Loads configuration from classpath defaults only — no filesystem discovery.
     *
     * <p>Useful for tests that should not be affected by a {@code spector.yml}
     * file in the working directory.</p>
     */
    public static SpectorProperties loadClasspathOnly() {
        SpectorConfigSource source = SpectorConfigSource.loadClasspathOnly();
        return SpectorConfigFactory.spectorProperties(source);
    }

    /**
     * Loads configuration with the specified profile.
     *
     * @param profile the active profile name (e.g., "dev", "production"), or null for none
     */
    public static SpectorProperties load(String profile) {
        SpectorConfigSource source = SpectorConfigSource.load(profile);
        return SpectorConfigFactory.spectorProperties(source);
    }

    /**
     * Loads configuration from an explicit file path.
     *
     * @param configFile path to the primary configuration file
     */
    public static SpectorProperties load(Path configFile) {
        SpectorConfigSource source = SpectorConfigSource.load(configFile);
        return SpectorConfigFactory.spectorProperties(source);
    }

    /**
     * Constructs a {@code SpectorProperties} from an existing {@link SpectorConfigSource}.
     *
     * <p>Use this when you have already loaded raw configuration and want to
     * hydrate all typed sub-domain POJOs from it.</p>
     *
     * @param source the raw configuration source
     * @return fully hydrated SpectorProperties aggregate
     */
    public static SpectorProperties from(SpectorConfigSource source) {
        return SpectorConfigFactory.spectorProperties(source);
    }

    // ─────────────── Typed Accessors ───────────────

    /**
     * Returns the memory subsystem configuration.
     * Maps to {@code spector.memory.*} namespace.
     */
    public MemoryProperties memory() { return memory; }

    /**
     * Returns the provider configuration (embedding, generation).
     * Maps to {@code spector.provider.*} namespace.
     */
    public ProviderProperties provider() { return provider; }

    /**
     * Returns the ingestion / file crawler configuration.
     * Maps to {@code spector.ingestion.*} namespace.
     *
     * <p>This is a legacy adapter for the file crawler. Chunking configuration
     * should be read from {@code memory().getRemember().getChunk()} once
     * {@code RememberProperties} is introduced.</p>
     */
    public IngestionProperties ingestion() { return ingestion; }

    /**
     * Returns the HNSW index configuration.
     * Maps to {@code spector.hnsw.*} namespace.
     */
    public HnswProperties hnsw() { return hnsw; }

    /**
     * Returns the IVF index configuration.
     * Maps to {@code spector.ivf.*} namespace.
     */
    public IvfProperties ivf() { return ivf; }

    /**
     * Returns the Spectrum index configuration.
     * Maps to {@code spector.spectrum.*} namespace.
     */
    public SpectrumProperties spectrum() { return spectrum; }

    /**
     * Returns the underlying raw configuration source.
     *
     * <p>This is an escape hatch for accessing configuration keys that have
     * not yet been mapped to a typed POJO field. Prefer using the typed
     * accessors ({@link #memory()}, {@link #provider()}, etc.) when possible.</p>
     */
    public SpectorConfigSource source() { return source; }
}
