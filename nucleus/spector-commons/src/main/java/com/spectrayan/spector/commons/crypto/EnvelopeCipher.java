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
package com.spectrayan.spector.commons.crypto;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Zero-dependency envelope encryption and key-derivation primitive using standard JDK
 * {@code AES/GCM/NoPadding} (256-bit key, 96-bit IV, 128-bit authentication tag) and
 * RFC 5869 {@code HKDF-SHA256} (ADR-0088 §3, §5.1, Issue #1070).
 */
public final class EnvelopeCipher {

    public static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    public static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    public static final int DEK_LENGTH_BYTES = 32;
    public static final int GCM_IV_LENGTH_BYTES = 12;
    public static final int GCM_NONCE_BYTES = GCM_IV_LENGTH_BYTES;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int GCM_TAG_BITS = GCM_TAG_LENGTH_BITS;
    public static final int GCM_TAG_LENGTH_BYTES = 16;
    public static final int GCM_TAG_BYTES = GCM_TAG_LENGTH_BYTES;
    private static final int SHA256_HASH_LEN = 32;

    public static final String SUBKEY_PAYLOAD = "dek_payload";
    public static final String SUBKEY_VECTOR = "dek_vector";
    public static final String SUBKEY_WAL = "dek_wal";
    public static final String SUBKEY_INDEX = "dek_index";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final ThreadLocal<Cipher> ENCRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(AES_GCM_ALGORITHM);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "AES-256-GCM cipher initialization failed: " + e.getMessage());
        }
    });

    private static final ThreadLocal<Cipher> DECRYPT_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(AES_GCM_ALGORITHM);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "AES-256-GCM cipher initialization failed: " + e.getMessage());
        }
    });

    private EnvelopeCipher() {
    }

    /**
     * Generates a cryptographically random 32-bit salt for per-segment page nonces (ADR-0088 §5.2).
     */
    public static int randomSalt32() {
        return SECURE_RANDOM.nextInt();
    }

    /**
     * Constructs a deterministic 96-bit (12-byte) GCM nonce from a 32-bit segment salt and
     * a 64-bit monotonic page sequence counter (ADR-0088 §5.2).
     */
    public static byte[] pageNonce(int nsSalt, long pageSeq) {
        byte[] nonce = new byte[GCM_IV_LENGTH_BYTES];
        java.nio.ByteBuffer.wrap(nonce).order(java.nio.ByteOrder.BIG_ENDIAN)
                .putInt(nsSalt)
                .putLong(pageSeq);
        return nonce;
    }

    /**
     * Immutable AES-256-GCM ciphertext envelope containing the 96-bit IV and the
     * ciphertext with appended 128-bit authentication tag.
     */
    public record CiphertextEnvelope(byte[] iv, byte[] ciphertextWithTag) {
        public CiphertextEnvelope {
            Objects.requireNonNull(iv, "iv must not be null");
            Objects.requireNonNull(ciphertextWithTag, "ciphertextWithTag must not be null");
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("GCM IV must be " + GCM_IV_LENGTH_BYTES + " bytes, got " + iv.length);
            }
            if (ciphertextWithTag.length < GCM_TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException("Ciphertext must include at least " + GCM_TAG_LENGTH_BYTES + "-byte GCM auth tag");
            }
            iv = iv.clone();
            ciphertextWithTag = ciphertextWithTag.clone();
        }

        @Override
        public byte[] iv() {
            return iv.clone();
        }

        @Override
        public byte[] ciphertextWithTag() {
            return ciphertextWithTag.clone();
        }

        /**
         * Extracts the 128-bit (16-byte) GCM authentication tag from the end of the ciphertext.
         */
        public byte[] authTag() {
            return Arrays.copyOfRange(
                    ciphertextWithTag,
                    ciphertextWithTag.length - GCM_TAG_LENGTH_BYTES,
                    ciphertextWithTag.length);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CiphertextEnvelope that)) return false;
            return Arrays.equals(iv, that.iv) && Arrays.equals(ciphertextWithTag, that.ciphertextWithTag);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(iv) + Arrays.hashCode(ciphertextWithTag);
        }
    }

    /**
     * Generates a cryptographically random 256-bit (32-byte) Data-Encryption Key (DEK).
     */
    public static DestroyableKeyMaterial generateDek() {
        byte[] raw = new byte[DEK_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        return new DestroyableKeyMaterial(raw, true);
    }

    /**
     * Generates a cryptographically random 96-bit (12-byte) GCM initialization vector.
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * Builds canonical Additional Authenticated Data (AAD) for wrapping/unwrapping a namespace DEK:
     * {@code tenantId:namespaceId:v<dekVersion>}.
     */
    public static byte[] buildNamespaceDekAad(String tenantId, String namespaceId, int dekVersion) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        String canonical = tenantId.trim() + ":" + namespaceId.trim() + ":v" + dekVersion;
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Derives a domain-separated 256-bit subkey from a namespace DEK using RFC 5869 HKDF-SHA256
     * (ADR-0088 §3).
     *
     * @param masterDek     256-bit namespace DEK
     * @param tenantId      owning tenant ID
     * @param namespaceId   owning namespace ID
     * @param dekVersion    DEK version number
     * @param domainPurpose subkey domain (e.g. {@link #SUBKEY_PAYLOAD}, {@link #SUBKEY_VECTOR},
     *                      {@link #SUBKEY_WAL}, {@link #SUBKEY_INDEX})
     * @return zeroizable 256-bit derived subkey
     */
    public static DestroyableKeyMaterial deriveSubkey(
            DestroyableKeyMaterial masterDek,
            String tenantId,
            String namespaceId,
            int dekVersion,
            String domainPurpose) {
        Objects.requireNonNull(masterDek, "masterDek must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(domainPurpose, "domainPurpose must not be null");

        byte[] salt = ("spector-dek-v1:" + tenantId.trim() + ":" + namespaceId.trim())
                .getBytes(StandardCharsets.UTF_8);
        byte[] info = ("spector:" + domainPurpose.trim() + ":v" + dekVersion)
                .getBytes(StandardCharsets.UTF_8);

        byte[] ikm = masterDek.bytes();
        try {
            byte[] derived = hkdfSha256(ikm, salt, info, DEK_LENGTH_BYTES);
            return new DestroyableKeyMaterial(derived, true);
        } finally {
            Arrays.fill(ikm, (byte) 0);
            VarHandle.fullFence();
        }
    }

    /**
     * Performs RFC 5869 HKDF-SHA256 (Extract-and-Expand) to derive {@code lengthBytes} of key material.
     */
    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int lengthBytes) {
        Objects.requireNonNull(ikm, "ikm must not be null");
        if (ikm.length == 0) {
            throw new IllegalArgumentException("ikm must not be empty");
        }
        if (lengthBytes <= 0 || lengthBytes > 255 * SHA256_HASH_LEN) {
            throw new IllegalArgumentException("Invalid HKDF output length: " + lengthBytes);
        }

        byte[] prk = hkdfExtract(salt, ikm);
        try {
            return hkdfExpand(prk, info, lengthBytes);
        } finally {
            Arrays.fill(prk, (byte) 0);
            VarHandle.fullFence();
        }
    }

    /**
     * RFC 5869 §2.2 HKDF-Extract(salt, IKM) -> PRK.
     */
    public static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
        try {
            byte[] effectiveSalt = (salt == null || salt.length == 0)
                    ? new byte[SHA256_HASH_LEN]
                    : salt;
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(new SecretKeySpec(effectiveSalt, HMAC_SHA256_ALGORITHM));
            return mac.doFinal(ikm);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "HKDF-Extract failed: " + e.getMessage());
        }
    }

    /**
     * RFC 5869 §2.3 HKDF-Expand(PRK, info, L) -> OKM.
     */
    public static byte[] hkdfExpand(byte[] prk, byte[] info, int lengthBytes) {
        try {
            byte[] effectiveInfo = info != null ? info : new byte[0];
            int n = (int) Math.ceil((double) lengthBytes / SHA256_HASH_LEN);
            byte[] okm = new byte[lengthBytes];
            byte[] t = new byte[0];
            int offset = 0;

            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec prkSpec = new SecretKeySpec(prk, HMAC_SHA256_ALGORITHM);

            for (int i = 1; i <= n; i++) {
                mac.init(prkSpec);
                mac.update(t);
                mac.update(effectiveInfo);
                mac.update((byte) i);
                t = mac.doFinal();
                int copyLen = Math.min(SHA256_HASH_LEN, lengthBytes - offset);
                System.arraycopy(t, 0, okm, offset, copyLen);
                offset += copyLen;
            }
            Arrays.fill(t, (byte) 0);
            return okm;
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "HKDF-Expand failed: " + e.getMessage());
        }
    }

    /**
     * Encrypts {@code plaintext} using AES-256-GCM with a freshly generated 96-bit IV and optional AAD.
     */
    public static CiphertextEnvelope encrypt(DestroyableKeyMaterial key, byte[] plaintext, byte[] aad) {
        Objects.requireNonNull(key, "key must not be null");
        byte[] keyBytes = key.bytes();
        try {
            return encrypt(keyBytes, generateIv(), plaintext, aad);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            VarHandle.fullFence();
        }
    }

    /**
     * Encrypts {@code plaintext} using AES-256-GCM with the provided 256-bit key, 96-bit IV, and optional AAD.
     */
    public static CiphertextEnvelope encrypt(byte[] keyBytes, byte[] iv, byte[] plaintext, byte[] aad) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        if (keyBytes.length != DEK_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be " + DEK_LENGTH_BYTES + " bytes, got " + keyBytes.length);
        }
        if (iv.length != GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("GCM IV must be " + GCM_IV_LENGTH_BYTES + " bytes, got " + iv.length);
        }

        try {
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = ENCRYPT_CIPHER.get();
            try {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            } catch (java.security.InvalidAlgorithmParameterException retryEx) {
                cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
                ENCRYPT_CIPHER.set(cipher);
            }
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            byte[] ciphertextWithTag = cipher.doFinal(plaintext);
            return new CiphertextEnvelope(iv, ciphertextWithTag);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "AES-256-GCM encryption failed: " + e.getMessage());
        }
    }

    /**
     * Decrypts and authenticates {@code ciphertextWithTag} using AES-256-GCM.
     *
     * @throws SpectorMemoryException with {@link ErrorCode#MEMORY_AUTH_TAG_MISMATCH} ({@code SPE-310-026})
     *                                if the 128-bit GCM authentication tag or AAD fails verification
     */
    public static byte[] decrypt(DestroyableKeyMaterial key, byte[] iv, byte[] ciphertextWithTag, byte[] aad) {
        Objects.requireNonNull(key, "key must not be null");
        byte[] keyBytes = key.bytes();
        try {
            return decrypt(keyBytes, iv, ciphertextWithTag, aad);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            VarHandle.fullFence();
        }
    }

    /**
     * Decrypts and authenticates {@code ciphertextWithTag} using AES-256-GCM.
     *
     * @throws SpectorMemoryException with {@link ErrorCode#MEMORY_AUTH_TAG_MISMATCH} ({@code SPE-310-026})
     *                                if the 128-bit GCM authentication tag or AAD fails verification
     */
    public static byte[] decrypt(byte[] keyBytes, byte[] iv, byte[] ciphertextWithTag, byte[] aad) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        Objects.requireNonNull(iv, "iv must not be null");
        Objects.requireNonNull(ciphertextWithTag, "ciphertextWithTag must not be null");
        if (keyBytes.length != DEK_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be " + DEK_LENGTH_BYTES + " bytes, got " + keyBytes.length);
        }
        if (iv.length != GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("GCM IV must be " + GCM_IV_LENGTH_BYTES + " bytes, got " + iv.length);
        }

        try {
            Cipher cipher = DECRYPT_CIPHER.get();
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertextWithTag);
        } catch (AEADBadTagException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, e,
                    "tag or AAD mismatch");
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "AES-256-GCM decryption failed: " + e.getMessage());
        }
    }
}
