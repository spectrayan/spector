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
package com.spectrayan.spector.provider.onnx;

import com.spectrayan.spector.provider.embedding.InProcessEmbeddingProvider;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * In-Process Native ONNX Embedding Provider adapting LangChain4j {@link EmbeddingModel}.
 *
 * <p>Generates dense vector embeddings directly inside JVM process memory
 * with zero network I/O, supporting models of arbitrary dimensions (384, 768, 1024, etc.).</p>
 */
public class OnnxEmbeddingProvider extends LangChain4jEmbeddingAdapter implements InProcessEmbeddingProvider {

    private final String executionBackend;

    public OnnxEmbeddingProvider(EmbeddingModel delegate, String modelName, int dimensions, String executionBackend) {
        super(delegate, modelName, dimensions);
        this.executionBackend = executionBackend != null && !executionBackend.isBlank() ? executionBackend : "CPU";
    }

    public OnnxEmbeddingProvider(EmbeddingModel delegate, String modelName, int dimensions) {
        this(delegate, modelName, dimensions, "CPU");
    }

    @Override
    public boolean isInProcess() {
        return true;
    }

    @Override
    public String executionBackend() {
        return executionBackend;
    }
}
