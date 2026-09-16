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
 * Provides unchecked, raw access to bundle bytes for offline tooling — inspection, migration,
 * and repair — bypassing every safety mechanism the kernel normally enforces.
 *
 * <p>Engine code must never call into this package. Ordinary access goes through
 * {@link RegionRef} and {@link RegionLease}, which survive region growth and keep the backing
 * {@link java.lang.foreign.Arena} alive for the duration of an operation. Callers here hold a
 * raw segment instead and assume all remap and lifetime hazards themselves: a concurrent
 * {@code growRegion} invalidates their view with no warning.</p>
 *
 * <p>Permitted callers are {@code spector-inspect}, {@code spector-cli}, and test sources.
 * The restriction is enforced by a JPMS qualified export where the module path is available,
 * and by ArchUnit rules otherwise.</p>
 *
 * <p>Key components include {@link RawBundleAccess}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.unsafe;

import com.spectrayan.spector.kernel.bundle.RegionLease;
import com.spectrayan.spector.kernel.bundle.RegionRef;
