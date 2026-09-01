/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
