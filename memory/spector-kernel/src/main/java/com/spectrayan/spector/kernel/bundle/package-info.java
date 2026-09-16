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
 * Provides the V4 bundle container (ADR-0004) that packs many memory regions into a single
 * mmap'd file sharing one {@link java.lang.foreign.Arena}, replacing the V3 layout of one
 * standalone file per store.
 *
 * <p>A bundle file opens with a 64-byte {@link RegionPreamble}, followed by a 64-byte
 * {@link BundleSubHeader} and a {@link RegionEntry} directory; region payloads begin at the
 * first page boundary. Two concrete bundles exist: {@link PartitionBundle} packs the four
 * fixed-size cognitive tier regions of one partition, while {@link RuntimeBundle} packs the
 * global runtime stores and supports growing a region by relocating it to the tail of the
 * file and remapping.</p>
 *
 * <p>Because growth invalidates every previously resolved slice, callers never hold a raw
 * segment. {@link RegionRef} resolves {@code (bundle, regionId, generation)} on each access,
 * and batch operations hold a {@link RegionLease} for the duration of a scan, cursor, or
 * checkpoint so the arena cannot close underneath them.</p>
 *
 * <p>Key components include {@link AbstractBundle}, {@link PartitionBundle},
 * {@link RuntimeBundle}, {@link BundleDirectory}, {@link BundleManager},
 * {@link RegionOpener}, {@link RegionRef}, and {@link RegionLease}.</p>
 *
 * @author Spectrayan Maintainers
 * @since 1.2.0
 */
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionPreamble;
