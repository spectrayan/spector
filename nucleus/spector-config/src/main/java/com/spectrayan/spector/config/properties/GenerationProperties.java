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
import java.util.Map;

/**
 * Configuration properties POJO for text generation providers.
 *
 * <p>Maps to {@code spector.provider.generation.*} namespace.</p>
 */
public class GenerationProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type = DEFAULT_PROVIDER_GENERATION_TYPE;
    private String model = DEFAULT_PROVIDER_GENERATION_MODEL;
    private String apiKey = DEFAULT_PROVIDER_GENERATION_API_KEY;
    private String baseUrl = DEFAULT_PROVIDER_GENERATION_BASE_URL;
    private Map<String, String> properties = Map.of();

    public GenerationProperties() {}

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

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) {
        if (properties != null) this.properties = properties;
    }

    public String type() { return getType(); }
    public String model() { return getModel(); }
    public String apiKey() { return getApiKey(); }
    public String baseUrl() { return getBaseUrl(); }
    public Map<String, String> properties() { return getProperties(); }
}
