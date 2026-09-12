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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates mutual TLS 1.3 enforcement and rejection of unauthenticated peers on :9090 (Req R6.1, R6.2).
 */
@DisplayName("Task 5.1 & 5.2: Dedicated Listener & mTLS 1.3 Transport Tests")
class ReplicationTlsTest {

    @TempDir
    Path tempDir;

    private TlsCertificateFixture.KeyPairStores stores;
    private SSLContext serverSslContext;
    private SSLContext clientSslContext;

    private ReplicationServer server;

    @BeforeEach
    void setUp() throws Exception {
        stores = TlsCertificateFixture.generateMtlsStores(tempDir.resolve("certs"));

        serverSslContext = ReplicationTlsFactory.createMtlsContext(
                stores.serverKeyStore(),
                TlsCertificateFixture.PASSWORD,
                stores.serverTrustStore(),
                TlsCertificateFixture.PASSWORD
        );

        clientSslContext = ReplicationTlsFactory.createMtlsContext(
                stores.clientKeyStore(),
                TlsCertificateFixture.PASSWORD,
                stores.clientTrustStore(),
                TlsCertificateFixture.PASSWORD
        );

        server = new ReplicationServer(
                "127.0.0.1",
                0, // dynamic port
                serverSslContext,
                new TenantAllowListFilter(Set.of("tenant-1")),
                null,
                new ReplicationMetrics(),
                tempDir.resolve("staging")
        );
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("R6.2: Mutually authenticated TLS 1.3 client connects and exchanges ping/pong frame")
    void testMtlsTls13CommunicationSuccess() throws Exception {
        SSLSocketFactory ssf = clientSslContext.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) ssf.createSocket("127.0.0.1", server.getBoundPort())) {
            ReplicationTlsFactory.configureClientSocket(socket);
            socket.startHandshake();

            // Verify negotiated protocol is TLS 1.3
            assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");

            try (BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                ReplicationFrame ping = new ReplicationFrame(ReplicationFrame.TYPE_PING, new byte[0]);
                ping.writeTo(out);

                ReplicationFrame pong = ReplicationFrame.readFrom(in);
                assertThat(pong.type()).isEqualTo(ReplicationFrame.TYPE_PONG);
            }
        }
    }

    @Test
    @DisplayName("R6.2: Plaintext client connecting to mTLS server is rejected")
    void testPlaintextClientRejectedByMtlsServer() {
        assertThatThrownBy(() -> {
            try (Socket socket = new Socket("127.0.0.1", server.getBoundPort());
                 BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                ReplicationFrame ping = new ReplicationFrame(ReplicationFrame.TYPE_PING, new byte[0]);
                ping.writeTo(out);

                // Expect read to fail or connection to be closed by SSL handshake failure
                ReplicationFrame.readFrom(in);
            }
        }).isInstanceOf(IOException.class);
    }
}
