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
 * Request model for document search.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchRequest {

    private String text;
    private float[] vector;
    private String mode = "KEYWORD";
    private int topK = 10;
    private Map<String, Object> filter;
    private Map<String, Object> options;

    public SearchRequest() {}

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public Map<String, Object> getFilter() { return filter; }
    public void setFilter(Map<String, Object> filter) { this.filter = filter; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}
