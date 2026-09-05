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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.kernel.layout.ProvenanceLayout;

/**
 * Builder-style value object for constructing a provenance write.
 *
 * <p>Used by the consolidation relay to capture the relationship between episodic
 * source turns and a consolidated semantic or procedural memory target.</p>
 *
 * @param sessionId        episodic session that produced the turns
 * @param targetTsid       raw 64-bit TSID of the consolidated memory
 * @param passNumber       consolidation pass counter for this session (1-indexed)
 * @param factIndex        index of this fact within its batch (0-indexed)
 * @param batchFactCount   total facts produced in this consolidation batch
 * @param partitionSeq     partition sequence number holding source turns
 * @param firstSeq         first episodic sequence_id in the turn range
 * @param lastSeq          last episodic sequence_id in the turn range
 * @param firstOffsetHint  region-relative byte offset hint for first turn
 * @param lastOffsetHint   region-relative byte offset hint for last turn
 * @param turnCount        number of turns covered by this edge
 * @param contentHashHi    upper 16 bits of fact text CRC32C (dedup aid)
 * @param consolidatedAtMs epoch millis when consolidation occurred
 * @param sourceKind       source kind (default: {@link ProvenanceLayout#SOURCE_EPISODIC_LOG})
 * @param targetKind       target kind ({@link ProvenanceLayout#TARGET_SEMANTIC} or
 *                         {@link ProvenanceLayout#TARGET_PROCEDURAL})
 * @param prefixKind       target ID prefix registry ordinal
 *
 * @see ProvenanceLayout
 * @see ProvenanceMemory
 * @since 1.5.0
 */
public record ProvenanceEdge(
        long sessionId,
        long targetTsid,
        short passNumber,
        byte factIndex,
        byte batchFactCount,
        int partitionSeq,
        int firstSeq,
        int lastSeq,
        int firstOffsetHint,
        int lastOffsetHint,
        short turnCount,
        short contentHashHi,
        long consolidatedAtMs,
        byte sourceKind,
        byte targetKind,
        byte prefixKind
) {

    /**
     * Creates a provenance edge with default source/target kinds for the common case:
     * episodic log → semantic memory.
     */
    public ProvenanceEdge(long sessionId, long targetTsid, short passNumber,
                          byte factIndex, byte batchFactCount,
                          int partitionSeq, int firstSeq, int lastSeq,
                          int firstOffsetHint, int lastOffsetHint,
                          short turnCount, short contentHashHi,
                          long consolidatedAtMs) {
        this(sessionId, targetTsid, passNumber, factIndex, batchFactCount,
                partitionSeq, firstSeq, lastSeq, firstOffsetHint, lastOffsetHint,
                turnCount, contentHashHi, consolidatedAtMs,
                ProvenanceLayout.SOURCE_EPISODIC_LOG,
                ProvenanceLayout.TARGET_SEMANTIC,
                (byte) 0);
    }
}
