/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.replication;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Protocol frame representation for dedicated replication transport on :9090 (Req R6.1, R6.2).
 *
 * <p>Wire layout:
 * <pre>
 *   +-------------------+--------------+------------+-------------------+--------------------+
 *   | Magic (0x53505245)| Version (1B) | Type (1B)  | Length (4B int)   | Payload (N bytes)  |
 *   +-------------------+--------------+------------+-------------------+--------------------+
 * </pre>
 * </p>
 */
public record ReplicationFrame(
        byte type,
        byte[] payload
) {

    public static final int MAGIC = 0x53505245; // 'SPRE'
    public static final byte VERSION_1 = 1;

    public static final byte TYPE_SNAPSHOT_PAYLOAD = 1;
    public static final byte TYPE_WAL_TAIL = 2;
    public static final byte TYPE_ACK = 3;
    public static final byte TYPE_ERROR = 4;
    public static final byte TYPE_RESYNC_REQUEST = 5;
    public static final byte TYPE_PING = 6;
    public static final byte TYPE_PONG = 7;

    public static final int MAX_FRAME_SIZE = 128 * 1024 * 1024; // 128MB max frame

    public ReplicationFrame {
        Objects.requireNonNull(payload, "payload must not be null");
    }

    /**
     * Creates an ACK frame.
     *
     * @param tenantId tenant identifier
     * @param namespaceId namespace identifier
     * @param appliedHwm high-water mark applied
     * @param status status string (e.g. "SUCCESS", "ALREADY_APPLIED")
     * @param message detail message
     * @return ReplicationFrame of type ACK
     */
    public static ReplicationFrame ack(String tenantId, String namespaceId, long appliedHwm, String status, String message) {
        String json = String.format("{\"tenantId\":\"%s\",\"namespaceId\":\"%s\",\"appliedHwm\":%d,\"status\":\"%s\",\"message\":\"%s\"}",
                tenantId != null ? tenantId : "",
                namespaceId != null ? namespaceId : "",
                appliedHwm,
                status != null ? status : "SUCCESS",
                message != null ? message : "");
        return new ReplicationFrame(TYPE_ACK, json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a sanitized ERROR frame to return to peer without leaking sensitive information (Req R6.5).
     *
     * @param sanitizedMessage error message
     * @return ReplicationFrame of type ERROR
     */
    public static ReplicationFrame error(String sanitizedMessage) {
        String json = String.format("{\"error\":\"%s\"}", sanitizedMessage != null ? sanitizedMessage : "ERROR");
        return new ReplicationFrame(TYPE_ERROR, json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes this frame to the output stream.
     *
     * @param out target output stream
     * @throws IOException on I/O error
     */
    public void writeTo(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(MAGIC);
        dos.writeByte(VERSION_1);
        dos.writeByte(type);
        dos.writeInt(payload.length);
        dos.write(payload);
        dos.flush();
    }

    /**
     * Decodes a frame from the input stream.
     *
     * @param in source input stream
     * @return parsed ReplicationFrame
     * @throws IOException on wire or corruption error
     */
    public static ReplicationFrame readFrom(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IOException(String.format("Invalid replication frame magic: 0x%08X (expected 0x%08X)", magic, MAGIC));
        }

        byte version = dis.readByte();
        if (version != VERSION_1) {
            throw new IOException("Unsupported replication protocol version: " + version);
        }

        byte type = dis.readByte();
        int length = dis.readInt();
        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Frame length out of bounds: " + length);
        }

        byte[] payload = dis.readNBytes(length);
        if (payload.length != length) {
            throw new IOException(String.format("Premature EOF reading payload: expected %d bytes, got %d", length, payload.length));
        }

        return new ReplicationFrame(type, payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReplicationFrame that)) return false;
        return type == that.type && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "ReplicationFrame[type=" + type + ", len=" + payload.length + "]";
    }
}
