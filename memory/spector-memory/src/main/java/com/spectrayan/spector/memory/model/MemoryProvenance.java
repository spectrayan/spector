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
package com.spectrayan.spector.memory.model;

/**
 * Public-facing API model for provenance information.
 *
 * <p>Returned by facade methods like {@code SpectorMemory.explain(memoryId)}
 * to describe the lineage between an episodic session and a consolidated memory.</p>
 *
 * @param targetId          the string ID of the consolidated semantic/procedural memory
 * @param sessionId         the episodic session that produced the source turns
 * @param partitionSeq      partition sequence number holding source turns
 * @param firstSeq          first episodic sequence_id in the turn range
 * @param lastSeq           last episodic sequence_id in the turn range
 * @param turnCount         number of turns covered
 * @param passNumber        consolidation pass number (1-indexed, supports multi-pass)
 * @param factIndex         index of this fact within its batch (0-indexed)
 * @param batchFactCount    total facts produced in the batch
 * @param consolidatedAtMs  epoch millis when consolidation occurred
 *
 * @since 1.5.0
 */
public record MemoryProvenance(
        String targetId,
        long sessionId,
        int partitionSeq,
        int firstSeq,
        int lastSeq,
        int turnCount,
        int passNumber,
        int factIndex,
        int batchFactCount,
        long consolidatedAtMs
) {}
