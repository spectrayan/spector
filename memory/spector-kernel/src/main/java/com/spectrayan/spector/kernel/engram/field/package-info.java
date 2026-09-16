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
 * Provides the byte offsets and field constants of the 64-byte engram encoding header, separated
 * from the accessor layouts that read and write them.
 *
 * <p>These constants are the on-disk contract: every offset here is persisted in bundle files
 * and may never change without a format migration. {@link EncodingHeaderFields} defines the
 * V2 pure-encoding header shared by all tiers (bytes 0–15 plus the trailing provenance and
 * soul fields), while the per-tier classes claim bytes 16–63 for their own meanings under
 * ADR-0030: {@link SemanticProceduralHeaderFields} for vector quantization metadata and
 * 128-bit synaptic tags, {@link EpisodicHeaderFields} for session TSID, model registry id,
 * turn role, and episode context tags.</p>
 *
 * <p>Splitting the constants per tier is what prevents field punning — an episodic session id
 * can no longer be read through a semantic centroid offset, because the two never share a
 * constants class.</p>
 *
 * <p>Key components include {@link EncodingHeaderFields}, {@link EpisodicHeaderFields}, and
 * {@link SemanticProceduralHeaderFields}; the matching accessors live in
 * {@link com.spectrayan.spector.kernel.engram}.</p>
 *
 * @author Spectrayan Maintainers
 */
package com.spectrayan.spector.kernel.engram.field;
