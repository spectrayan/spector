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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Factory for building mutually authenticated TLS 1.3 contexts, server sockets, and client sockets
 * for the dedicated :9090 replication listener (ADR-0034 §10.2, Req R6.2).
 */
public class ReplicationTlsFactory {

    private static final Logger log = LoggerFactory.getLogger(ReplicationTlsFactory.class);

    public static final String TLS_V1_3 = "TLSv1.3";
    public static final String[] ENABLED_PROTOCOLS = new String[]{TLS_V1_3};

    /**
     * Builds an SSLContext configured with mutually authenticated TLS 1.3 from PKCS12 keystore and truststore files.
     *
     * @param keyStorePath path to PKCS12 keystore containing local node certificate and private key
     * @param keyStorePassword password for the keystore
     * @param trustStorePath path to PKCS12 truststore containing peer public certificates
     * @param trustStorePassword password for the truststore
     * @return initialized SSLContext with TLS 1.3
     * @throws Exception on keystore loading or SSL initialization failure
     */
    public static SSLContext createMtlsContext(
            Path keyStorePath,
            char[] keyStorePassword,
            Path trustStorePath,
            char[] trustStorePassword
    ) throws Exception {
        Objects.requireNonNull(keyStorePath, "keyStorePath must not be null");
        Objects.requireNonNull(trustStorePath, "trustStorePath must not be null");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(keyStorePath)) {
            keyStore.load(is, keyStorePassword);
        }

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(trustStorePath)) {
            trustStore.load(is, trustStorePassword);
        }

        return createMtlsContext(keyStore, keyStorePassword, trustStore);
    }

    /**
     * Builds an SSLContext configured with mutually authenticated TLS 1.3 from in-memory KeyStores.
     *
     * @param keyStore keystore containing local identity
     * @param keyPassword password for private key
     * @param trustStore truststore containing trusted peer certificates
     * @return initialized SSLContext with TLS 1.3
     * @throws Exception on SSL initialization failure
     */
    public static SSLContext createMtlsContext(
            KeyStore keyStore,
            char[] keyPassword,
            KeyStore trustStore
    ) throws Exception {
        Objects.requireNonNull(keyStore, "keyStore must not be null");
        Objects.requireNonNull(trustStore, "trustStore must not be null");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance(TLS_V1_3);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

    /**
     * Configures an {@link SSLServerSocket} with strict TLS 1.3 and mandatory mutual client authentication (Req R6.2).
     *
     * @param serverSocket socket to configure
     */
    public static void configureServerSocket(SSLServerSocket serverSocket) {
        Objects.requireNonNull(serverSocket, "serverSocket must not be null");

        // Enforce TLS 1.3 protocol
        serverSocket.setEnabledProtocols(ENABLED_PROTOCOLS);

        // Enforce mandatory mutual client certificate authentication (mTLS)
        serverSocket.setNeedClientAuth(true);

        log.debug("Configured SSLServerSocket on port {} with TLS 1.3 and needClientAuth=true", serverSocket.getLocalPort());
    }

    /**
     * Configures an {@link SSLSocket} with strict TLS 1.3.
     *
     * @param socket socket to configure
     */
    public static void configureClientSocket(SSLSocket socket) {
        Objects.requireNonNull(socket, "socket must not be null");
        socket.setEnabledProtocols(ENABLED_PROTOCOLS);
    }
}
