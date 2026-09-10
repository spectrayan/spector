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
 * Pluggable ID generation for cognitive memories.
 *
 * <h3>Design</h3>
 * <p>Provides a {@link com.spectrayan.spector.kernel.id.MemoryIdGenerator} strategy
 * interface with built-in implementations:</p>
 * <ul>
 *   <li>{@link com.spectrayan.spector.kernel.id.TsidGenerator} — time-sorted, 13-char, distributed-safe (default)</li>
 *   <li>{@link com.spectrayan.spector.kernel.id.UuidGenerator} — standard UUID v4, 36-char</li>
 *   <li>{@link com.spectrayan.spector.kernel.id.SequenceGenerator} — monotonic counter, fastest, single-node only</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 *   SpectorMemory memory = SpectorMemory.builder()
 *       .idStrategy(IdStrategy.TSID)    // built-in
 *       .idGenerator(myCustomGen)       // or custom
 *       .build();
 * }</pre>
 */
package com.spectrayan.spector.kernel.id;

import com.spectrayan.spector.kernel.id.MemoryId;
