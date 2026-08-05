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
import com.spectrayan.spector.config.MemoryConfig;
import com.spectrayan.spector.config.ClientConfig;

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

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    private Engine engine = new Engine();

    private MemoryConfig memory = new MemoryConfig();
    private Metrics metrics = new Metrics();
    private Embedding embedding = new Embedding();
    private ClientConfig client = new ClientConfig();

    public ClientConfig getClient() { return client; }
    public void setClient(ClientConfig client) { this.client = client; }

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public Engine getEngine() { return engine; }
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public void setEngine(Engine engine) { this.engine = engine; }

    public MemoryConfig getMemory() { return memory; }
    public void setMemory(MemoryConfig memory) { this.memory = memory; }

    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

    // ─────────────── Obsolete Engine ───────────────

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public static class Engine {
        private int dimensions = 768;
        private int capacity = 100_000;
        private String similarity = "COSINE";
        private String indexType = "HNSW";
        private String persistenceMode = "DISK";
        private String dataDirectory;

        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public String getSimilarity() { return similarity; }
        public void setSimilarity(String similarity) { this.similarity = similarity; }
        public String getIndexType() { return indexType; }
        public void setIndexType(String indexType) { this.indexType = indexType; }
        public String getPersistenceMode() { return persistenceMode; }
        public void setPersistenceMode(String persistenceMode) { this.persistenceMode = persistenceMode; }
        public String getDataDirectory() { return dataDirectory; }
        public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    }

    // ─────────────── Metrics ───────────────

    public static class Metrics {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    // ─────────────── Embedding ───────────────

    public static class Embedding {
        private String model = "nomic-embed-text";
        private String baseUrl = "http://localhost:11434";
        private int batchSize = 32;
        private int maxConcurrent = 0;
        private Duration timeout;
        private String apiKey;
        private String providerName;
        private int dimensions;
        private Map<String,String> properties;
        private String type;

        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }

        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }

        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    }

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public com.spectrayan.spector.config.SpectorConfig toEngineConfig() {
        var config = com.spectrayan.spector.config.SpectorConfig.DEFAULT
                .withDimensions(engine.dimensions)
                .withCapacity(engine.capacity)
                .withSimilarityFunction(
                        com.spectrayan.spector.core.similarity.SimilarityFunction.valueOf(engine.similarity));

        if (engine.dataDirectory != null) {
            config = config.withPersistence(
                    com.spectrayan.spector.config.PersistenceMode.valueOf(engine.persistenceMode),
                    Path.of(engine.dataDirectory));
        }

        return config;
    }
}
