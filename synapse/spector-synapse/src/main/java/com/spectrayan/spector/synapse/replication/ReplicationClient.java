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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Client for shipping snapshot payloads and frames to replica nodes over mTLS :9090 (Req R6.1, R6.2).
 */
public class ReplicationClient {

    private static final Logger log = LoggerFactory.getLogger(ReplicationClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SSLContext sslContext;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public record ReplicationResponse(
            boolean success,
            long appliedHwm,
            String status,
            String message,
            String rawJson
    ) {}

    public ReplicationClient(SSLContext sslContext) {
        this(sslContext, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }

    public ReplicationClient(SSLContext sslContext, Duration connectTimeout, Duration readTimeout) {
        this.sslContext = sslContext;
        this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(30);
    }

    /**
     * Sends a snapshot payload consisting of a manifest and binary bundle files to a replica.
     *
     * @param host replica host
     * @param port replica replication port (:9090)
     * @param manifest snapshot manifest
     * @param fileDataMap map of bundle filename to byte contents
     * @return ReplicationResponse from replica
     * @throws Exception on connection or transmission failure
     */
    public ReplicationResponse sendSnapshot(
            String host,
            int port,
            SnapshotManifest manifest,
            Map<String, byte[]> fileDataMap
    ) throws Exception {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(fileDataMap, "fileDataMap must not be null");

        // Construct serialized payload: manifest length + manifest json + file count + files
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            byte[] manifestJsonBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(manifestJsonBytes.length);
            dos.write(manifestJsonBytes);

            dos.writeInt(fileDataMap.size());
            for (Map.Entry<String, byte[]> entry : fileDataMap.entrySet()) {
                dos.writeUTF(entry.getKey());
                dos.writeInt(entry.getValue().length);
                dos.write(entry.getValue());
            }
            dos.flush();
        }

        ReplicationFrame frame = new ReplicationFrame(ReplicationFrame.TYPE_SNAPSHOT_PAYLOAD, baos.toByteArray());

        try (Socket socket = openSocket(host, port);
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

            frame.writeTo(out);

            ReplicationFrame reply = ReplicationFrame.readFrom(in);
            String replyJson = new String(reply.payload(), StandardCharsets.UTF_8);

            if (reply.type() == ReplicationFrame.TYPE_ACK) {
                JsonNode node = MAPPER.readTree(replyJson);
                long hwm = node.has("appliedHwm") ? node.get("appliedHwm").asLong() : 0L;
                String status = node.has("status") ? node.get("status").asText() : "SUCCESS";
                String msg = node.has("message") ? node.get("message").asText() : "";
                return new ReplicationResponse(true, hwm, status, msg, replyJson);
            } else if (reply.type() == ReplicationFrame.TYPE_ERROR) {
                JsonNode node = MAPPER.readTree(replyJson);
                String error = node.has("error") ? node.get("error").asText() : replyJson;
                return new ReplicationResponse(false, 0L, "ERROR", error, replyJson);
            } else {
                return new ReplicationResponse(false, 0L, "UNKNOWN", "Unexpected frame type: " + reply.type(), replyJson);
            }
        }
    }

    private Socket openSocket(String host, int port) throws IOException {
        Socket socket;
        if (sslContext != null) {
            SSLSocketFactory ssf = sslContext.getSocketFactory();
            SSLSocket sslSocket = (SSLSocket) ssf.createSocket();
            ReplicationTlsFactory.configureClientSocket(sslSocket);
            socket = sslSocket;
        } else {
            socket = new Socket();
        }

        socket.setSoTimeout((int) readTimeout.toMillis());
        socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
        return socket;
    }
}
