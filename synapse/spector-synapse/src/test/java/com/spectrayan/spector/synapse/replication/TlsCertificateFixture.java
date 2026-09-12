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

import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Test fixture that generates self-signed PKCS12 keystores and truststores using keytool
 * for validating mutually authenticated TLS 1.3 replication transports (Req R6.2).
 */
public class TlsCertificateFixture {

    public static final char[] PASSWORD = "changeit".toCharArray();

    public record KeyPairStores(
            Path serverKeyStore,
            Path serverTrustStore,
            Path clientKeyStore,
            Path clientTrustStore
    ) {}

    /**
     * Generates temporary server and client PKCS12 keystores and mutual truststores in the given directory.
     *
     * @param workDir temporary directory to hold keystore files
     * @return KeyPairStores containing paths to all stores
     * @throws Exception if generation fails
     */
    public static KeyPairStores generateMtlsStores(Path workDir) throws Exception {
        Files.createDirectories(workDir);

        Path serverKs = workDir.resolve("server.p12");
        Path serverCert = workDir.resolve("server.crt");
        Path serverTs = workDir.resolve("server-trust.p12");

        Path clientKs = workDir.resolve("client.p12");
        Path clientCert = workDir.resolve("client.crt");
        Path clientTs = workDir.resolve("client-trust.p12");

        String javaHome = System.getenv("JAVA_HOME");
        String keytoolBin = (javaHome != null && !javaHome.isBlank())
                ? javaHome + "/bin/keytool"
                : "keytool";

        // 1. Generate server keypair with SAN for 127.0.0.1 and localhost
        runCmd(keytoolBin, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", serverKs.toString(), "-storepass", "changeit",
                "-validity", "30", "-dname", "CN=localhost, OU=Spector, O=Spectrayan, C=US",
                "-ext", "SAN=ip:127.0.0.1,dns:localhost");

        // 2. Export server cert
        runCmd(keytoolBin, "-exportcert", "-alias", "server", "-keystore", serverKs.toString(),
                "-storepass", "changeit", "-file", serverCert.toString());

        // 3. Import server cert into client truststore
        runCmd(keytoolBin, "-importcert", "-noprompt", "-alias", "server", "-keystore", clientTs.toString(),
                "-storepass", "changeit", "-file", serverCert.toString());

        // 4. Generate client keypair
        runCmd(keytoolBin, "-genkeypair", "-alias", "client", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", clientKs.toString(), "-storepass", "changeit",
                "-validity", "30", "-dname", "CN=client, OU=Spector, O=Spectrayan, C=US");

        // 5. Export client cert
        runCmd(keytoolBin, "-exportcert", "-alias", "client", "-keystore", clientKs.toString(),
                "-storepass", "changeit", "-file", clientCert.toString());

        // 6. Import client cert into server truststore
        runCmd(keytoolBin, "-importcert", "-noprompt", "-alias", "client", "-keystore", serverTs.toString(),
                "-storepass", "changeit", "-file", clientCert.toString());

        return new KeyPairStores(serverKs, serverTs, clientKs, clientTs);
    }

    private static void runCmd(String... cmd) throws Exception {
        Process proc = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("keytool command timed out: " + String.join(" ", cmd));
        }
        if (proc.exitValue() != 0) {
            String output = new String(proc.getInputStream().readAllBytes());
            throw new IllegalStateException("keytool failed (" + proc.exitValue() + "): " + output);
        }
    }
}
