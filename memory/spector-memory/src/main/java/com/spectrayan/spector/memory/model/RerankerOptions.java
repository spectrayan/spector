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
package com.spectrayan.spector.memory.model;

/**
 * ColBERT v2 reranker parameters for recall queries.
 *
 * @param enableReranker enable ColBERT token-level MaxSim reranking
 * @param rerankerDepth  number of first-stage candidates to rerank (default: 50)
 */
public record RerankerOptions(
        boolean enableReranker,
        int rerankerDepth
) {
    /** Default: reranker disabled. */
    public static final RerankerOptions DISABLED = new RerankerOptions(false, 50);

    /** Enabled with default depth of 50. */
    public static final RerankerOptions DEFAULT_ENABLED = new RerankerOptions(true, 50);
}
