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
package com.spectrayan.spector.memory.kernel.layout;

/**
 * Dedicated record layout for the Working memory tier (ADR-0030).
 *
 * <p>Pairs a 64-byte {@link WorkingHeaderLayout} with a trailing quantized vector.
 * Shares layout ID {@code 0x434F4700} with all fixed-stride engram tiers.</p>
 *
 * @param quantizedVecBytes byte length of the trailing quantized vector payload
 * @param headerLayout      dedicated working encoding header layout
 * @since 1.5.0
 * @see FixedEngramLayout
 * @see WorkingHeaderLayout
 */
public record WorkingLayout(
        int quantizedVecBytes,
        WorkingHeaderLayout headerLayout
) implements FixedEngramLayout {

    public static final int LAYOUT_ID = FixedEngramLayout.LAYOUT_ID; // 0x434F4700
    public static final int SCHEMA_VERSION = 1;

    public WorkingLayout(int quantizedVecBytes) {
        this(quantizedVecBytes, WorkingHeaderLayout.defaultLayout());
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
        return "WorkingLayout";
    }
}
