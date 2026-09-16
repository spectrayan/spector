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
 * Provides read-only decoders for superseded engram header formats, quarantined away from the
 * live encoding layouts.
 *
 * <p>The legacy V1 header mixed encoding identity with recall telemetry in one 64-byte record.
 * ADR-0028 split those concerns, so V1 field offsets no longer describe anything the kernel
 * writes. They are kept here — and only here — so migration tooling such as
 * {@code HeaderMigrator} and backward-compatibility readers can still interpret old files
 * (spec task 3.4, requirements R2.1 and R3.1). Nothing in this package writes V1 bytes, and
 * live kernel code must not reference these offsets.</p>
 *
 * <p>Key components include {@link LegacyEncodingHeaderReader}, which decodes V1 headers into
 * the current {@link EncodingHeader} shape.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.engram.compat;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
