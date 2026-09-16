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
 * Provides binary framing and payload codecs for the variable-length records that fixed-stride
 * layouts cannot describe.
 *
 * <p>A store backed by {@code RecordMemory} gets its field offsets from a {@code RegionLayout}
 * because every record is the same width. Append-shaped stores are different: the record is a
 * fixed prefix followed by a payload whose length is known only at write time. This package
 * owns the encoding of those payloads, keeping the byte work out of the store itself.</p>
 *
 * <p>{@link EpisodeCodec} encodes a conversation turn for {@link EpisodicMemory}
 * (ADR-0010 / ADR-0030, D2 Option B): a 35-byte metadata prefix — role, session TSID, model
 * registry id, token counts, latency, user TSID, body length — followed by the raw CBOR body,
 * written at {@code recordOffset + 80} after the 16-byte framing prefix and the 64-byte
 * encoding header.</p>
 *
 * <p>Key components include {@link EpisodeCodec}.</p>
 *
 * @author Spectrayan Maintainers
 * @since 1.4.0
 */
package com.spectrayan.spector.kernel.store.codec;

import com.spectrayan.spector.kernel.store.EpisodicMemory;
