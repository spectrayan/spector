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
import com.spectrayan.spector.config.MemoryProperties;
import com.spectrayan.spector.config.ClientProperties;
import com.spectrayan.spector.config.EmbeddingProperties;
import com.spectrayan.spector.config.ProviderProperties;

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

    // ─────────────── Metrics ───────────────

    public static class Metrics {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
