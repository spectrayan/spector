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

import com.spectrayan.spector.config.model.TextSearchMode;

/**
 * Text search parameters for BM25/SPLADE hybrid recall.
 *
 * @param gamma           BM25 weight in fused score (default: 0.3)
 * @param enableTextSearch enable BM25 parallel path (default: true)
 * @param textSearchMode  search mode — HYBRID, BM25_ONLY, SPLADE_ONLY, etc.
 */
public record TextSearchOptions(
        float gamma,
        boolean enableTextSearch,
        TextSearchMode textSearchMode
) {
    /** Default: hybrid text search enabled with 0.3 weight. */
    public static final TextSearchOptions DEFAULT = new TextSearchOptions(
            0.3f, true, TextSearchMode.HYBRID);

    /** Text search disabled. */
    public static final TextSearchOptions DISABLED = new TextSearchOptions(
            0.0f, false, TextSearchMode.HYBRID);
}
