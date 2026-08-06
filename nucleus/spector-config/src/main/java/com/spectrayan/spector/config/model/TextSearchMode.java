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
package com.spectrayan.spector.config.model;

/**
 * Text search strategy for memory recall.
 */
public enum TextSearchMode {

    VECTOR_ONLY,
    HYBRID,
    BM25_ONLY,
    @Deprecated KEYWORD_ONLY,
    SPLADE_ONLY,
    @Deprecated SPLADE,
    @Deprecated LI_LSR,
    SPLADE_VECTOR,
    @Deprecated SPLADE_HYBRID,
    COLBERT,
    @Deprecated COLBERT_RERANK,
    FULL_STACK;

    public boolean isHybrid() {
        return this == HYBRID || this == SPLADE_VECTOR || this == SPLADE_HYBRID || this == COLBERT || this == COLBERT_RERANK || this == FULL_STACK;
    }

    public boolean usesVector() {
        return this == VECTOR_ONLY || this == HYBRID || this == SPLADE_VECTOR || this == SPLADE_HYBRID || this == COLBERT || this == COLBERT_RERANK || this == FULL_STACK;
    }

    public boolean usesSplade() {
        return this == SPLADE_ONLY || this == SPLADE || this == LI_LSR || this == SPLADE_VECTOR || this == SPLADE_HYBRID || this == FULL_STACK;
    }

    public boolean usesSPLADE() {
        return usesSplade();
    }

    public boolean usesColbert() {
        return this == COLBERT || this == COLBERT_RERANK || this == FULL_STACK;
    }

    public boolean usesColBERT() {
        return usesColbert();
    }

    public boolean usesBm25() {
        return this == HYBRID || this == BM25_ONLY || this == KEYWORD_ONLY || this == COLBERT || this == COLBERT_RERANK || this == FULL_STACK;
    }

    public boolean usesBM25() {
        return usesBm25();
    }
}
