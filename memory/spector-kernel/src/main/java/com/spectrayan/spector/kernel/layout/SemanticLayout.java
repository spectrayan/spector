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
package com.spectrayan.spector.kernel.layout;

import com.spectrayan.spector.kernel.engram.SemanticHeaderLayout;

/**
 * Dedicated record layout for the Semantic memory tier (ADR-0030).
 *
 * <p>Pairs a 64-byte {@link SemanticHeaderLayout} with a trailing quantized vector.
 * Shares layout ID {@code 0x434F4700} with all fixed-stride engram tiers.</p>
 *
 * @param quantizedVecBytes byte length of the trailing quantized vector payload
 * @param headerLayout      dedicated semantic encoding header layout
 * @since 1.5.0
 * @see FixedEngramLayout
 * @see SemanticHeaderLayout
 */
public record SemanticLayout(
        int quantizedVecBytes,
        SemanticHeaderLayout headerLayout
) implements FixedEngramLayout {

    public static final int LAYOUT_ID = FixedEngramLayout.LAYOUT_ID; // 0x434F4700
    public static final int SCHEMA_VERSION = 1;

    public SemanticLayout(int quantizedVecBytes) {
        this(quantizedVecBytes, SemanticHeaderLayout.defaultLayout());
    }

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public String name() {
        return "SemanticLayout";
    }
}
