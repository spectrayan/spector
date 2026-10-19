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
package com.spectrayan.spector.synapse.security.kms;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Default non-BYOK platform envelope {@link KeyProvider} ({@code platform-hkdf}) that derives
 * per-tenant 256-bit KEKs from the platform master secret using HKDF-SHA256 (ADR-0088 §5.2, Issue #1070).
 */
@Component
public class PlatformHkdfKeyProvider implements KeyProvider {

    public static final String PROVIDER_ID = "platform-hkdf";
    private static final byte[] PLATFORM_KEK_SALT = "spector-platform-kek-v1".getBytes(StandardCharsets.UTF_8);

    private final AesGcmCipher aesGcmCipher;

    @Autowired
    public PlatformHkdfKeyProvider(AesGcmCipher aesGcmCipher) {
        this.aesGcmCipher = Objects.requireNonNull(aesGcmCipher, "aesGcmCipher must not be null");
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(plaintextDek, "plaintextDek must not be null");
        String effectiveKeyId = (kekKeyId != null && !kekKeyId.isBlank()) ? kekKeyId.trim() : "platform-master-v1";

        byte[] kek = derivePlatformKek(tenantId.trim(), effectiveKeyId);
        try {
            byte[] iv = EnvelopeCipher.generateIv();
            EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(kek, iv, plaintextDek, aad);
            byte[] combined = packIvAndCiphertext(env.iv(), env.ciphertextWithTag());
            return new WrappedKeyResult(combined, env.iv(), effectiveKeyId, "v1");
        } finally {
            Arrays.fill(kek, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] wrappedDek, byte[] aad) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        String effectiveKeyId = (kekKeyId != null && !kekKeyId.isBlank()) ? kekKeyId.trim() : "platform-master-v1";

        byte[] kek = derivePlatformKek(tenantId.trim(), effectiveKeyId);
        try {
            byte[][] unpacked = unpackIvAndCiphertext(wrappedDek);
            byte[] plaintext = EnvelopeCipher.decrypt(kek, unpacked[0], unpacked[1], aad);
            return new DestroyableKeyMaterial(plaintext, true);
        } finally {
            Arrays.fill(kek, (byte) 0);
            VarHandle.fullFence();
        }
    }

    @Override
    public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
        return KeyHealthStatus.HEALTHY;
    }

    private byte[] derivePlatformKek(String tenantId, String kekKeyId) {
        byte[] tenantBase = aesGcmCipher.deriveTenantKey(
                "spector-platform-envelope-root".getBytes(StandardCharsets.UTF_8),
                tenantId);
        try {
            byte[] info = ("platform-kek:" + tenantId + ":" + kekKeyId).getBytes(StandardCharsets.UTF_8);
            return EnvelopeCipher.hkdfSha256(tenantBase, PLATFORM_KEK_SALT, info, EnvelopeCipher.DEK_LENGTH_BYTES);
        } finally {
            Arrays.fill(tenantBase, (byte) 0);
            VarHandle.fullFence();
        }
    }

    static byte[] packIvAndCiphertext(byte[] iv, byte[] ciphertextWithTag) {
        ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertextWithTag.length);
        buf.put(iv);
        buf.put(ciphertextWithTag);
        return buf.array();
    }

    static byte[][] unpackIvAndCiphertext(byte[] packed) {
        if (packed.length <= EnvelopeCipher.GCM_IV_LENGTH_BYTES + EnvelopeCipher.GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Wrapped DEK payload is too short (" + packed.length + " bytes)");
        }
        byte[] iv = Arrays.copyOfRange(packed, 0, EnvelopeCipher.GCM_IV_LENGTH_BYTES);
        byte[] cipher = Arrays.copyOfRange(packed, EnvelopeCipher.GCM_IV_LENGTH_BYTES, packed.length);
        return new byte[][]{iv, cipher};
    }
}
