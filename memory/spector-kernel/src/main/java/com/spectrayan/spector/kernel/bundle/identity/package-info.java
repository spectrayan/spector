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
 * Provides the identity-plane bundle (ADR-0029 §23) that stores an account's or tenant's
 * identity, salience, continuity, and policy state outside the data-plane rememberer.
 *
 * <p>An identity bundle is a lightweight single-file-descriptor mmap container: a 64-byte
 * {@link RegionPreamble} with layout {@code IDNT}, a 64-byte {@code SIDB} sub-header, and a
 * directory of up to 16 {@link IdentityRegionEntry} records, with page-aligned payloads of
 * 64&nbsp;KB by default. Because it carries no rememberer engine, an open identity bundle does
 * not count against {@code maxHotNamespaces}.</p>
 *
 * <p>{@link IdentityRegionId} is deliberately distinct from the data-plane
 * {@link com.spectrayan.spector.kernel.region.RegionId}: the two planes evolve independently
 * and must never share region numbering.</p>
 *
 * <p>Key components include {@link IdentityBundle}, {@link IdentityBundleHeader},
 * {@link IdentityRegionEntry}, and {@link IdentityRegionId}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.bundle.identity;

import com.spectrayan.spector.kernel.region.RegionPreamble;
