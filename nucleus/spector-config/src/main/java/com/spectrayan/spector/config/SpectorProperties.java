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

import com.spectrayan.spector.config.properties.*;

import java.io.Serializable;
import java.nio.file.Path;

/**
 * Aggregate root configuration POJO for Spector.
 *
 * <p>Holds all typed sub-domain configuration objects as an aggregate root.
 * Note that while top-level references held by this class are final, child property
 * objects are standard JavaBeans. The underlying {@link #source()} is transient and
 * is not retained across serialization.</p>
 *
 * <p>This is the single entry point that downstream modules (memory builders,
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
    private final TelemetryProperties telemetry;
    private final MultimodalProperties multimodal;
    private final HardwareProperties hardware;
    private final EventsProperties events;
    private final ConcurrencyProperties concurrency;
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
        this(memory, provider, ingestion, hnsw, ivf, spectrum,
                new TelemetryProperties(), new MultimodalProperties(), new HardwareProperties(),
                new EventsProperties(), new ConcurrencyProperties(), source);
    }

    SpectorProperties(MemoryProperties memory,
                      ProviderProperties provider,
                      IngestionProperties ingestion,
                      HnswProperties hnsw,
                      IvfProperties ivf,
                      SpectrumProperties spectrum,
                      TelemetryProperties telemetry,
                      MultimodalProperties multimodal,
                      HardwareProperties hardware,
                      EventsProperties events,
                      ConcurrencyProperties concurrency,
                      SpectorConfigSource source) {
        this.memory = memory != null ? memory : new MemoryProperties();
        this.provider = provider != null ? provider : new ProviderProperties();
        this.ingestion = ingestion != null ? ingestion : new IngestionProperties();
        this.hnsw = hnsw;
        this.ivf = ivf;
        this.spectrum = spectrum;
        this.telemetry = telemetry != null ? telemetry : new TelemetryProperties();
        this.multimodal = multimodal != null ? multimodal : new MultimodalProperties();
        this.hardware = hardware != null ? hardware : new HardwareProperties();
        this.events = events != null ? events : new EventsProperties();
        this.concurrency = concurrency != null ? concurrency : new ConcurrencyProperties();
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

    /**
     * Creates a new builder for programmatic assembly of {@link SpectorProperties}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a {@link SpectorProperties} aggregate containing the specified memory configuration
     * and default configurations for all other sub-domains.
     */
    public static SpectorProperties of(MemoryProperties memory) {
        return builder().memory(memory).build();
    }

    /**
     * Creates a {@link SpectorProperties} aggregate containing the specified memory and provider
     * configurations and default configurations for all other sub-domains.
     */
    public static SpectorProperties of(MemoryProperties memory, ProviderProperties provider) {
        return builder().memory(memory).provider(provider).build();
    }

    /**
     * Fluent builder for {@link SpectorProperties}.
     */
    public static final class Builder {
        private MemoryProperties memory;
        private ProviderProperties provider;
        private IngestionProperties ingestion;
        private HnswProperties hnsw;
        private IvfProperties ivf;
        private SpectrumProperties spectrum;
        private TelemetryProperties telemetry;
        private MultimodalProperties multimodal;
        private HardwareProperties hardware;
        private EventsProperties events;
        private ConcurrencyProperties concurrency;
        private SpectorConfigSource source;

        public Builder memory(MemoryProperties memory) { this.memory = memory; return this; }
        public Builder provider(ProviderProperties provider) { this.provider = provider; return this; }
        public Builder ingestion(IngestionProperties ingestion) { this.ingestion = ingestion; return this; }
        public Builder hnsw(HnswProperties hnsw) { this.hnsw = hnsw; return this; }
        public Builder ivf(IvfProperties ivf) { this.ivf = ivf; return this; }
        public Builder spectrum(SpectrumProperties spectrum) { this.spectrum = spectrum; return this; }
        public Builder telemetry(TelemetryProperties telemetry) { this.telemetry = telemetry; return this; }
        public Builder multimodal(MultimodalProperties multimodal) { this.multimodal = multimodal; return this; }
        public Builder hardware(HardwareProperties hardware) { this.hardware = hardware; return this; }
        public Builder events(EventsProperties events) { this.events = events; return this; }
        public Builder concurrency(ConcurrencyProperties concurrency) { this.concurrency = concurrency; return this; }
        public Builder source(SpectorConfigSource source) { this.source = source; return this; }

        public SpectorProperties build() {
            return new SpectorProperties(
                    memory != null ? memory : new MemoryProperties(),
                    provider != null ? provider : new ProviderProperties(),
                    ingestion != null ? ingestion : new IngestionProperties(),
                    hnsw, ivf, spectrum,
                    telemetry != null ? telemetry : new TelemetryProperties(),
                    multimodal != null ? multimodal : new MultimodalProperties(),
                    hardware != null ? hardware : new HardwareProperties(),
                    events != null ? events : new EventsProperties(),
                    concurrency != null ? concurrency : new ConcurrencyProperties(),
                    source
            );
        }
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
     * <p>This is an adapter for file crawling and legacy ingestion configurations.
     * Memory write and chunking configuration is canonically configured and accessible
     * via {@code memory().getRemember()}.</p>
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
     * Returns the Telemetry configuration.
     * Maps to {@code spector.telemetry.*} namespace.
     */
    public TelemetryProperties telemetry() { return telemetry; }
    public TelemetryProperties getTelemetry() { return telemetry; }

    /**
     * Returns the Multimodal sensory media configuration.
     * Maps to {@code spector.multimodal.*} namespace.
     */
    public MultimodalProperties multimodal() { return multimodal; }
    public MultimodalProperties getMultimodal() { return multimodal; }

    /**
     * Returns the Hardware & GPU configuration.
     * Maps to {@code spector.hardware.*} and {@code spector.gpu.*} namespaces.
     */
    public HardwareProperties hardware() { return hardware; }
    public HardwareProperties getHardware() { return hardware; }

    /**
     * Returns the Event Bus configuration.
     * Maps to {@code spector.events.*} namespace.
     */
    public EventsProperties events() { return events; }
    public EventsProperties getEvents() { return events; }

    /**
     * Returns the Concurrency configuration.
     * Maps to {@code spector.concurrency.*} namespace.
     */
    public ConcurrencyProperties concurrency() { return concurrency; }
    public ConcurrencyProperties getConcurrency() { return concurrency; }

    /**
     * Returns the underlying raw configuration source.
     *
     * <p>This is an escape hatch for accessing configuration keys that have
     * not yet been mapped to a typed POJO field. Prefer using the typed
     * accessors ({@link #memory()}, {@link #provider()}, etc.) when possible.</p>
     */
    public SpectorConfigSource source() { return source; }
}
