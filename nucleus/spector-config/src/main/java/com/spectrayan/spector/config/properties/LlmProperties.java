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

/**
 * LLM generation configuration properties.
 */
public class LlmProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private float temperature = DEFAULT_MEMORY_LLM_TEMPERATURE;
    private int maxTokens = DEFAULT_MEMORY_LLM_MAX_TOKENS;
    private float topP = DEFAULT_MEMORY_LLM_TOP_P;
    private String entityModel = DEFAULT_MEMORY_LLM_ENTITY_MODEL;

    public LlmProperties() {}

    public LlmProperties(float temperature, int maxTokens, float topP, String entityModel) {
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        if (entityModel != null) this.entityModel = entityModel;
    }

    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public float getTopP() { return topP; }
    public void setTopP(float topP) { this.topP = topP; }

    public String getEntityModel() { return entityModel; }
    public void setEntityModel(String entityModel) {
        if (entityModel != null) this.entityModel = entityModel;
    }

    public float temperature() { return getTemperature(); }
    public int maxTokens() { return getMaxTokens(); }
    public float topP() { return getTopP(); }
    public String entityModel() { return getEntityModel(); }
}
