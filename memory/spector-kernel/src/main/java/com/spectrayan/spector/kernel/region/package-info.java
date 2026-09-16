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
 * Provides storage identity for a region: the prologue, directory entry, and stable identifiers
 * that describe <em>where</em> memories live, never what any individual memory contains.
 *
 * <p>{@link RegionPreamble} reads and writes the standardized 64-byte {@code SMKM} prologue that
 * opens every kernel file and every region slice inside a bundle — magic, schema version,
 * {@link MemoryShape}, persistence flags, capacity and count, record stride, persisted
 * {@code layoutId}, timestamps, and a CRC32C over the preceding 56 bytes. A preamble is not a
 * header: the term <em>header</em> is reserved for the per-engram encoding record in
 * {@link com.spectrayan.spector.kernel.engram}, which is why {@code PREAMBLE_BYTES} deliberately
 * avoids the word.</p>
 *
 * <p>{@link RegionEntry} is the 64-byte directory entry a bundle stores per region,
 * {@link RegionId} assigns the stable numeric ids those entries key on, and
 * {@link RegionSizeSpec} carries a region's size requirements when a bundle is first laid out.
 * Every offset in this package is persisted on disk and must remain byte-stable.</p>
 *
 * <p>Key components include {@link RegionPreamble}, {@link RegionEntry}, {@link RegionId}, and
 * {@link RegionSizeSpec}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.region;

import com.spectrayan.spector.kernel.shape.MemoryShape;
