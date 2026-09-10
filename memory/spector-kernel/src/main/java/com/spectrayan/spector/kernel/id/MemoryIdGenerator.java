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
package com.spectrayan.spector.kernel.id;

/**
 * Strategy interface for generating unique memory identifiers.
 *
 * <h3>Design Pattern: Strategy</h3>
 * <p>Allows pluggable ID generation strategies. The default is {@link TsidGenerator}
 * (Time-Sorted ID), which produces compact, time-ordered, distributed-safe identifiers.</p>
 *
 * <h3>Built-in Strategies</h3>
 * <ul>
 *   <li>{@link TsidGenerator} — 13-char Crockford Base32, time-sorted, distributed-safe (default)</li>
 *   <li>{@link SequenceGenerator} — monotonic AtomicLong counter, fastest, single-node only</li>
 *   <li>{@link UuidGenerator} — standard UUID v4, 36-char, maximum uniqueness guarantee</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Use built-in strategy
 *   SpectorMemory memory = SpectorMemory.builder()
 *       .idStrategy(IdStrategy.TSID)
 *       .build();
 *
 *   // Use custom generator
 *   SpectorMemory memory = SpectorMemory.builder()
 *       .idGenerator(() -> myCustomIdScheme())
 *       .build();
 * }</pre>
 *
 * @see IdStrategy
 * @see TsidGenerator
 */
@FunctionalInterface
public interface MemoryIdGenerator {

    /**
     * Generates a unique memory identifier.
     *
     * <p>Implementations must be thread-safe — this method is called from
     * virtual threads during concurrent ingestion.</p>
     *
     * @return a unique, non-null, non-empty string identifier
     */
    String generate();
}
