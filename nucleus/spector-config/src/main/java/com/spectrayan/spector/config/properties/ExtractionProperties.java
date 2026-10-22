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
 * Local span-extraction settings. Maps to {@code spector.provider.extraction.*}.
 * Paths are opt-in: an empty path is not a model, and nothing is downloaded.
 */
public class ExtractionProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final float DEFAULT_THRESHOLD = 0.55f;
    public static final int DEFAULT_MAX_WIDTH = 12;
    public static final int DEFAULT_MAX_TOKENS = 384;
    public static final int DEFAULT_INTRA_OP_THREADS = 1;
    public static final String DEFAULT_LABELS = "person,organization,location";

    private String type = "gliner";
    private String modelPath = "";
    private String tokenizerPath = "";
    private String configPath = "";
    private String relationModelPath = "";
    private float entityThreshold = DEFAULT_THRESHOLD;
    private int maxWidth = DEFAULT_MAX_WIDTH;
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int intraOpThreads = DEFAULT_INTRA_OP_THREADS;
    private String labels = DEFAULT_LABELS;

    public String getType() { return type; }
    public void setType(String type) { if (type != null) this.type = type; }

    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { if (modelPath != null) this.modelPath = modelPath; }

    public String getTokenizerPath() { return tokenizerPath; }
    public void setTokenizerPath(String tokenizerPath) { if (tokenizerPath != null) this.tokenizerPath = tokenizerPath; }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { if (configPath != null) this.configPath = configPath; }

    public String getRelationModelPath() { return relationModelPath; }
    public void setRelationModelPath(String relationModelPath) {
        if (relationModelPath != null) this.relationModelPath = relationModelPath;
    }

    public float getEntityThreshold() { return entityThreshold; }
    public void setEntityThreshold(float entityThreshold) { this.entityThreshold = entityThreshold; }

    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getIntraOpThreads() { return intraOpThreads; }
    public void setIntraOpThreads(int intraOpThreads) { this.intraOpThreads = intraOpThreads; }

    public String getLabels() { return labels; }
    public void setLabels(String labels) { if (labels != null) this.labels = labels; }

    public ExtractionProperties copy() {
        ExtractionProperties copy = new ExtractionProperties();
        copy.setType(type);
        copy.setModelPath(modelPath);
        copy.setTokenizerPath(tokenizerPath);
        copy.setConfigPath(configPath);
        copy.setRelationModelPath(relationModelPath);
        copy.setEntityThreshold(entityThreshold);
        copy.setMaxWidth(maxWidth);
        copy.setMaxTokens(maxTokens);
        copy.setIntraOpThreads(intraOpThreads);
        copy.setLabels(labels);
        return copy;
    }
}
