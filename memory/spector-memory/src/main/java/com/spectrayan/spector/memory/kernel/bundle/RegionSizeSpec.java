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
package com.spectrayan.spector.memory.kernel.bundle;

/**
 * Specification for a single region's size requirements in a bundle.
 */
public record RegionSizeSpec(
    RegionId regionId,
    long dataBytes,      // total data bytes (excluding region's own SMKM header)
    int capacity,        // max records
    int stride,          // record stride (from store's MemoryLayout)
    int layoutId,        // store's MemoryLayout.layoutId()
    int schemaVersion,   // store's MemoryLayout.schemaVersion()
    boolean growable     // whether region can grow via relocate-to-tail
) {}
