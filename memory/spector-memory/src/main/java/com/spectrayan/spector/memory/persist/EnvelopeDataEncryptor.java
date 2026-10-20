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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.core.cognitive.SynapticTag128;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader;
import com.spectrayan.spector.memory.error.SpectorEncryptionException;
import com.spectrayan.spector.memory.synapse.KeyedSynapticTagEncoder;

import javax.security.auth.Destroyable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Production {@link DataEncryptor} implementing ADR-0088 envelope encryption for
 * Spector memory payloads, quantized vectors, write-ahead logs, and synaptic tag Bloom filters.
 *
 * <h3>Cryptographic Key Hierarchy (ADR-0088 §2)</h3>
 * <ul>
 *   <li>{@code dek_payload} — {@code HKDF-SHA256(DEK, info="spector/v1/subkey/dek_payload")} for text blobs</li>
 *   <li>{@code dek_vector} — {@code HKDF-SHA256(DEK, info="spector/v1/subkey/dek_vector")} for vectors &amp; partition bundles</li>
 *   <li>{@code dek_index} — {@code HKDF-SHA256(DEK, info="spector/v1/subkey/dek_index")} for runtime bundles &amp; indexes</li>
 *   <li>{@code dek_wal} — {@code HKDF-SHA256(DEK, info="spector/v1/subkey/dek_wal")} for WAL frames</li>
 *   <li>{@code dek_tag} — {@code HKDF-SHA256(DEK, info="spector/v1/subkey/dek_tag")} for {@link KeyedSynapticTagEncoder}</li>
 * </ul>
 *
 * <h3>Fail-Closed Semantics</h3>
 * <p>Any authentication tag mismatch, cross-namespace AAD mismatch, destroyed DEK, or KMS/BYOK
 * key revocation throws {@link SpectorEncryptionException} with the corresponding {@link ErrorCode}.</p>
 */
public final class EnvelopeDataEncryptor implements DataEncryptor, Destroyable, AutoCloseable {

    private static final String SUBKEY_INFO_PREFIX = "spector/v1/subkey/";

    private final String tenantId;
    private final String namespaceId;
    private final int dekVersion;
    private final byte encryptionMode;
    private final byte[] dekIdBytes;
    private final byte[] kekKeyIdHashBytes;
    private final byte[] domainSalt;
    private final byte[] textRecordAad;
    private final byte[] vectorRecordAad;

    private final DestroyableKeyMaterial masterDek;
    private final DestroyableKeyMaterial payloadKey;
    private final DestroyableKeyMaterial vectorKey;
    private final DestroyableKeyMaterial indexKey;
    private final DestroyableKeyMaterial walKey;
    private final DestroyableKeyMaterial tagKey;
    private final KeyedSynapticTagEncoder tagEncoder;

    private final BooleanSupplier keyRevokedProbe;
    private final BooleanSupplier kmsAvailableProbe;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public EnvelopeDataEncryptor(
            String tenantId,
            String namespaceId,
            DestroyableKeyMaterial namespaceDek,
            int dekVersion,
            byte encryptionMode,
            byte[] dekIdBytes,
            byte[] kekKeyIdHashBytes,
            BooleanSupplier keyRevokedProbe,
            BooleanSupplier kmsAvailableProbe) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.namespaceId = Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (namespaceDek == null || namespaceDek.isDestroyed()) {
            throw new SpectorEncryptionException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                    "Namespace DEK must be non-null and active");
        }
        if (dekVersion < 1) {
            throw new IllegalArgumentException("dekVersion must be >= 1");
        }
        this.dekVersion = dekVersion;
        this.encryptionMode = encryptionMode;
        this.dekIdBytes = dekIdBytes != null ? Arrays.copyOf(dekIdBytes, 16) : deriveDefaultDekId(tenantId, namespaceId, dekVersion);
        this.kekKeyIdHashBytes = kekKeyIdHashBytes != null ? Arrays.copyOf(kekKeyIdHashBytes, 32) : new byte[32];
        this.keyRevokedProbe = keyRevokedProbe != null ? keyRevokedProbe : () -> false;
        this.kmsAvailableProbe = kmsAvailableProbe != null ? kmsAvailableProbe : () -> true;

        this.domainSalt = buildDomainSalt(tenantId, namespaceId, dekVersion);
        this.textRecordAad = buildRecordAad(EncryptedSegmentHeader.SEGMENT_TYPE_TEXT);
        this.vectorRecordAad = buildRecordAad(EncryptedSegmentHeader.SEGMENT_TYPE_VECTOR);
        this.masterDek = namespaceDek;
        this.payloadKey = EnvelopeCipher.deriveSubkey(namespaceDek, tenantId, namespaceId, dekVersion, EnvelopeCipher.SUBKEY_PAYLOAD);
        this.vectorKey = EnvelopeCipher.deriveSubkey(namespaceDek, tenantId, namespaceId, dekVersion, EnvelopeCipher.SUBKEY_VECTOR);
        this.indexKey = EnvelopeCipher.deriveSubkey(namespaceDek, tenantId, namespaceId, dekVersion, EnvelopeCipher.SUBKEY_INDEX);
        this.walKey = EnvelopeCipher.deriveSubkey(namespaceDek, tenantId, namespaceId, dekVersion, EnvelopeCipher.SUBKEY_WAL);
        this.tagKey = EnvelopeCipher.deriveSubkey(namespaceDek, tenantId, namespaceId, dekVersion, "dek_tag");

        byte[] rawTagKey = this.tagKey.borrowRawBytes();
        try {
            this.tagEncoder = new KeyedSynapticTagEncoder(
                    new javax.crypto.spec.SecretKeySpec(rawTagKey, "HmacSHA256"));
        } finally {
            Arrays.fill(rawTagKey, (byte) 0);
        }
    }

    public EnvelopeDataEncryptor(
            String tenantId,
            String namespaceId,
            int dekVersion,
            DestroyableKeyMaterial namespaceDek) {
        this(
                tenantId,
                namespaceId,
                namespaceDek,
                dekVersion,
                EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                null,
                null,
                null,
                null);
    }

    public EnvelopeDataEncryptor(
            String tenantId,
            String namespaceId,
            int dekVersion,
            byte encryptionMode,
            String kekKeyId,
            DestroyableKeyMaterial namespaceDek) {
        this(
                tenantId,
                namespaceId,
                namespaceDek,
                dekVersion,
                encryptionMode,
                null,
                hashKekKeyId(kekKeyId),
                null,
                null);
    }

    public EnvelopeDataEncryptor(
            String tenantId,
            String namespaceId,
            int dekVersion,
            byte encryptionMode,
            String kekKeyId,
            DestroyableKeyMaterial namespaceDek,
            BooleanSupplier keyRevokedProbe) {
        this(
                tenantId,
                namespaceId,
                namespaceDek,
                dekVersion,
                encryptionMode,
                null,
                hashKekKeyId(kekKeyId),
                keyRevokedProbe,
                null);
    }

    private static byte[] hashKekKeyId(String kekKeyId) {
        if (kekKeyId == null || kekKeyId.isBlank()) {
            return new byte[32];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(kekKeyId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }

    /**
     * Creates an {@link EnvelopeDataEncryptor} for a tenant and namespace from a 256-bit DEK.
     */
    public static EnvelopeDataEncryptor forNamespace(
            String tenantId,
            String namespaceId,
            DestroyableKeyMaterial namespaceDek,
            int dekVersion) {
        return new EnvelopeDataEncryptor(
                tenantId,
                namespaceId,
                namespaceDek,
                dekVersion,
                EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                null,
                null,
                null,
                null);
    }

    /**
     * Creates an {@link EnvelopeDataEncryptor} for a tenant and namespace from raw 32-byte key material.
     */
    public static EnvelopeDataEncryptor forNamespace(
            String tenantId,
            String namespaceId,
            byte[] rawDekBytes) {
        return forNamespace(tenantId, namespaceId, DestroyableKeyMaterial.fromBytes(rawDekBytes), 1);
    }

    @Override
    public String tenantId() {
        return tenantId;
    }

    @Override
    public String namespaceId() {
        return namespaceId;
    }

    @Override
    public int dekVersion() {
        return dekVersion;
    }

    @Override
    public byte encryptionMode() {
        return encryptionMode;
    }

    @Override
    public byte[] dekIdBytes() {
        return Arrays.copyOf(dekIdBytes, 16);
    }

    @Override
    public byte[] kekKeyIdHashBytes() {
        return Arrays.copyOf(kekKeyIdHashBytes, 32);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public byte[] encryptText(byte[] plaintext) {
        ensureActive();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            return packEnvelope(EnvelopeCipher.encrypt(payloadKey, plaintext, textRecordAad));
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to encrypt text payload for namespace '" + namespaceId + "'");
        }
    }

    @Override
    public byte[] decryptText(byte[] ciphertext) {
        ensureActive();
        if (ciphertext == null) {
            throw new IllegalArgumentException("ciphertext must not be null");
        }
        try {
            return unpackAndDecrypt(payloadKey, ciphertext, textRecordAad);
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to decrypt text payload for namespace '" + namespaceId + "'");
        }
    }

    @Override
    public byte[] encryptPayload(byte[] plaintext) {
        ensureActive();
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        try {
            return packEnvelope(EnvelopeCipher.encrypt(vectorKey, plaintext, vectorRecordAad));
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to encrypt vector/binary payload for namespace '" + namespaceId + "'");
        }
    }

    @Override
    public byte[] decryptPayload(byte[] ciphertext) {
        ensureActive();
        if (ciphertext == null) {
            throw new IllegalArgumentException("ciphertext must not be null");
        }
        try {
            return unpackAndDecrypt(vectorKey, ciphertext, vectorRecordAad);
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to decrypt vector/binary payload for namespace '" + namespaceId + "'");
        }
    }

    /**
     * Encrypts a WAL frame payload bound to sequence number, event type, and memory ID.
     */
    public byte[] encryptWalPayload(long sequence, byte eventTypeCode, String memoryId, byte[] plaintext) {
        ensureActive();
        if (plaintext == null) {
            return new byte[0];
        }
        try {
            byte[] aad = buildWalAad(sequence, eventTypeCode, memoryId);
            return packEnvelope(EnvelopeCipher.encrypt(walKey, plaintext, aad));
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to encrypt WAL frame seq=" + sequence + " for namespace '" + namespaceId + "'");
        }
    }

    /**
     * Decrypts a WAL frame payload bound to sequence number, event type, and memory ID.
     */
    public byte[] decryptWalPayload(long sequence, byte eventTypeCode, String memoryId, byte[] ciphertext) {
        ensureActive();
        if (ciphertext == null || ciphertext.length == 0) {
            return new byte[0];
        }
        try {
            byte[] aad = buildWalAad(sequence, eventTypeCode, memoryId);
            return unpackAndDecrypt(walKey, ciphertext, aad);
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to decrypt WAL frame seq=" + sequence + " for namespace '" + namespaceId + "'");
        }
    }

    private static byte[] packEnvelope(EnvelopeCipher.CiphertextEnvelope env) {
        byte[] iv = env.iv();
        byte[] ct = env.ciphertextWithTag();
        byte[] packed = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(ct, 0, packed, iv.length, ct.length);
        return packed;
    }

    private static byte[] unpackAndDecrypt(DestroyableKeyMaterial key, byte[] packed, byte[] aad) {
        if (packed.length < EnvelopeCipher.GCM_IV_LENGTH_BYTES + EnvelopeCipher.GCM_TAG_LENGTH_BYTES) {
            throw new SpectorEncryptionException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                    "Truncated AES-256-GCM envelope (" + packed.length + " bytes)");
        }
        byte[] iv = Arrays.copyOfRange(packed, 0, EnvelopeCipher.GCM_IV_LENGTH_BYTES);
        byte[] ct = Arrays.copyOfRange(packed, EnvelopeCipher.GCM_IV_LENGTH_BYTES, packed.length);
        return EnvelopeCipher.decrypt(key, iv, ct, aad);
    }

    @Override
    public long encodeTag(String tag) {
        ensureActive();
        return tagEncoder.encodeTag(tag);
    }

    @Override
    public SynapticTag128 encodeTag128(String tag) {
        ensureActive();
        return tagEncoder.encodeTag128(tag);
    }

    @Override
    public DestroyableKeyMaterial segmentSubkey(byte segmentType) {
        ensureActive();
        String domain = switch (segmentType) {
            case EncryptedSegmentHeader.SEGMENT_TYPE_RUNTIME_BUNDLE,
                 EncryptedSegmentHeader.SEGMENT_TYPE_INDEX -> EnvelopeCipher.SUBKEY_INDEX;
            case EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                 EncryptedSegmentHeader.SEGMENT_TYPE_VECTOR -> EnvelopeCipher.SUBKEY_VECTOR;
            case EncryptedSegmentHeader.SEGMENT_TYPE_TEXT -> EnvelopeCipher.SUBKEY_PAYLOAD;
            case EncryptedSegmentHeader.SEGMENT_TYPE_WAL -> EnvelopeCipher.SUBKEY_WAL;
            default -> "dek_segment_" + Byte.toUnsignedInt(segmentType);
        };
        try {
            return EnvelopeCipher.deriveSubkey(masterDek, tenantId, namespaceId, dekVersion, domain);
        } catch (RuntimeException e) {
            throw translateException(e, "Failed to derive segment subkey for namespace '" + namespaceId + "'");
        }
    }

    private void ensureActive() {
        if (destroyed.get() || masterDek.isDestroyed()) {
            throw new SpectorEncryptionException(ErrorCode.BYOK_KEY_REVOKED,
                    "Namespace DEK has been destroyed or revoked for namespace '" + namespaceId + "'");
        }
        if (keyRevokedProbe.getAsBoolean()) {
            throw new SpectorEncryptionException(ErrorCode.BYOK_KEY_REVOKED,
                    "BYOK key is revoked for tenant '" + tenantId + "', namespace '" + namespaceId + "'");
        }
        if (!kmsAvailableProbe.getAsBoolean()) {
            throw new SpectorEncryptionException(ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    "KMS provider unavailable (fail-closed) for tenant '" + tenantId + "', namespace '" + namespaceId + "'");
        }
    }

    private byte[] buildRecordAad(byte segmentType) {
        byte[] tenantBytes = tenantId.getBytes(StandardCharsets.UTF_8);
        byte[] nsBytes = namespaceId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(tenantBytes.length + 1 + nsBytes.length + 1 + 1 + 2)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(tenantBytes).put((byte) 0);
        buf.put(nsBytes).put((byte) 0);
        buf.put(segmentType);
        buf.putShort((short) dekVersion);
        return buf.array();
    }

    private byte[] buildWalAad(long sequence, byte eventTypeCode, String memoryId) {
        byte[] tenantBytes = tenantId.getBytes(StandardCharsets.UTF_8);
        byte[] nsBytes = namespaceId.getBytes(StandardCharsets.UTF_8);
        byte[] idBytes = (memoryId != null ? memoryId : "").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(tenantBytes.length + 1 + nsBytes.length + 1 + 1 + 2 + 8 + 1 + idBytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(tenantBytes).put((byte) 0);
        buf.put(nsBytes).put((byte) 0);
        buf.put(EncryptedSegmentHeader.SEGMENT_TYPE_WAL);
        buf.putShort((short) dekVersion);
        buf.putLong(sequence);
        buf.put(eventTypeCode);
        buf.put(idBytes);
        return buf.array();
    }

    private static byte[] buildDomainSalt(String tenantId, String namespaceId, int dekVersion) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenantId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(namespaceId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((byte) ((dekVersion >>> 8) & 0xFF));
            digest.update((byte) (dekVersion & 0xFF));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e, "SHA-256 unavailable");
        }
    }

    private static byte[] deriveDefaultDekId(String tenantId, String namespaceId, int dekVersion) {
        byte[] hash = buildDomainSalt(tenantId, namespaceId, dekVersion);
        return Arrays.copyOf(hash, 16);
    }

    private static SpectorEncryptionException translateException(RuntimeException e, String contextMessage) {
        if (e instanceof SpectorEncryptionException see) {
            return see;
        }
        if (e instanceof SpectorException se) {
            return new SpectorEncryptionException(se.errorCode(), e, contextMessage + ": " + e.getMessage());
        }
        return new SpectorEncryptionException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e, contextMessage + ": " + e.getMessage());
    }

    @Override
    public boolean isDestroyed() {
        return destroyed.get() || masterDek.isDestroyed();
    }

    @Override
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            payloadKey.close();
            vectorKey.close();
            indexKey.close();
            walKey.close();
            tagKey.close();
            masterDek.close();
        }
    }

    @Override
    public void close() {
        destroy();
    }
}
