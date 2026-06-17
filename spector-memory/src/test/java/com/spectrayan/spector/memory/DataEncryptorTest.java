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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataEncryptor} SPI and {@link NoopDataEncryptor}.
 */
class DataEncryptorTest {

    @Test
    void noopEncryptorPassesThroughText() {
        DataEncryptor encryptor = DataEncryptor.NOOP;
        byte[] plaintext = "Hello, Spector!".getBytes();

        byte[] encrypted = encryptor.encryptText(plaintext);
        byte[] decrypted = encryptor.decryptText(encrypted);

        assertThat(encrypted).isSameAs(plaintext);
        assertThat(decrypted).isSameAs(encrypted);
    }

    @Test
    void noopEncryptorPassesThroughPayload() {
        DataEncryptor encryptor = DataEncryptor.NOOP;
        byte[] payload = new byte[]{0x01, 0x02, 0x03, 0x04};

        byte[] encrypted = encryptor.encryptPayload(payload);
        byte[] decrypted = encryptor.decryptPayload(encrypted);

        assertThat(encrypted).isSameAs(payload);
        assertThat(decrypted).isSameAs(encrypted);
    }

    @Test
    void noopEncryptorDelegatesToStandardTagEncoder() {
        DataEncryptor encryptor = DataEncryptor.NOOP;

        long noopResult = encryptor.encodeTag("java");
        long standardResult = SynapticTagEncoder.encodeTag("java");

        assertThat(noopResult).isEqualTo(standardResult);
    }

    @Test
    void noopEncryptorReportsDisabled() {
        assertThat(DataEncryptor.NOOP.isEnabled()).isFalse();
    }

    @Test
    void noopIsSingletonInstance() {
        DataEncryptor a = DataEncryptor.NOOP;
        DataEncryptor b = DataEncryptor.NOOP;
        assertThat(a).isSameAs(b);
    }

    @Test
    void noopEncryptorIsInstanceOfNoopClass() {
        assertThat(DataEncryptor.NOOP).isInstanceOf(NoopDataEncryptor.class);
    }

    @Test
    void defaultIsEnabledReturnsTrue() {
        // A custom encryptor (not NOOP) should default to isEnabled=true
        DataEncryptor custom = new DataEncryptor() {
            @Override public byte[] encryptText(byte[] p) { return p; }
            @Override public byte[] decryptText(byte[] c) { return c; }
            @Override public byte[] encryptPayload(byte[] p) { return p; }
            @Override public byte[] decryptPayload(byte[] c) { return c; }
            @Override public long encodeTag(String t) { return 0L; }
        };

        assertThat(custom.isEnabled()).isTrue();
    }
}
