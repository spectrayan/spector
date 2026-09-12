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

import com.spectrayan.spector.memory.replication.ReplicaApplyEngine;
import com.spectrayan.spector.memory.replication.SnapshotManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated replication network listener on port :9090 with mTLS 1.3 mutual authentication,
 * tenant allow-list isolation, and atomic apply engine integration (ADR-0034 §10, Req R6.1–R6.5).
 */
public class ReplicationServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReplicationServer.class);

    private final String bindHost;
    private final int port;
    private final SSLContext sslContext;
    private final TenantAllowListFilter allowListFilter;
    private final ReplicaApplyEngine applyEngine;
    private final ReplicationMetrics metrics;
    private final Path tempStagingDir;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService connectionPool;
    private int boundPort;

    public ReplicationServer(
            String bindHost,
            int port,
            SSLContext sslContext,
            TenantAllowListFilter allowListFilter,
            ReplicaApplyEngine applyEngine,
            ReplicationMetrics metrics,
            Path tempStagingDir
    ) {
        this.bindHost = bindHost != null ? bindHost : "127.0.0.1";
        this.port = port > 0 ? port : 9090;
        this.sslContext = sslContext;
        this.allowListFilter = Objects.requireNonNull(allowListFilter, "allowListFilter must not be null");
        this.applyEngine = applyEngine;
        this.metrics = metrics != null ? metrics : new ReplicationMetrics();
        this.tempStagingDir = tempStagingDir != null ? tempStagingDir : Path.of(System.getProperty("java.io.tmpdir"), "spector-replica-staging");
    }

    /**
     * Starts the dedicated replication listener.
     *
     * @throws IOException if binding fails
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        Files.createDirectories(tempStagingDir);
        InetAddress bindAddr = InetAddress.getByName(bindHost);

        if (sslContext != null) {
            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
            SSLServerSocket sslServerSocket = (SSLServerSocket) ssf.createServerSocket(port, 128, bindAddr);
            ReplicationTlsFactory.configureServerSocket(sslServerSocket);
            serverSocket = sslServerSocket;
        } else {
            serverSocket = new ServerSocket(port, 128, bindAddr);
        }

        boundPort = serverSocket.getLocalPort();
        running.set(true);
        connectionPool = Executors.newCachedThreadPool();

        acceptThread = new Thread(this::acceptLoop, "replication-listener-" + boundPort);
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("ReplicationServer started on {}:{} (mTLS={}, allowListSize={})",
                bindHost, boundPort, sslContext != null, allowListFilter.getAllowedTenants().size());
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                connectionPool.submit(() -> handleConnection(socket));
            } catch (SocketException se) {
                if (!running.get()) break;
                log.warn("Replication accept socket closed: {}", se.getMessage());
            } catch (Exception e) {
                if (!running.get()) break;
                log.error("Error accepting replication connection", e);
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            while (running.get() && !socket.isClosed()) {
                ReplicationFrame frame;
                try {
                    frame = ReplicationFrame.readFrom(in);
                } catch (EOFException eof) {
                    break; // peer closed connection gracefully
                }

                ReplicationFrame response = processFrame(frame);
                response.writeTo(out);
            }
        } catch (Exception e) {
            log.debug("Connection ended: {}", e.getMessage());
        }
    }

    private ReplicationFrame processFrame(ReplicationFrame frame) {
        if (frame.type() == ReplicationFrame.TYPE_PING) {
            return new ReplicationFrame(ReplicationFrame.TYPE_PONG, new byte[0]);
        }

        if (frame.type() == ReplicationFrame.TYPE_SNAPSHOT_PAYLOAD) {
            return handleSnapshotPayload(frame.payload());
        }

        return ReplicationFrame.error("Unsupported frame type: " + frame.type());
    }

    private ReplicationFrame handleSnapshotPayload(byte[] payload) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            // Read manifest JSON
            int manifestLen = dis.readInt();
            byte[] manifestBytes = dis.readNBytes(manifestLen);
            SnapshotManifest manifest = SnapshotManifest.fromJson(new String(manifestBytes, StandardCharsets.UTF_8));

            // Multi-tenant isolation boundary (Req R6.3, R6.5)
            if (!allowListFilter.isAllowed(manifest.tenantId())) {
                metrics.recordFrameRejected();
                // Return sanitized error without leaking tenant ID (Req R6.5)
                return ReplicationFrame.error(ReplicationAuthorizationException.SANITIZED_PEER_MESSAGE);
            }

            // Read file entries
            int fileCount = dis.readInt();
            Map<String, Path> stagedFiles = new HashMap<>();
            Path batchTmpDir = Files.createTempDirectory(tempStagingDir, "batch_recv_");

            try {
                for (int i = 0; i < fileCount; i++) {
                    String fileName = dis.readUTF();
                    int fileLen = dis.readInt();
                    byte[] fileBytes = dis.readNBytes(fileLen);

                    Path targetFile = batchTmpDir.resolve(fileName);
                    Files.createDirectories(targetFile.getParent());
                    Files.write(targetFile, fileBytes);
                    stagedFiles.put(fileName, targetFile);
                }

                if (applyEngine != null) {
                    ReplicaApplyEngine.ApplyResult result = applyEngine.applySnapshot(manifest, stagedFiles, List.of());
                    return ReplicationFrame.ack(
                            manifest.tenantId(),
                            manifest.namespaceId(),
                            result.appliedHwm(),
                            result.updated() ? "SUCCESS" : "ALREADY_APPLIED",
                            "Applied snapshot successfully"
                    );
                } else {
                    return ReplicationFrame.ack(
                            manifest.tenantId(),
                            manifest.namespaceId(),
                            manifest.hwm(),
                            "SUCCESS",
                            "Accepted without apply engine"
                    );
                }
            } finally {
                // Cleanup temporary receive files
                try {
                    if (Files.exists(batchTmpDir)) {
                        try (var stream = Files.walk(batchTmpDir)) {
                            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("Failed processing snapshot payload: {}", e.getMessage(), e);
            metrics.recordVerificationFailure();
            return ReplicationFrame.error("Apply failed: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        if (connectionPool != null) {
            connectionPool.shutdownNow();
        }

        log.info("ReplicationServer stopped on port {}", boundPort);
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getBoundPort() {
        return boundPort;
    }
}
