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
/**
 * Provides the per-engram encoding header — the 64-byte, cache-line-aligned record of what a
 * memory <em>was</em> at the moment it was formed.
 *
 * <p>Under MF-001 / ADR-0028 the model's single logical {@code header} is split in two:
 * this package owns the immutable or read-mostly half (importance, valence, arousal, 128-bit
 * synaptic tags, source, flags, formation timestamp), while all mutable recall telemetry —
 * recall counters, auto-LTP cooldowns, storage strength, the ACT-R ring — lives in
 * {@link StrengthLayout} / {@code StrengthMemory}. Keeping the two apart is what removes false
 * sharing from the hot scan path. The word <em>header</em> is reserved for this encoding record;
 * a file or region prologue is a {@link RegionPreamble}.</p>
 *
 * <p>{@link EncodingHeaderLayout} is the sole live base layout (V2). ADR-0030 specializes it
 * per tier so bytes 16–63 carry honest, non-punned fields: vectors and Bloom tags for
 * {@link SemanticHeaderLayout} and {@link ProceduralHeaderLayout} via
 * {@link SemanticProceduralHeaderLayout}, conversation session and model identity for
 * {@link EpisodicHeaderLayout}, and ring-buffer identity for {@link WorkingHeaderLayout}.
 * {@link HeaderBits} packs the fields that dominate scoring into one {@code long} so slab
 * scans can rank candidates without allocating.</p>
 *
 * <p>Key components include {@link EncodingHeader}, {@link EncodingHeaderLayout},
 * {@link SemanticProceduralHeaderLayout}, {@link EpisodicHeaderLayout}, and
 * {@link HeaderBits}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.engram;

import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
