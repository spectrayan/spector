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
package com.spectrayan.spector.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.ClientProperties;
import com.spectrayan.spector.config.properties.EmbeddingProperties;
import com.spectrayan.spector.config.properties.ProviderProperties;
import com.spectrayan.spector.config.properties.HardwareProperties;
import com.spectrayan.spector.config.properties.EventsProperties;
import com.spectrayan.spector.config.properties.ConcurrencyProperties;
import com.spectrayan.spector.config.properties.TelemetryProperties;
import com.spectrayan.spector.config.properties.MultimodalProperties;
import com.spectrayan.spector.config.properties.IngestionProperties;
import com.spectrayan.spector.config.properties.HnswProperties;
import com.spectrayan.spector.config.properties.IvfProperties;
import com.spectrayan.spector.config.properties.SpectrumProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Spring Boot configuration properties for Spector.
 *
 * <p>Maps to the {@code spector.*} namespace in {@code application.yml} /
 * {@code application.properties}. Reuses core domain configuration POJOs
 * from {@code com.spectrayan.spector.config}.</p>
 */
@ConfigurationProperties("spector")
public class SpectorConfigProperties {

    private MemoryProperties memory = new MemoryProperties();
    private Metrics metrics = new Metrics();
    private ProviderProperties provider = new ProviderProperties();
    private ClientProperties client = new ClientProperties();
    private HardwareProperties hardware = new HardwareProperties();
    private EventsProperties events = new EventsProperties();
    private ConcurrencyProperties concurrency = new ConcurrencyProperties();
    private TelemetryProperties telemetry = new TelemetryProperties();
    private MultimodalProperties multimodal = new MultimodalProperties();
    private IngestionProperties ingestion = new IngestionProperties();
    private HnswProperties hnsw;
    private IvfProperties ivf;
    private SpectrumProperties spectrum;

    public ClientProperties getClient() { return client; }
    public void setClient(ClientProperties client) { this.client = client; }

    public MemoryProperties getMemory() { return memory; }
    public void setMemory(MemoryProperties memory) { this.memory = memory; }

    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }

    public ProviderProperties getProvider() { return provider; }
    public void setProvider(ProviderProperties provider) {
        if (provider != null) this.provider = provider;
    }

    public HardwareProperties getHardware() { return hardware; }
    public void setHardware(HardwareProperties hardware) {
        if (hardware != null) this.hardware = hardware;
    }

    public EventsProperties getEvents() { return events; }
    public void setEvents(EventsProperties events) {
        if (events != null) this.events = events;
    }

    public ConcurrencyProperties getConcurrency() { return concurrency; }
    public void setConcurrency(ConcurrencyProperties concurrency) {
        if (concurrency != null) this.concurrency = concurrency;
    }

    public TelemetryProperties getTelemetry() { return telemetry; }
    public void setTelemetry(TelemetryProperties telemetry) {
        if (telemetry != null) this.telemetry = telemetry;
    }

    public MultimodalProperties getMultimodal() { return multimodal; }
    public void setMultimodal(MultimodalProperties multimodal) {
        if (multimodal != null) this.multimodal = multimodal;
    }

    public IngestionProperties getIngestion() { return ingestion; }
    public void setIngestion(IngestionProperties ingestion) {
        if (ingestion != null) this.ingestion = ingestion;
    }

    public HnswProperties getHnsw() { return hnsw; }
    public void setHnsw(HnswProperties hnsw) { this.hnsw = hnsw; }

    public IvfProperties getIvf() { return ivf; }
    public void setIvf(IvfProperties ivf) { this.ivf = ivf; }

    public SpectrumProperties getSpectrum() { return spectrum; }
    public void setSpectrum(SpectrumProperties spectrum) { this.spectrum = spectrum; }

    /**
     * Converts this Spring Boot configuration properties bean into a canonical
     * {@link SpectorProperties} aggregate root snapshot.
     */
    public SpectorProperties toSpectorProperties() {
        return SpectorProperties.builder()
                .memory(memory)
                .provider(provider)
                .ingestion(ingestion)
                .hnsw(hnsw)
                .ivf(ivf)
                .spectrum(spectrum)
                .hardware(hardware)
                .events(events)
                .concurrency(concurrency)
                .telemetry(telemetry)
                .multimodal(multimodal)
                .build();
    }

    // ─────────────── Metrics ───────────────

    public static class Metrics {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
