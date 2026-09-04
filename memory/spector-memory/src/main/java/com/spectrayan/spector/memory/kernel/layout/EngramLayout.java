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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import com.spectrayan.spector.memory.kernel.RegionLayout;

/**
 * Read/write operations for engram memory records.
 *
 * <p>An engram record = 64-byte encoding header + quantized vector payload.
 * This layout is the zero-copy Panama FFM memory layout specific to
 * {@code spector-memory}.</p>
 *
 * <h3>Biological Analog: The Synaptic Tag</h3>
 * <p>In neuroscience, synapses are "tagged" during learning (Frey &amp; Morris, 1997)
 * to mark them for later consolidation. The synaptic header is the digital
 * equivalent — a lightweight marker enabling microsecond-latency routing,
 * filtering, and scoring without touching the heavy vector payload.</p>
 *
 * <h3>Header Layout</h3>
 * <p>The {@link EncodingHeaderLayout} provides access
 * to header fields. The layout is 64 bytes (full cache line), with
 * header_version at byte 0 and synaptic_tags at the end of the core (offset 24)
 * for 128-bit growth.</p>
 *
 * @param quantizedVecBytes number of bytes for the quantized vector payload
 * @param headerLayout      the encoding header layout to use for read/write
 *
 * @see EncodingHeaderLayout
 * @see EncodingHeader
 */
public record EngramLayout(int quantizedVecBytes, EncodingHeaderLayout headerLayout) implements FixedEngramLayout {

    public static final int LAYOUT_ID = 0x434F4700; // 'COG\0'

    /**
     * Default constructor — uses the default 64-byte header layout.
     *
     * @param quantizedVecBytes bytes per quantized vector payload
     */
    public EngramLayout(int quantizedVecBytes) {
        this(quantizedVecBytes, EncodingHeaderLayout.defaultLayout());
    }

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public int recordStride() {
        return stride();
    }

    /**
     * Returns whether CRC32C integrity checksumming is enabled for fixed-stride engram records.
     *
     * <h3>Design Decision D3 (Integrity Gap &amp; Performance Tradeoff / R10.1, R10.2)</h3>
     * <p>CRC32C is intentionally <b>disabled</b> (returns {@code false}) for fixed-stride engram records.
     * In the current implementation, {@link com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory}'s
     * CRC verification path allocates a temporary heap {@code byte[]} array and a {@link java.util.zip.CRC32C}
     * instance per read operation. On the SIMD vector scan and recall hot path, millions of engram records
     * may be evaluated per second; introducing heap allocations and copies into that loop would violate
     * Spector's core zero-allocation hot-path guarantee and trigger severe GC pressure.</p>
     *
     * <p>By contrast, CRC32C checksumming is enabled for:</p>
     * <ul>
     *     <li><b>Strength Region ({@code StrengthLayout})</b>: Point updates and lookups off the hot vector scan loop.</li>
     *     <li><b>Variable-Length Append Records (e.g. {@code WalLayout}, {@code TextBlobLayout})</b>: Where sequential
     *         write integrity across log segments is paramount and access is I/O-dominated.</li>
     * </ul>
     *
     * <p>Revisiting CRC32C for engrams is deferred until a zero-copy, direct off-heap {@link java.lang.foreign.MemorySegment}-based
     * hardware-accelerated CRC calculation path is introduced that does not perturb the cache-line stride or
     * allocate on the heap.</p>
     *
     * @return {@code false} to preserve the zero-allocation vector scan hot path
     */
    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "EngramLayout";
    }

    /**
     * Total bytes per record (header + payload).
     */
    public int stride() {
        return headerLayout.headerBytes() + quantizedVecBytes;
    }

    /**
     * Offset where the quantized vector payload begins within a record.
     */
    public long vectorOffset(long recordOffset) {
        return recordOffset + headerLayout.headerBytes();
    }

    // ── Write operations (delegate to HeaderLayout) ──

    /**
     * Writes a complete encoding header to the given segment at the specified record offset.
     */
    public void writeHeader(MemorySegment segment, long offset, EncodingHeader header) {
        headerLayout.writeHeader(segment, offset, header);
    }

    /**
     * Reads a complete encoding header from the given segment at the specified record offset.
     */
    public EncodingHeader readHeader(MemorySegment segment, long offset) {
        return headerLayout.readHeader(segment, offset);
    }

    // ── Field-level accessors (delegate to HeaderLayout) ──

    /** Reads the flags byte at the given record offset. */
    public byte readFlags(MemorySegment segment, long offset) {
        return headerLayout.readFlags(segment, offset);
    }

    /** Reads the synaptic tags (Bloom filter) at the given record offset. */
    public long readSynapticTags(MemorySegment segment, long offset) {
        return headerLayout.readSynapticTags(segment, offset);
    }

    /** Reads the low 64 bits of the synaptic tags Bloom filter. */
    public long readSynapticTagsLo(MemorySegment segment, long offset) {
        return headerLayout.readSynapticTagsLo(segment, offset);
    }

    /** Reads the high 64 bits of the synaptic tags Bloom filter. */
    public long readSynapticTagsHi(MemorySegment segment, long offset) {
        return headerLayout.readSynapticTagsHi(segment, offset);
    }

    /** Writes a 128-bit synaptic tag filter (low and high 64 bits). */
    public void writeSynapticTags(MemorySegment segment, long offset, long tagsLo, long tagsHi) {
        headerLayout.writeSynapticTags(segment, offset, tagsLo, tagsHi);
    }

    /** Merges 128-bit synaptic tags via atomic bitwise OR. */
    public void mergeSynapticTags128(MemorySegment segment, long offset, long tagsLo, long tagsHi) {
        headerLayout.mergeSynapticTags128(segment, offset, tagsLo, tagsHi);
    }

    /**
     * Reads the source modality (TEXT, IMAGE, AUDIO, VIDEO) from the flags byte.
     *
     * <p>Extracts bits 6-7 from the flags byte and maps to a {@link SourceModality}.
     * Existing records with zeroed bits return {@code SourceModality.TEXT}.</p>
     */
    public SourceModality readSourceModality(MemorySegment segment, long offset) {
        byte flags = headerLayout.readFlags(segment, offset);
        return SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags));
    }

    /**
     * Reads the 1-byte source code at the given record offset (zero-allocation hot path).
     */
    public byte readSourceCode(MemorySegment segment, long offset) {
        return headerLayout.readSourceCode(segment, offset);
    }

    /**
     * Reads the trace provenance source enum from offset 46 (NF7).
     */
    public EngramSource readSource(MemorySegment segment, long offset) {
        return headerLayout.readSource(segment, offset);
    }

    /**
     * Writes the trace provenance source enum to offset 46 (NF7).
     */
    public void writeSource(MemorySegment segment, long offset, EngramSource source) {
        headerLayout.writeSource(segment, offset, source);
    }

    /** Reads the valence byte at the given record offset. */
    public byte readValence(MemorySegment segment, long offset) {
        return headerLayout.readValence(segment, offset);
    }

    /** Reads the timestamp at the given record offset. */
    public long readTimestamp(MemorySegment segment, long offset) {
        return headerLayout.readTimestamp(segment, offset);
    }

    /** Reads the importance at the given record offset. */
    public float readImportance(MemorySegment segment, long offset) {
        return headerLayout.readImportance(segment, offset);
    }

    /** Reads the recall count at the given record offset. */
    public int readAgentRecallCount(MemorySegment segment, long offset) {
        return headerLayout.readAgentRecallCount(segment, offset);
    }

    /** Reads the arousal byte (unsigned 0-255). Returns 0 on V1 layouts. */
    public byte readArousal(MemorySegment segment, long offset) {
        return headerLayout.readArousal(segment, offset);
    }

    /** Reads the storage strength. Returns 1.0f on V1 layouts. */
    public float readStorageStrength(MemorySegment segment, long offset) {
        return headerLayout.readStorageStrength(segment, offset);
    }

    /** Reads the exact norm at the given record offset. */
    public float readExactNorm(MemorySegment segment, long offset) {
        return headerLayout.readExactNorm(segment, offset);
    }

    /** Reads the centroid ID at the given record offset. */
    public short readCentroidId(MemorySegment segment, long offset) {
        return headerLayout.readCentroidId(segment, offset);
    }

    /**
     * Increments the recall count (reconsolidation / LTP reinforcement).
     *
     * <h3>Semantic Note</h3>
     * <p>As of the agent_recall_count inflation fix, this is only called from
     * {@code SpectorMemory.reinforce()}, meaning agent_recall_count represents
     * "times the agent explicitly found this useful" — not "times it appeared
     * in search results." This produces more meaningful LTP adjustment.</p>
     *
     * <h3>Thread Safety</h3>
     * <p>Uses a thread-safe atomic getAndAdd operation via {@link java.lang.invoke.VarHandle}.
     * This guarantees atomicity and zero race conditions under heavy concurrent
     * reinforcement workloads on modern multicore CPUs.</p>
     *
     * @return the previous recall count value
     */
    public int incrementAgentRecallCount(MemorySegment segment, long offset) {
        return headerLayout.incrementAgentRecallCount(segment, offset);
    }

    /** Reads the spector-internal recall count. Returns 0 on V1/V2 layouts. */
    public int readSpectorRecallCount(MemorySegment segment, long offset) {
        return headerLayout.readSpectorRecallCount(segment, offset);
    }

    /**
     * Atomically increments the spector-internal recall count (auto-LTP).
     *
     * <p>Unlike {@code incrementAgentRecallCount()}, this is called automatically
     * by the recall pipeline when a memory surfaces in results, subject to
     * a cooldown to prevent inflation from repeated queries.</p>
     *
     * @return the previous spector recall count value
     */
    public int incrementSpectorRecallCount(MemorySegment segment, long offset) {
        return headerLayout.incrementSpectorRecallCount(segment, offset);
    }

    /** Reads the last auto-LTP timestamp. Returns 0L on V1/V2 layouts. */
    public long readLastAutoLtp(MemorySegment segment, long offset) {
        return headerLayout.readLastAutoLtp(segment, offset);
    }

    /** Writes the last auto-LTP timestamp. No-op on V1/V2 layouts. */
    public void writeLastAutoLtp(MemorySegment segment, long offset, long timestampMs) {
        headerLayout.writeLastAutoLtp(segment, offset, timestampMs);
    }

    /** Sets the tombstone flag (logical deletion / pruning by Deep Sleep). */
    public void tombstone(MemorySegment segment, long offset) {
        headerLayout.markTombstoned(segment, offset);
    }

    /** Sets the consolidated flag (memory has been reflected into Semantic tier). */
    public void markConsolidated(MemorySegment segment, long offset) {
        headerLayout.markConsolidated(segment, offset);
    }

    /**
     * Sets the pinned flag (memory is exempt from decay and pruning).
     *
     * <p>Used by neurodivergent lossless consolidation (SYSTEMATIZER profile)
     * to pin source episodes during REM sleep, preserving encyclopedic detail
     * alongside the synthesized semantic fact.</p>
     */
    public void pin(MemorySegment segment, long offset) {
        headerLayout.markPinned(segment, offset);
    }

    /**
     * Sets the resolved flag (Zeigarnik Effect — marks a task/issue as done).
     *
     * <p>Once resolved, the memory succumbs to normal time-decay and gradually
     * fades from active recall. Call {@link #markUnresolved} if the issue resurfaces.</p>
     */
    public void markResolved(MemorySegment segment, long offset) {
        headerLayout.markResolved(segment, offset);
    }

    /**
     * Clears the resolved flag (Zeigarnik Effect — re-opens a task/issue).
     *
     * <p>The memory re-enters the Zeigarnik loop: it resists decay and floats
     * to the top of recall until explicitly resolved again.</p>
     */
    public void markUnresolved(MemorySegment segment, long offset) {
        headerLayout.markUnresolved(segment, offset);
    }

    /** Reads the consolidation flags byte (offset 34). */
    public byte readConsolidationFlags(MemorySegment segment, long offset) {
        return headerLayout.readConsolidationFlags(segment, offset);
    }

    /** Sets the contradicted flag (bit 0 of consolidation flags byte). */
    public void markContradicted(MemorySegment segment, long offset) {
        headerLayout.markContradicted(segment, offset);
    }

    /** Updates the importance field. */
    public void writeImportance(MemorySegment segment, long offset, float importance) {
        headerLayout.writeImportance(segment, offset, importance);
    }

    /** Updates the timestamp field. */
    public void writeTimestamp(MemorySegment segment, long offset, long timestampMs) {
        headerLayout.writeTimestamp(segment, offset, timestampMs);
    }

    /** Merges synaptic tags by ORing the existing tags with new ones. */
    public void mergeSynapticTags(MemorySegment segment, long offset, long additionalTags) {
        headerLayout.mergeSynapticTags(segment, offset, additionalTags);
    }

    /** Writes the arousal byte. No-op on V1 layouts. */
    public void writeArousal(MemorySegment segment, long offset, byte arousal) {
        headerLayout.writeArousal(segment, offset, arousal);
    }

    /** Writes the storage strength. No-op on V1 layouts. */
    public void writeStorageStrength(MemorySegment segment, long offset, float strength) {
        headerLayout.writeStorageStrength(segment, offset, strength);
    }

    /** Reads the encoding-time cognitive profile byte. Returns 0 for pre-V3 records. */
    public byte readEncodingProfile(MemorySegment segment, long offset) {
        return headerLayout.readEncodingProfile(segment, offset);
    }

    /** Reads the quantized alpha at encoding time. */
    public byte readEncodingAlpha(MemorySegment segment, long offset) {
        return headerLayout.readEncodingAlpha(segment, offset);
    }

    /** Reads the quantized beta at encoding time. */
    public byte readEncodingBeta(MemorySegment segment, long offset) {
        return headerLayout.readEncodingBeta(segment, offset);
    }

    /** Reads the soul version counter. */
    public short readSoulVersion(MemorySegment segment, long offset) {
        return headerLayout.readSoulVersion(segment, offset);
    }

    /** Writes the soul version counter. */
    public void writeSoulVersion(MemorySegment segment, long offset, short version) {
        headerLayout.writeSoulVersion(segment, offset, version);
    }

    /** Reads the surprise z-score at encoding time. */
    public float readEncodingSurprise(MemorySegment segment, long offset) {
        return headerLayout.readEncodingSurprise(segment, offset);
    }

    /** Writes the surprise z-score at encoding time. */
    public void writeEncodingSurprise(MemorySegment segment, long offset, float surprise) {
        headerLayout.writeEncodingSurprise(segment, offset, surprise);
    }

    /**
     * Writes a pre-quantized vector payload to the segment at the record's vector offset.
     *
     * @param segment      off-heap memory segment
     * @param recordOffset byte offset of the record start
     * @param quantizedVec pre-quantized byte array (e.g., from ScalarQuantizer.encode())
     */
    public void writeQuantizedVector(MemorySegment segment, long recordOffset, byte[] quantizedVec) {
        MemorySegment.copy(MemorySegment.ofArray(quantizedVec), ValueLayout.JAVA_BYTE, 0,
                segment, ValueLayout.JAVA_BYTE, vectorOffset(recordOffset), quantizedVec.length);
    }

    /**
     * Quantizes a float32 vector using a calibrated {@link ScalarQuantizer} and writes
     * the result directly to the segment at the record's vector offset.
     *
     * @param segment      off-heap memory segment
     * @param recordOffset byte offset of the record start
     * @param vector       float32 vector to quantize
     * @param quantizer    calibrated ScalarQuantizer
     */
    public void writeQuantizedVector(MemorySegment segment, long recordOffset,
                                      float[] vector, ScalarQuantizer quantizer) {
        byte[] quantized = quantizer.encode(vector);
        writeQuantizedVector(segment, recordOffset, quantized);
    }
}
