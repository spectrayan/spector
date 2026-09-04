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

import com.spectrayan.spector.memory.kernel.RegionLayout;

/**
 * Dedicated record layout for the Episodic memory tier (ADR-0030).
 *
 * <p>Variable-length append-only record layout pairing a 16-byte framing prefix,
 * a 64-byte {@link EpisodicHeaderLayout}, and a variable-length CBOR payload.</p>
 *
 * @param headerLayout dedicated episodic encoding header layout
 * @since 1.5.0
 * @see EpisodicHeaderLayout
 * @see EpisodeLayout
 */
public record EpisodicLayout(
        EpisodicHeaderLayout headerLayout
) implements RegionLayout {

    public static final int LAYOUT_ID = EpisodeLayout.LAYOUT_ID; // 0x4550494C ('EPIL')
    public static final int VERSION = EpisodeLayout.VERSION;     // 2
    public static final int PREFIX_BYTES = EpisodeLayout.PREFIX_BYTES; // 16
    public static final int HEADER_BYTES = EpisodeLayout.HEADER_BYTES; // 64
    public static final int FIXED_OVERHEAD_BYTES = EpisodeLayout.FIXED_OVERHEAD_BYTES; // 80
    public static final int MAGIC = EpisodeLayout.MAGIC; // 0x45504953 ('EPIS')

    public static final EpisodicLayout INSTANCE = new EpisodicLayout();

    public EpisodicLayout() {
        this(EpisodicHeaderLayout.defaultLayout());
    }

    public static EpisodicLayout defaultLayout() {
        return INSTANCE;
    }

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    @Override
    public int recordStride() {
        return 0; // variable-length
    }

    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "EpisodicLayout";
    }
}
