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

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties POJO for embedding providers.
 *
 * <p>Maps to {@code spector.provider.embedding.*} namespace.</p>
 */
public class EmbeddingProperties {

    private String type = "ollama";
    private String model = "nomic-embed-text";
    private String apiKey;
    private String baseUrl = "http://localhost:11434";
    private int dimensions = 768;
    private int batchSize = 32;
    private int maxConcurrent = 0;
    private Duration timeout = Duration.ofSeconds(30);
    private Map<String, String> properties = Map.of();

    public EmbeddingProperties() {}

    public String getType() { return type; }
    public void setType(String type) {
        if (type != null && !type.isBlank()) this.type = type;
    }

    public String getModel() { return model; }
    public void setModel(String model) {
        if (model != null && !model.isBlank()) this.model = model;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) this.baseUrl = baseUrl;
    }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) {
        if (dimensions > 0) this.dimensions = dimensions;
    }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) {
        if (batchSize > 0) this.batchSize = batchSize;
    }

    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) {
        if (maxConcurrent >= 0) this.maxConcurrent = maxConcurrent;
    }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) {
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) this.timeout = timeout;
    }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) {
        if (properties != null) this.properties = properties;
    }

    // Record-style accessors
    public String type() { return getType(); }
    public String model() { return getModel(); }
    public String apiKey() { return getApiKey(); }
    public String baseUrl() { return getBaseUrl(); }
    public int dimensions() { return getDimensions(); }
    public int batchSize() { return getBatchSize(); }
    public int maxConcurrent() { return getMaxConcurrent(); }
    public Duration timeout() { return getTimeout(); }
    public Map<String, String> properties() { return getProperties(); }
}
