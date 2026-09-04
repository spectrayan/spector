/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */

/**
 * Physical record descriptors and field layouts for Spector Memory Kernel regions.
 *
 * <h2>MF-001 Specification Glossary &amp; Reserved Vocabulary</h2>
 * <p>This package defines the physical layout layer conforming to the MF-001 Memory Model v1.0.0
 * specification (§12 Physical Design). Each concept in the kernel vocabulary has exactly one owning type:</p>
 *
 * <table border="1">
 *   <caption>Reserved Vocabulary and Realized Concepts</caption>
 *   <tr><th>Term</th><th>Meaning</th><th>Owning Type</th><th>MF-001 Concept</th></tr>
 *   <tr><td><b>Engram</b></td><td>One stored memory trace</td><td>{@link com.spectrayan.spector.memory.kernel.layout.EngramLayout} (becoming {@code EngramLayout})</td><td>Trace \(T\)</td></tr>
 *   <tr><td><b>Preamble</b></td><td>Fixed prologue of a region or store file (64 bytes)</td><td>{@link com.spectrayan.spector.memory.kernel.RegionPreamble}</td><td>Physical container prologue</td></tr>
 *   <tr><td><b>Region Layout</b></td><td>Descriptor of a region's records (stride, schema, CRC)</td><td>{@link com.spectrayan.spector.memory.kernel.RegionLayout}</td><td>Physical record descriptor</td></tr>
 *   <tr><td><b>Encoding Header</b></td><td>Encoding-time identity: importance, valence, arousal, tags, source, timestamp</td><td>{@code EncodingHeaderLayout}, {@code EncodingHeader}, {@code EncodingHeaderFields}</td><td>Part of logical {@code header}</td></tr>
 *   <tr><td><b>Strength State</b></td><td>Recall dynamics: \(D\), \(S\), recall counts, ACT-R ring, last access</td><td>{@link com.spectrayan.spector.memory.kernel.layout.StrengthLayout}, {@code StrengthState}</td><td>Part of logical {@code header}</td></tr>
 *   <tr><td><b>Payload</b></td><td>Content face: vector, text, episode, skill</td><td>Vector stores, text stores, {@code EpisodeCodec}</td><td>Payload \(P\)</td></tr>
 *   <tr><td><b>Location</b></td><td>Physical address within memory</td><td>{@link com.spectrayan.spector.memory.kernel.MemoryLocation}</td><td>Location \(L\)</td></tr>
 *   <tr><td><b>Lineage</b></td><td>Derivation and provenance record</td><td>{@code LineageRecordLayout} (ADR-0029)</td><td>Lineage (NF2, M8)</td></tr>
 *   <tr><td><b>Episode</b></td><td>Payload of {@code MemoryType.EPISODIC}</td><td>{@code EpisodeRecord}</td><td>Tier payload</td></tr>
 *   <tr><td><b>Store</b></td><td>One tier's persistence, named by content only</td><td>{@code SemanticMemory}, {@code EpisodicMemory}, {@code ProceduralMemory}, {@code WorkingMemory}</td><td>Physical tier storage</td></tr>
 *   <tr><td><b>Shape</b></td><td>Access pattern: record, append, graph, chain, registry</td><td>{@link com.spectrayan.spector.memory.kernel.MemoryShape}, {@code kernel.shape.*}</td><td>Physical shape abstraction</td></tr>
 *   <tr><td><b>Accessor</b></td><td>Computes an address, then delegates to a layout</td><td>{@code *HeaderAccessor}</td><td>Physical addressing</td></tr>
 * </table>
 *
 * <h2>The Header Identity Rule</h2>
 * <p>MF-001 §4.2 defines the trace as \(T = (\text{id}, \text{tier}, \text{payload}, \text{header}, \text{loc})\).
 * In Spector, MF-001's single logical {@code header} is physically split across the immutable encoding
 * record and the mutable recall strength region (ADR-0028):</p>
 *
 * <p style="text-align: center; font-weight: bold;">
 *   \(\text{MF-001 } header \;\equiv\; \text{EncodingHeader} \;\cup\; \text{StrengthState}\)
 * </p>
 *
 * <p>Neither half may be referred to as "the header" without qualification. The term <em>header</em>
 * is strictly reserved for per-engram encoding identity; file and region prologues are <b>preambles</b>
 * (e.g. {@link com.spectrayan.spector.memory.kernel.RegionPreamble#PREAMBLE_BYTES}).</p>
 *
 * <h2>The Shape Rule</h2>
 * <p><b>Shape belongs on the shape abstraction, never on a concrete store or layout.</b>
 * Types in {@code com.spectrayan.spector.memory.kernel.shape.*} legitimately carry shape tokens
 * ({@code Record}, {@code Append}, {@code Graph}, {@code Chain}, {@code Registry}) because that is what
 * they are. Concrete stores and their layouts name their <em>content</em> only (e.g. {@code SemanticMemory},
 * {@code StrengthLayout}, {@code WalLayout}).</p>
 */
package com.spectrayan.spector.memory.kernel.layout;
