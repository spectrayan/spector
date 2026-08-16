/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.spring.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.DataEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptingJsonCacheSerializerTest {

    record SamplePayload(String id, int count, List<String> tags) {}

    @Test
    @DisplayName("plain mode serializes and deserializes JSON roundtrip")
    void plainMode_roundtrip() {
        var mapper = new ObjectMapper();
        var serializer = new EncryptingJsonCacheSerializer(mapper, DataEncryptor.NOOP);

        assertThat(serializer.isEncryptionEnabled()).isFalse();

        var original = new SamplePayload("sample-1", 42, List.of("tagA", "tagB"));
        byte[] bytes = serializer.serialize(original);
        assertThat(bytes).isNotEmpty();

        SamplePayload result = serializer.deserialize(bytes, SamplePayload.class);
        assertThat(result).isEqualTo(original);
    }

    @Test
    @DisplayName("encrypted mode transforms bytes with DataEncryptor and decrypts back")
    void encryptedMode_roundtrip() throws Exception {
        var mapper = new ObjectMapper();

        // Simple XOR mock encryptor for unit testing
        var mockEncryptor = new DataEncryptor() {
            @Override
            public byte[] encryptText(byte[] plaintext) { return encryptPayload(plaintext); }

            @Override
            public byte[] decryptText(byte[] ciphertext) { return decryptPayload(ciphertext); }

            @Override
            public byte[] encryptPayload(byte[] plaintext) {
                byte[] out = new byte[plaintext.length];
                for (int i = 0; i < plaintext.length; i++) {
                    out[i] = (byte) (plaintext[i] ^ 0x5A);
                }
                return out;
            }

            @Override
            public byte[] decryptPayload(byte[] ciphertext) {
                return encryptPayload(ciphertext); // XOR is symmetric
            }

            @Override
            public long encodeTag(String tag) { return 0; }

            @Override
            public boolean isEnabled() { return true; }
        };

        var serializer = new EncryptingJsonCacheSerializer(mapper, mockEncryptor);
        assertThat(serializer.isEncryptionEnabled()).isTrue();

        var original = new SamplePayload("secret-1", 99, List.of("classified"));
        byte[] encryptedBytes = serializer.serialize(original);

        // Encrypted bytes should NOT equal plain JSON bytes
        byte[] plainJsonBytes = mapper.writeValueAsBytes(original);
        assertThat(Arrays.equals(encryptedBytes, plainJsonBytes)).isFalse();

        SamplePayload decrypted = serializer.deserialize(encryptedBytes, SamplePayload.class);
        assertThat(decrypted).isEqualTo(original);
    }
}
