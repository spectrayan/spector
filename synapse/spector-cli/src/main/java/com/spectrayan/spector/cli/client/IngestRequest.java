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
package com.spectrayan.spector.cli.client;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request model for document ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestRequest {

    private String id;
    private String text;
    private float[] vector;
    private Map<String, Object> metadata;

    public IngestRequest() {}

    public IngestRequest(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public IngestRequest(String id, String text, float[] vector) {
        this.id = id;
        this.text = text;
        this.vector = vector;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
