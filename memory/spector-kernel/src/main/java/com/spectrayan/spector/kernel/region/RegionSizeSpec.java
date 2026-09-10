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
package com.spectrayan.spector.kernel.region;

import com.spectrayan.spector.kernel.layout.RegionLayout;

import com.spectrayan.spector.kernel.region.RegionId;

/**
 * Specification for a single region's size requirements in a bundle.
 */
public record RegionSizeSpec(
    RegionId regionId,
    long dataBytes,      // total data bytes (excluding region's own SMKM header)
    int capacity,        // max records
    int stride,          // record stride (from store's RegionLayout)
    int layoutId,        // store's RegionLayout.layoutId()
    int schemaVersion,   // store's RegionLayout.schemaVersion()
    boolean growable     // whether region can grow via relocate-to-tail
) {}
