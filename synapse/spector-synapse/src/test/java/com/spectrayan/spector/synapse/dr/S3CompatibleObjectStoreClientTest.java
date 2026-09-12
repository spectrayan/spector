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
package com.spectrayan.spector.synapse.dr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit and integration tests for S3-compatible object store client abstraction
 * (ADR-0034 §11.2, §14, Req R1.1, R1.2, R1.5, R1.6).
 */
@DisplayName("Phase 6 Group 1: Object-Store Abstraction & S3 Client Specification")
class S3CompatibleObjectStoreClientTest {

    private EmbeddedS3Server server;
    private S3CompatibleObjectStoreClient client;
    private static final String BUCKET = "spector-dr-test-bucket";

    @BeforeEach
    void setUp() throws Exception {
        server = new EmbeddedS3Server();
        server.createBucket(BUCKET);
        client = new S3CompatibleObjectStoreClient(
                server.getEndpoint(),
                "us-east-1",
                "test-access-key",
                "test-secret-key",
                0L // unlimited bandwidth for general tests
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @DisplayName("R1.1, R1.2: Put, Get, Exists, and Delete object round-trip")
    void testBasicObjectLifecycle() {
        String key = "snapshots/ten-1/ns-1/manifest.json";
        byte[] payload = "{\"kind\":\"FULL\",\"hwm\":42}".getBytes(StandardCharsets.UTF_8);

        assertThat(client.objectExists(BUCKET, key)).isFalse();

        // PUT
        client.putObject(BUCKET, key, payload, "application/json", Map.of("tenant", "ten-1"));

        // EXISTS
        assertThat(client.objectExists(BUCKET, key)).isTrue();

        // GET
        Optional<byte[]> fetched = client.getObject(BUCKET, key);
        assertThat(fetched).isPresent();
        assertThat(new String(fetched.get(), StandardCharsets.UTF_8)).isEqualTo("{\"kind\":\"FULL\",\"hwm\":42}");

        // DELETE
        boolean deleted = client.deleteObject(BUCKET, key);
        assertThat(deleted).isTrue();
        assertThat(client.objectExists(BUCKET, key)).isFalse();
        assertThat(client.getObject(BUCKET, key)).isEmpty();
    }

    @Test
    @DisplayName("R1.1, R1.5: List keys by prefix and delete by prefix")
    void testListAndDeleteByPrefix() {
        String prefix = "snapshots/ten-alpha/";
        client.putObject(BUCKET, prefix + "manifest.json", "manifest".getBytes(StandardCharsets.UTF_8), null, null);
        client.putObject(BUCKET, prefix + "runtime.dat", "runtime".getBytes(StandardCharsets.UTF_8), null, null);
        client.putObject(BUCKET, prefix + "part-0.spct", "part0".getBytes(StandardCharsets.UTF_8), null, null);
        client.putObject(BUCKET, "snapshots/ten-beta/manifest.json", "other".getBytes(StandardCharsets.UTF_8), null, null);

        // List
        List<String> keys = client.listKeysByPrefix(BUCKET, prefix);
        assertThat(keys).containsExactlyInAnyOrder(
                prefix + "manifest.json",
                prefix + "runtime.dat",
                prefix + "part-0.spct"
        );

        // Delete by prefix
        int count = client.deleteByPrefix(BUCKET, prefix);
        assertThat(count).isEqualTo(3);

        assertThat(client.listKeysByPrefix(BUCKET, prefix)).isEmpty();
        // Unrelated tenant remains untouched
        assertThat(client.objectExists(BUCKET, "snapshots/ten-beta/manifest.json")).isTrue();
    }

    @Test
    @DisplayName("R1.5, R1.6: Server outage throws ObjectStoreException without crashing runtime")
    void testServerOutageHandling() {
        server.setUnavailable(true);

        assertThatThrownBy(() -> client.putObject(BUCKET, "test-key", "data".getBytes(StandardCharsets.UTF_8), null, null))
                .isInstanceOf(ObjectStoreException.class)
                .hasMessageContaining("HTTP 503");

        assertThatThrownBy(() -> client.getObject(BUCKET, "test-key"))
                .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    @DisplayName("R1.6: Bandwidth rate limiting paces byte transfers")
    void testBandwidthThrottlerPacing() {
        // Limit to 500 bytes per second
        BandwidthThrottler throttler = new BandwidthThrottler(500);
        long start = System.currentTimeMillis();

        throttler.throttle(300);
        long firstElapsed = System.currentTimeMillis() - start;
        assertThat(firstElapsed).isLessThan(200); // within first window

        // Transferring another 300 exceeds 500 byte limit, so should throttle
        throttler.throttle(300);
        long totalElapsed = System.currentTimeMillis() - start;
        assertThat(totalElapsed).isGreaterThanOrEqualTo(800); // throttled to next second window
    }
}
