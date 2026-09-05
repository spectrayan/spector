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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("EpisodeCodec Tests (Option B Payload & Framing)")
class EpisodeCodecTest {

    @Test
    @DisplayName("encode and decode payload roundtrip preserves all metadata and body")
    void testEncodeDecodeRoundtrip() {
        ConversationRole role = ConversationRole.USER;
        long sessionId = 0x123456789ABCDEF0L;
        short modelId = 42;
        int tokenIn = 120;
        int tokenOut = 250;
        int latencyMs = 85;
        long userId = 0xFEDCBA9876543210L;
        byte[] body = "Test episode conversation body content".getBytes(StandardCharsets.UTF_8);

        byte[] payload = EpisodeCodec.encode(role, sessionId, modelId, tokenIn, tokenOut, latencyMs, userId, body);
        assertThat(payload.length).isEqualTo(EpisodeCodec.PAYLOAD_METADATA_BYTES + body.length);

        MemorySegment segment = MemorySegment.ofArray(payload);
        EpisodeCodec.DecodedPayload decoded = EpisodeCodec.decode(segment, 0L, payload.length, true);

        assertAll("Decoded payload fields",
                () -> assertThat(decoded.role()).isEqualTo(role),
                () -> assertThat(decoded.sessionId()).isEqualTo(sessionId),
                () -> assertThat(decoded.modelId()).isEqualTo(modelId),
                () -> assertThat(decoded.tokenIn()).isEqualTo(tokenIn),
                () -> assertThat(decoded.tokenOut()).isEqualTo(tokenOut),
                () -> assertThat(decoded.latencyMs()).isEqualTo(latencyMs),
                () -> assertThat(decoded.userId()).isEqualTo(userId),
                () -> assertThat(decoded.bodyLength()).isEqualTo(body.length),
                () -> assertThat(decoded.body()).isEqualTo(body)
        );
    }

    @Test
    @DisplayName("decode with includeBody=false omits byte copy")
    void testDecodeExcludingBody() {
        byte[] body = "Hello world".getBytes(StandardCharsets.UTF_8);
        byte[] payload = EpisodeCodec.encode(ConversationRole.ASSISTANT, 1L, (short) 2, 10, 20, 30, 4L, body);

        MemorySegment segment = MemorySegment.ofArray(payload);
        EpisodeCodec.DecodedPayload decoded = EpisodeCodec.decode(segment, 0L, payload.length, false);

        assertThat(decoded.role()).isEqualTo(ConversationRole.ASSISTANT);
        assertThat(decoded.bodyLength()).isEqualTo(body.length);
        assertThat(decoded.body()).isNull();
    }

    @Test
    @DisplayName("Checksum calculation and verification")
    void testChecksum() {
        byte[] headerBytes = new byte[64];
        headerBytes[0] = 2; // version
        MemorySegment headerSeg = MemorySegment.ofArray(headerBytes);
        byte[] payload = new byte[]{1, 2, 3, 4, 5};

        int checksum = EpisodeCodec.computeChecksum(100, headerSeg, payload);
        assertThat(EpisodeCodec.verifyChecksum(100, headerSeg, payload, checksum)).isTrue();
        assertThat(EpisodeCodec.verifyChecksum(100, headerSeg, payload, checksum + 1)).isFalse();
        assertThat(EpisodeCodec.verifyChecksum(101, headerSeg, payload, checksum)).isFalse();
    }
}
