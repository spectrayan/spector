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

import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Episodic field accessor — reinterprets the 64-byte
 * {@link SynapticHeaderConstants synaptic header} for conversation records.
 *
 * <h3>Design Rationale</h3>
 * <p>The same 64-byte cache-line-aligned header is shared across all memory tiers
 * ({@code WORKING}, {@code EPISODIC}, {@code SEMANTIC}, {@code PROCEDURAL}).
 * When the {@link MemoryType} bits in the flags byte (bits 1-2) indicate
 * {@code EPISODIC}, several fields carry conversation-specific semantics
 * instead of their cognitive defaults:</p>
 *
 * <pre>
 *   Offset  Size  Cognitive Field          Episodic Field
 *   ──────  ────  ──────────────────────   ──────────────────────────
 *    0      1B    header_version           header_version (same)
 *    1      1B    flags                    flags (same bit layout)
 *    2      1B    valence                  role (ConversationRole ordinal)
 *    3      1B    arousal                  reserved
 *    4      4B    importance (float)       sequence_id (int32)
 *    8      8B    timestamp_ms             timestamp_ms (same)
 *   16      4B    agent_recall_count       token_in_count (int32)
 *   20      4B    exact_norm               token_out_count (int32)
 *   24      8B    synaptic_tags (Bloom)    session_id (8B TSID hash)
 *   32      2B    centroid_id              model_id (short)
 *   34      1B    consolidation_flags      conversation_flags
 *   35      1B    encoding_profile         reserved
 *   36      4B    storage_strength         latency_ms (int32)
 *   40      4B    spector_recall_cnt       reserved
 *   44-45   2B    encoding_alpha/beta      reserved
 *   46      2B    soul_version             soul_version (same)
 *   48      8B    last_auto_ltp            user_id (8B TSID hash)
 *   56      4B    encoding_surprise        body_length (int32)
 *   60      1B    last_recall_profile      reserved
 *   61-63   3B    _reserved                _reserved
 * </pre>
 *
 * <p>This accessor operates on the same {@link MemorySegment} offsets as
 * {@link HeaderLayout64} but interprets the bytes with conversation semantics.
 * Both accessors are compatible — the underlying binary format is identical.</p>
 *
 * @since 1.3.0
 * @see HeaderLayout64
 * @see SynapticHeaderConstants
 * @see ConversationRole
 */
public final class EpisodicFieldAccessor {

    private EpisodicFieldAccessor() {} // static utility

    // ── Read accessors ──

    /**
     * Reads the conversation role from the valence byte (offset 2).
     */
    public static ConversationRole readRole(MemorySegment segment, long offset) {
        byte ordinal = segment.get(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_VALENCE);
        return ConversationRole.fromOrdinal(ordinal & 0xFF);
    }

    /**
     * Reads the monotonic sequence ID from the importance field (offset 4).
     *
     * <p>Reinterprets the 4-byte importance float as an int32 sequence counter.
     * This is a raw bit reinterpretation — the stored bytes are an integer,
     * not a float, despite sharing the same offset.</p>
     */
    public static int readSequenceId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_IMPORTANCE);
    }

    /**
     * Reads the timestamp in epoch milliseconds (offset 8).
     * Same interpretation as cognitive records.
     */
    public static long readTimestamp(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_TIMESTAMP);
    }

    /**
     * Reads the session ID from the synaptic_tags field (offset 24).
     *
     * <p>In episodic records, this is a raw 8-byte TSID hash — NOT a Bloom
     * filter. The session ID is the first 8 bytes of the TSID's hash,
     * enabling direct equality comparison for session-scoped queries.</p>
     */
    public static long readSessionId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_SYNAPTIC_TAGS);
    }

    /**
     * Reads the input token count from the agent_recall_count field (offset 16).
     */
    public static int readTokenInCount(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_AGENT_RECALL_COUNT);
    }

    /**
     * Reads the output token count from the exact_norm field (offset 20).
     *
     * <p>Reinterprets the 4-byte exact_norm float as an int32 token counter.</p>
     */
    public static int readTokenOutCount(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_EXACT_NORM);
    }

    /**
     * Reads the LLM model registry ID from the centroid_id field (offset 32).
     */
    public static short readModelId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_SHORT, offset + SynapticHeaderConstants.OFFSET_CENTROID_ID);
    }

    /**
     * Reads the response generation latency in milliseconds (offset 36).
     *
     * <p>Reinterprets the 4-byte storage_strength float as an int32 latency counter.</p>
     */
    public static int readLatencyMs(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_STORAGE_STRENGTH);
    }

    /**
     * Reads the user/tenant ID from the last_auto_ltp field (offset 48).
     *
     * <p>8-byte TSID hash identifying the user or tenant who owns this
     * conversation session.</p>
     */
    public static long readUserId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_LAST_AUTO_LTP);
    }

    /**
     * Reads the CBOR body length in bytes from the encoding_surprise field (offset 56).
     *
     * <p>Reinterprets the 4-byte encoding_surprise float as an int32 length.
     * This value determines the variable stride: the next record begins at
     * {@code offset + 64 + bodyLength}.</p>
     */
    public static int readBodyLength(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_ENCODING_SURPRISE);
    }

    /**
     * Reads the flags byte (offset 1). Same interpretation as cognitive records.
     */
    public static byte readFlags(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_FLAGS);
    }

    /**
     * Reads the conversation flags byte (offset 34).
     */
    public static byte readConversationFlags(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_CONSOLIDATION_FLAGS);
    }

    /**
     * Reads the soul version (offset 46). Same interpretation as cognitive records.
     */
    public static short readSoulVersion(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_SHORT, offset + SynapticHeaderConstants.OFFSET_SOUL_VERSION);
    }

    /**
     * Extracts the source modality from the flags byte (bits 6-7).
     */
    public static SourceModality readModality(MemorySegment segment, long offset) {
        byte flags = readFlags(segment, offset);
        return SourceModality.fromOrdinal(SynapticHeaderConstants.sourceModalityOrdinal(flags));
    }

    /**
     * Checks if this record has been tombstoned (logically deleted).
     */
    public static boolean isTombstoned(MemorySegment segment, long offset) {
        return SynapticHeaderConstants.isTombstoned(readFlags(segment, offset));
    }

    /**
     * Checks if the given flags byte has the tombstone bit set.
     */
    public static boolean isTombstoned(byte flags) {
        return SynapticHeaderConstants.isTombstoned(flags);
    }

    /**
     * Checks if this record has been consolidated (reflected into SEMANTIC tier).
     */
    public static boolean isConsolidated(MemorySegment segment, long offset) {
        return SynapticHeaderConstants.isConsolidated(readFlags(segment, offset));
    }

    /**
     * Checks if the given flags byte has the consolidated bit set.
     */
    public static boolean isConsolidated(byte flags) {
        return SynapticHeaderConstants.isConsolidated(flags);
    }

    // ── Write accessors ──

    /**
     * Writes a complete episodic header to the given segment at the specified offset.
     *
     * <p>This is the primary write method for constructing episodic records.
     * It writes all 64 bytes using the episodic field interpretation.</p>
     *
     * @param segment     the target mmap segment
     * @param offset      byte offset where the 64B header begins
     * @param role        conversation role (stored in valence byte)
     * @param sequenceId  monotonic turn counter per session (stored as int in importance field)
     * @param timestampMs epoch milliseconds
     * @param sessionId   8B TSID hash (stored in synaptic_tags field)
     * @param bodyLength  CBOR payload byte count (stored as int in encoding_surprise field)
     * @param modelId     LLM model registry ID (stored in centroid_id field)
     * @param tokenIn     input token count (stored in agent_recall_count field)
     * @param tokenOut    output token count (stored in exact_norm field)
     * @param latencyMs   response generation latency in ms (stored in storage_strength field)
     * @param userId      user/tenant 8B TSID hash (stored in last_auto_ltp field)
     * @param soulVersion agent soul configuration version
     * @param modality    source modality (TEXT, IMAGE, AUDIO, VIDEO)
     */
    public static void writeHeader(MemorySegment segment, long offset,
                                    ConversationRole role, int sequenceId,
                                    long timestampMs, long sessionId,
                                    int bodyLength, short modelId,
                                    int tokenIn, int tokenOut,
                                    int latencyMs, long userId,
                                    short soulVersion, SourceModality modality) {
        // Byte 0: header version
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_HEADER_VERSION,
                (byte) SynapticHeaderConstants.HEADER_VERSION);

        // Byte 1: flags (type=EPISODIC, modality, no tombstone/consolidated/pinned)
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = SynapticHeaderConstants.withSourceModality(flags, modality.ordinal());
        }
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_FLAGS, flags);

        // Byte 2: role (reinterprets valence)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_VALENCE,
                (byte) role.ordinal());

        // Byte 3: reserved (reinterprets arousal)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_AROUSAL, (byte) 0);

        // Bytes 4-7: sequence_id (reinterprets importance as int32)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_IMPORTANCE, sequenceId);

        // Bytes 8-15: timestamp_ms (same interpretation)
        segment.set(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_TIMESTAMP, timestampMs);

        // Bytes 16-19: token_in_count (reinterprets agent_recall_count)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_AGENT_RECALL_COUNT, tokenIn);

        // Bytes 20-23: token_out_count (reinterprets exact_norm as int32)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_EXACT_NORM, tokenOut);

        // Bytes 24-31: session_id (reinterprets synaptic_tags as raw hash)
        segment.set(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_SYNAPTIC_TAGS, sessionId);

        // Bytes 32-33: model_id (reinterprets centroid_id)
        segment.set(ValueLayout.JAVA_SHORT, offset + SynapticHeaderConstants.OFFSET_CENTROID_ID, modelId);

        // Byte 34: conversation_flags (reinterprets consolidation_flags)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_CONSOLIDATION_FLAGS, (byte) 0);

        // Byte 35: reserved (reinterprets encoding_profile)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_ENCODING_PROFILE, (byte) 0);

        // Bytes 36-39: latency_ms (reinterprets storage_strength as int32)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_STORAGE_STRENGTH, latencyMs);

        // Bytes 40-43: reserved (reinterprets spector_recall_cnt)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_SPECTOR_RECALL_COUNT, 0);

        // Bytes 44-45: reserved (reinterprets encoding_alpha/beta)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_ENCODING_ALPHA, (byte) 0);
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_ENCODING_BETA, (byte) 0);

        // Bytes 46-47: soul_version (same interpretation)
        segment.set(ValueLayout.JAVA_SHORT, offset + SynapticHeaderConstants.OFFSET_SOUL_VERSION, soulVersion);

        // Bytes 48-55: user_id (reinterprets last_auto_ltp as raw hash)
        segment.set(ValueLayout.JAVA_LONG, offset + SynapticHeaderConstants.OFFSET_LAST_AUTO_LTP, userId);

        // Bytes 56-59: body_length (reinterprets encoding_surprise as int32)
        segment.set(ValueLayout.JAVA_INT, offset + SynapticHeaderConstants.OFFSET_ENCODING_SURPRISE, bodyLength);

        // Byte 60: reserved (reinterprets last_recall_profile)
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE, (byte) 0);

        // Bytes 61-63: reserved padding (zero-fill)
        segment.set(ValueLayout.JAVA_BYTE, offset + 61, (byte) 0);
        segment.set(ValueLayout.JAVA_BYTE, offset + 62, (byte) 0);
        segment.set(ValueLayout.JAVA_BYTE, offset + 63, (byte) 0);
    }

    /**
     * Tombstones an episodic record at the given offset.
     */
    public static void tombstone(MemorySegment segment, long offset) {
        byte flags = readFlags(segment, offset);
        flags = (byte) (flags | SynapticHeaderConstants.FLAG_TOMBSTONE);
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_FLAGS, flags);
    }

    /**
     * Marks an episodic record as consolidated (reflected into SEMANTIC tier).
     */
    public static void markConsolidated(MemorySegment segment, long offset) {
        byte flags = readFlags(segment, offset);
        flags = (byte) (flags | SynapticHeaderConstants.FLAG_CONSOLIDATED);
        segment.set(ValueLayout.JAVA_BYTE, offset + SynapticHeaderConstants.OFFSET_FLAGS, flags);
    }

    // ── Episodic record ──

    /**
     * In-memory representation of an episodic conversation turn.
     *
     * <p>Carries the decoded header fields alongside the raw CBOR body bytes.
     * Used as the return type for episodic record reads.</p>
     *
     * @param role        conversation role
     * @param sequenceId  monotonic turn counter per session
     * @param timestampMs epoch milliseconds
     * @param sessionId   8B TSID hash
     * @param bodyLength  CBOR payload byte count
     * @param body        raw CBOR body bytes (null if read without body)
     * @param modelId     LLM model registry ID
     * @param tokenIn     input token count
     * @param tokenOut    output token count
     * @param latencyMs   response generation latency in ms
     * @param userId      user/tenant 8B TSID hash
     * @param soulVersion agent soul configuration version
     * @param modality    source modality
     * @param flags       raw flags byte
     */
    public record EpisodicRecord(
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
            byte flags
    ) {}

    /**
     * Reads a complete episodic record from the given segment at the specified offset.
     *
     * @param segment    the mmap segment
     * @param offset     byte offset where the 64B header begins
     * @param includeBody if true, also reads the CBOR body bytes after the header
     * @return the decoded episodic record
     */
    public static EpisodicRecord readRecord(MemorySegment segment, long offset, boolean includeBody) {
        byte flags = readFlags(segment, offset);
        int bodyLength = readBodyLength(segment, offset);

        byte[] body = null;
        if (includeBody && bodyLength > 0) {
            long bodyOffset = offset + SynapticHeaderConstants.HEADER_BYTES;
            body = segment.asSlice(bodyOffset, bodyLength).toArray(ValueLayout.JAVA_BYTE);
        }

        return new EpisodicRecord(
                readRole(segment, offset),
                readSequenceId(segment, offset),
                readTimestamp(segment, offset),
                readSessionId(segment, offset),
                bodyLength,
                body,
                readModelId(segment, offset),
                readTokenInCount(segment, offset),
                readTokenOutCount(segment, offset),
                readLatencyMs(segment, offset),
                readUserId(segment, offset),
                readSoulVersion(segment, offset),
                SourceModality.fromOrdinal(SynapticHeaderConstants.sourceModalityOrdinal(flags)),
                flags
        );
    }
}
