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
package com.spectrayan.spector.kernel.api;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

/**
 * Top-level in-memory representation of an episodic conversation turn.
 *
 * <p>Carries the decoded encoding header fields alongside conversation metadata
 * and raw payload bytes. Realizes the MF-001 episodic trace payload contract (M4, NF6, NF7).</p>
 *
 * @param role        conversation role (USER, ASSISTANT, SYSTEM, etc.)
 * @param sequenceId  monotonic turn counter per session (ADR-0029 lineage anchor)
 * @param timestampMs epoch milliseconds when the turn was created
 * @param sessionId   8B TSID hash identifying the conversation session
 * @param bodyLength  payload byte count
 * @param body        raw payload bytes (null if read without body)
 * @param modelId     LLM model registry ID
 * @param tokenIn     input token count
 * @param tokenOut    output token count
 * @param latencyMs   response generation latency in milliseconds
 * @param userId      user/tenant 8B TSID hash
 * @param soulVersion agent soul configuration version
 * @param modality    source modality (TEXT, IMAGE, AUDIO, VIDEO)
 * @param flags       raw header flags byte
 * @param importance  salience importance score (NF6)
 * @param valence     emotional valence byte [-128, 127] (NF6)
 * @param arousal     emotional arousal byte [-128, 127] (NF6)
 * @param source      engram provenance source (NF7)
 *
 * @since 1.4.0
 */
public record EpisodeRecord(
        ConversationRole role,
        int sequenceId,
        long timestampMs,
        long sessionId,
        int bodyLength,
        byte[] body,
        short modelId,
        int tokenIn,
        int tokenOut,
        int latencyMs,
        long userId,
        short soulVersion,
        SourceModality modality,
        byte flags,
        float importance,
        byte valence,
        byte arousal,
        EngramSource source
) {

    /**
     * Backward-compatible constructor for 14-argument legacy callers.
     */
    public EpisodeRecord(ConversationRole role,
                         int sequenceId,
                         long timestampMs,
                         long sessionId,
                         int bodyLength,
                         byte[] body,
                         short modelId,
                         int tokenIn,
                         int tokenOut,
                         int latencyMs,
                         long userId,
                         short soulVersion,
                         SourceModality modality,
                         byte flags) {
        this(role, sequenceId, timestampMs, sessionId, bodyLength, body, modelId, tokenIn, tokenOut,
                latencyMs, userId, soulVersion, modality, flags, 0.0f, (byte) 0, (byte) 0, EngramSource.EXPERIENCED);
    }

    /**
     * Checks if this episodic record is logically tombstoned.
     */
    public boolean isTombstoned() {
        return (flags & com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.FLAG_TOMBSTONE) != 0;
    }

    /**
     * Checks if this episodic record has been consolidated into semantic memory.
     */
    public boolean isConsolidated() {
        return (flags & com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.FLAG_CONSOLIDATED) != 0;
    }
}
