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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;

/**
 * Binary framing and payload codec for episodic conversation records (ADR-0010 / ADR-0030, D2 Option B).
 *
 * <h3>Payload Layout ($N$ bytes at recordOffset + 80)</h3>
 * <pre>
 *   Offset  Size  Field
 *   ──────  ────  ─────────────────────
 *    0      1B    role (ConversationRole ordinal)
 *    1      8B    session_id (long TSID hash)
 *    9      2B    model_id (short registry id)
 *   11      4B    token_in_count (int32)
 *   15      4B    token_out_count (int32)
 *   19      4B    latency_ms (int32)
 *   23      8B    user_id (long TSID hash)
 *   31      4B    body_length (int32, $M$)
 *   35      MB    body (raw payload / CBOR bytes)
 * </pre>
 *
 * <p>Total metadata prefix inside payload is 35 bytes. Total payload size is {@code 35 + M}.</p>
 *
 * @since 1.4.0
 */
public final class EpisodeCodec {

    /** Base metadata size of the conversation payload before the raw body bytes. */
    public static final int PAYLOAD_METADATA_BYTES = 35;

    public static final int OFFSET_ROLE = 0;
    public static final int OFFSET_SESSION_ID = 1;
    public static final int OFFSET_MODEL_ID = 9;
    public static final int OFFSET_TOKEN_IN = 11;
    public static final int OFFSET_TOKEN_OUT = 15;
    public static final int OFFSET_LATENCY_MS = 19;
    public static final int OFFSET_USER_ID = 23;
    public static final int OFFSET_BODY_LENGTH = 31;
    public static final int OFFSET_BODY = 35;

    private EpisodeCodec() {} // static utility

    /**
     * In-memory representation of decoded conversation metadata and payload body.
     */
    public record DecodedPayload(
            ConversationRole role,
            long sessionId,
            short modelId,
            int tokenIn,
            int tokenOut,
            int latencyMs,
            long userId,
            int bodyLength,
            byte[] body
    ) {}

    /**
     * Encodes conversation metadata and body bytes into a contiguous binary payload.
     *
     * @param role       conversation role
     * @param sessionId  8B session hash
     * @param modelId    model registry ID
     * @param tokenIn    input token count
     * @param tokenOut   output token count
     * @param latencyMs  generation latency in ms
     * @param userId     user/tenant ID
     * @param body       raw payload / CBOR body bytes
     * @return encoded payload byte array
     */
    public static byte[] encode(ConversationRole role,
                                long sessionId,
                                short modelId,
                                int tokenIn,
                                int tokenOut,
                                int latencyMs,
                                long userId,
                                byte[] body) {
        int bodyLen = (body != null) ? body.length : 0;
        byte[] payload = new byte[PAYLOAD_METADATA_BYTES + bodyLen];
        MemorySegment seg = MemorySegment.ofArray(payload);

        seg.set(ValueLayout.JAVA_BYTE, OFFSET_ROLE, (byte) (role != null ? role.ordinal() : 0));
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_SESSION_ID, sessionId);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, OFFSET_MODEL_ID, modelId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFFSET_TOKEN_IN, tokenIn);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFFSET_TOKEN_OUT, tokenOut);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFFSET_LATENCY_MS, latencyMs);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_USER_ID, userId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFFSET_BODY_LENGTH, bodyLen);

        if (bodyLen > 0) {
            MemorySegment.copy(MemorySegment.ofArray(body), 0, seg, OFFSET_BODY, bodyLen);
        }
        return payload;
    }

    /**
     * Decodes the conversation payload starting at {@code payloadOffset} in the given segment.
     *
     * @param segment       mmap segment containing the payload
     * @param payloadOffset absolute offset where the payload begins
     * @param payloadBytes  total length of the payload
     * @param includeBody   if true, copies the body bytes into an array
     * @return decoded payload structure
     */
    public static DecodedPayload decode(MemorySegment segment, long payloadOffset, int payloadBytes, boolean includeBody) {
        if (payloadBytes < PAYLOAD_METADATA_BYTES) {
            return new DecodedPayload(ConversationRole.USER, 0L, (short) 0, 0, 0, 0, 0L, 0, null);
        }

        byte roleByte = segment.get(ValueLayout.JAVA_BYTE, payloadOffset + OFFSET_ROLE);
        ConversationRole role = ConversationRole.fromOrdinal(roleByte & 0xFF);
        long sessionId = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, payloadOffset + OFFSET_SESSION_ID);
        short modelId = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, payloadOffset + OFFSET_MODEL_ID);
        int tokenIn = segment.get(ValueLayout.JAVA_INT_UNALIGNED, payloadOffset + OFFSET_TOKEN_IN);
        int tokenOut = segment.get(ValueLayout.JAVA_INT_UNALIGNED, payloadOffset + OFFSET_TOKEN_OUT);
        int latencyMs = segment.get(ValueLayout.JAVA_INT_UNALIGNED, payloadOffset + OFFSET_LATENCY_MS);
        long userId = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, payloadOffset + OFFSET_USER_ID);
        int bodyLength = segment.get(ValueLayout.JAVA_INT_UNALIGNED, payloadOffset + OFFSET_BODY_LENGTH);

        byte[] body = null;
        if (includeBody && bodyLength > 0 && payloadBytes >= PAYLOAD_METADATA_BYTES + bodyLength) {
            body = segment.asSlice(payloadOffset + OFFSET_BODY, bodyLength).toArray(ValueLayout.JAVA_BYTE);
        }

        return new DecodedPayload(role, sessionId, modelId, tokenIn, tokenOut, latencyMs, userId, bodyLength, body);
    }

    /**
     * Computes the CRC32C checksum across the sequence ID, the 64-byte encoding header, and the payload.
     *
     * @param sequenceId sequence ID from the prefix
     * @param header     canonical 64B encoding header
     * @param payload    encoded payload bytes
     * @return 32-bit CRC32C checksum
     */
    public static int computeChecksum(int sequenceId, MemorySegment header, byte[] payload) {
        CRC32C crc = new CRC32C();
        // 4 bytes sequenceId
        crc.update((sequenceId >>> 24) & 0xFF);
        crc.update((sequenceId >>> 16) & 0xFF);
        crc.update((sequenceId >>> 8) & 0xFF);
        crc.update(sequenceId & 0xFF);

        // 64B header
        if (header != null && header.byteSize() >= EpisodeLayout.HEADER_BYTES) {
            crc.update(header.asSlice(0, EpisodeLayout.HEADER_BYTES).asByteBuffer());
        }

        // payload bytes
        if (payload != null && payload.length > 0) {
            crc.update(payload, 0, payload.length);
        }

        return (int) crc.getValue();
    }

    /**
     * Verifies the CRC32C checksum across sequenceId, header, and payload against expected checksum.
     */
    public static boolean verifyChecksum(int sequenceId, MemorySegment header, byte[] payload, int expectedChecksum) {
        return computeChecksum(sequenceId, header, payload) == expectedChecksum;
    }

    /**
     * Attempts to extract a readable string representation from the payload body bytes.
     *
     * @param body raw body bytes
     * @return extracted text string, or empty string if absent
     */
    public static String extractText(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
