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

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties POJO for embedding providers.
 *
 * <p>Maps to {@code spector.provider.embedding.*} namespace.</p>
 */
public class EmbeddingProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type = DEFAULT_PROVIDER_EMBEDDING_TYPE;
    private String model = DEFAULT_PROVIDER_EMBEDDING_MODEL;
    private String apiKey = DEFAULT_PROVIDER_EMBEDDING_API_KEY;
    private String baseUrl = DEFAULT_PROVIDER_EMBEDDING_BASE_URL;
    private int dimensions = DEFAULT_PROVIDER_EMBEDDING_DIMENSIONS;
    private int batchSize = DEFAULT_PROVIDER_EMBEDDING_BATCH_SIZE;
    private int maxRetries = DEFAULT_PROVIDER_EMBEDDING_MAX_RETRIES;
    private int maxConcurrent = DEFAULT_PROVIDER_EMBEDDING_MAX_CONCURRENT;
    private Duration timeout = DEFAULT_PROVIDER_EMBEDDING_TIMEOUT;
    private CacheProperties cache = new CacheProperties();
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

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0) this.maxRetries = maxRetries;
    }

    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) {
        if (maxConcurrent >= 0) this.maxConcurrent = maxConcurrent;
    }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) {
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) this.timeout = timeout;
    }

    public CacheProperties getCache() { return cache; }
    public void setCache(CacheProperties cache) { if (cache != null) this.cache = cache; }

    public boolean isCacheEnabled() { return cache.isEnabled(); }
    public void setCacheEnabled(boolean cacheEnabled) { this.cache.setEnabled(cacheEnabled); }

    public int getCacheMaxSize() { return cache.getMaxSize(); }
    public void setCacheMaxSize(int cacheMaxSize) { this.cache.setMaxSize(cacheMaxSize); }

    public Duration getCacheTtl() { return cache.getTtl(); }
    public void setCacheTtl(Duration cacheTtl) { this.cache.setTtl(cacheTtl); }

    public Duration getCacheStatsLogInterval() { return cache.getStatsLogInterval(); }
    public void setCacheStatsLogInterval(Duration cacheStatsLogInterval) { this.cache.setStatsLogInterval(cacheStatsLogInterval); }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) {
        if (properties != null) this.properties = properties;
    }

    public static class CacheProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = DEFAULT_PROVIDER_EMBEDDING_CACHE_ENABLED;
        private int maxSize = DEFAULT_PROVIDER_EMBEDDING_CACHE_MAX_SIZE;
        private Duration ttl = DEFAULT_PROVIDER_EMBEDDING_CACHE_TTL;
        private Duration statsLogInterval = DEFAULT_PROVIDER_EMBEDDING_CACHE_STATS_LOG_INTERVAL;

        public CacheProperties() {}

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { if (maxSize > 0) this.maxSize = maxSize; }

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { if (ttl != null) this.ttl = ttl; }

        public Duration getStatsLogInterval() { return statsLogInterval; }
        public void setStatsLogInterval(Duration statsLogInterval) { if (statsLogInterval != null) this.statsLogInterval = statsLogInterval; }
    }

    public String type() { return getType(); }
    public String model() { return getModel(); }
    public String apiKey() { return getApiKey(); }
    public String baseUrl() { return getBaseUrl(); }
    public int dimensions() { return getDimensions(); }
    public int batchSize() { return getBatchSize(); }
    public int maxRetries() { return getMaxRetries(); }
    public int maxConcurrent() { return getMaxConcurrent(); }
    public Duration timeout() { return getTimeout(); }
    public boolean cacheEnabled() { return isCacheEnabled(); }
    public int cacheMaxSize() { return getCacheMaxSize(); }
    public Duration cacheTtl() { return getCacheTtl(); }
    public Duration cacheStatsLogInterval() { return getCacheStatsLogInterval(); }
    public Map<String, String> properties() { return getProperties(); }
}
