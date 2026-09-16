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
 * Provides read-only knowledge of the legacy V3 on-disk storage layout, quarantined away from
 * the live V4 bundle code.
 *
 * <p>In V3 each runtime structure and partition tier was a standalone flat file
 * ({@code working.mem}, {@code hebbian.graph}, {@code semantic.mem}, …). ADR-0004 consolidated
 * them into {@code runtime.bundle} and {@code partition.bundle}. The path and file-name
 * resolvers kept here exist solely so migration tooling can still locate and read V3 artifacts;
 * nothing in this package writes the legacy format, and engine code must not depend on it.</p>
 *
 * <p>Key components include {@link LegacyV3BundleFormat}, which resolves legacy directory and
 * file names for the bundle migration path into {@link RuntimeBundle} and
 * {@link PartitionBundle}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.bundle.compat;

import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
