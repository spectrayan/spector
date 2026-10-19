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
package com.spectrayan.spector.kernel.storage;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Page-level {@code AES-256-GCM} codec for ADR-0088 encrypted segments and bundles.
 *
 * <p>Streams 64 KiB pages directly between an off-heap {@link MemorySegment} and a
 * {@link FileChannel} using a bounded 64 KiB page buffer, avoiding full-bundle heap copies.</p>
 */
public final class EncryptedSegmentPageCodec {

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = EnvelopeCipher.GCM_NONCE_BYTES; // 12
    private static final int TAG_BYTES = EnvelopeCipher.GCM_TAG_BYTES;     // 16
    private static final int TAG_BITS = EnvelopeCipher.GCM_TAG_BITS;       // 128
    private static final int PAGE_OVERHEAD_BYTES = NONCE_BYTES + TAG_BYTES; // 28
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EncryptedSegmentPageCodec() {}

    /**
     * Encrypts the contents of {@code masterSegment} into {@code targetPath} using the ADR-0088
     * 128-byte header + 64 KiB {@code AES-256-GCM} page format.
     */
    public static void writeEncryptedSegment(
            Path targetPath,
            MemorySegment masterSegment,
            byte segmentType,
            PayloadEncryptor encryptor) throws IOException {
        if (encryptor == null || !encryptor.isEnabled()) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                    "Cannot write encrypted segment without an enabled PayloadEncryptor");
        }

        long plaintextLen = masterSegment.byteSize();
        int nsSalt = EnvelopeCipher.randomSalt32();
        short dekVersion = (short) encryptor.dekVersion();
        int pageSize = EncryptedSegmentHeader.DEFAULT_PAGE_SIZE;

        try (DestroyableKeyMaterial sdk = resolveSubkey(encryptor, segmentType)) {
            EncryptedSegmentHeader header = new EncryptedSegmentHeader(
                    EncryptedSegmentHeader.MAGIC,
                    EncryptedSegmentHeader.SCHEMA_VERSION_V1_2,
                    encryptor.encryptionMode(),
                    EncryptedSegmentHeader.CIPHER_AES_256_GCM,
                    EncryptedSegmentHeader.KDF_HKDF_SHA256,
                    EncryptedSegmentHeader.DEFAULT_PAGE_SIZE_LOG2,
                    dekVersion,
                    segmentType,
                    nsSalt,
                    encryptor.dekIdBytes(),
                    encryptor.kekKeyIdHashBytes(),
                    plaintextLen,
                    null);
            byte[] headerBytes = header.toBytesWithMac(sdk);

            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmpPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".enc.tmp");

            byte[] rawKey = sdk.borrowRawBytes();
            SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
            try {
                Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                try (FileChannel fc = FileChannel.open(tmpPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    writeFully(fc, ByteBuffer.wrap(headerBytes));

                    byte[] plainPageBuf = new byte[pageSize];
                    long pageIndex = 0;
                    long offset = 0;
                    while (offset < plaintextLen) {
                        int plainPageLen = (int) Math.min(pageSize, plaintextLen - offset);
                        MemorySegment.copy(masterSegment, ValueLayout.JAVA_BYTE, offset,
                                plainPageBuf, 0, plainPageLen);

                        byte[] nonce = new byte[NONCE_BYTES];
                        SECURE_RANDOM.nextBytes(nonce);
                        byte[] aad = buildPageAad(
                                encryptor.tenantId(),
                                encryptor.namespaceId(),
                                segmentType,
                                dekVersion,
                                pageIndex,
                                plainPageLen);

                        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                        cipher.updateAAD(aad);
                        byte[] cipherAndTag = cipher.doFinal(plainPageBuf, 0, plainPageLen);

                        ByteBuffer pageOut = ByteBuffer.allocate(NONCE_BYTES + cipherAndTag.length);
                        pageOut.put(nonce);
                        pageOut.put(cipherAndTag);
                        pageOut.flip();
                        writeFully(fc, pageOut);

                        offset += plainPageLen;
                        pageIndex++;
                    }
                    Arrays.fill(plainPageBuf, (byte) 0);
                    fc.force(true);
                }
                Files.move(tmpPath, targetPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (GeneralSecurityException e) {
                Files.deleteIfExists(tmpPath);
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                        "Failed to encrypt segment page for " + targetPath);
            } finally {
                Arrays.fill(rawKey, (byte) 0);
            }
        }
    }

    /**
     * Returns true if {@code path} begins with the 4-byte ADR-0088 encrypted segment magic ({@code "SPEC"}).
     */
    public static boolean isEncrypted(Path path) {
        return EncryptedSegmentHeader.isEncryptedFile(path);
    }

    /**
     * Reads the 128-byte {@link EncryptedSegmentHeader} from {@code sourcePath}.
     */
    public static EncryptedSegmentHeader readHeader(Path sourcePath) throws IOException {
        try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ)) {
            if (fc.size() < EncryptedSegmentHeader.HEADER_BYTES) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                        "Encrypted segment file smaller than 128-byte header: " + sourcePath);
            }
            byte[] headerBytes = new byte[EncryptedSegmentHeader.HEADER_BYTES];
            readFully(fc, ByteBuffer.wrap(headerBytes), 0);
            return EncryptedSegmentHeader.read(headerBytes);
        }
    }

    /**
     * Reads and authenticates an ADR-0088 encrypted segment file from {@code sourcePath}
     * into a newly allocated page-aligned off-heap {@link MemorySegment} in {@code arena}.
     */
    public static MemorySegment readEncryptedSegment(
            Path sourcePath,
            byte expectedSegmentType,
            PayloadEncryptor encryptor,
            Arena arena) throws IOException {
        if (encryptor == null || !encryptor.isEnabled()) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING,
                    "Encrypted segment requires an active DataEncryptor: " + sourcePath);
        }

        try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ)) {
            long fileSize = fc.size();
            if (fileSize < EncryptedSegmentHeader.HEADER_BYTES) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                        "Encrypted segment file smaller than 128-byte header: " + sourcePath);
            }

            byte[] headerBytes = new byte[EncryptedSegmentHeader.HEADER_BYTES];
            readFully(fc, ByteBuffer.wrap(headerBytes), 0);
            EncryptedSegmentHeader header = EncryptedSegmentHeader.read(headerBytes);

            if (header.segmentType() != expectedSegmentType) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                        String.format("Segment type mismatch in %s: header=0x%02X, expected=0x%02X",
                                sourcePath, header.segmentType(), expectedSegmentType));
            }

            try (DestroyableKeyMaterial sdk = resolveSubkey(encryptor, expectedSegmentType)) {
                header.verifyMac(headerBytes, sdk);

                if (header.dekVersion() != (short) encryptor.dekVersion()) {
                    throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                            String.format("DEK version mismatch in %s: header=%d, encryptor=%d",
                                    sourcePath, Short.toUnsignedInt(header.dekVersion()), encryptor.dekVersion()));
                }

                long plaintextLen = header.plaintextLen();
                int pageSize = 1 << header.pageSizeLog2();
                MemorySegment masterSegment = arena.allocate(Math.max(4096L, plaintextLen), 4096);

                byte[] rawKey = sdk.borrowRawBytes();
                SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
                try {
                    Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                    long filePos = EncryptedSegmentHeader.HEADER_BYTES;
                    long plainOffset = 0;
                    long pageIndex = 0;

                    while (plainOffset < plaintextLen) {
                        int plainPageLen = (int) Math.min(pageSize, plaintextLen - plainOffset);
                        int diskPageLen = plainPageLen + PAGE_OVERHEAD_BYTES;
                        if (filePos + diskPageLen > fileSize) {
                            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                                    "Truncated encrypted page " + pageIndex + " in " + sourcePath);
                        }

                        byte[] diskPage = new byte[diskPageLen];
                        readFully(fc, ByteBuffer.wrap(diskPage), filePos);

                        byte[] nonce = Arrays.copyOfRange(diskPage, 0, NONCE_BYTES);
                        byte[] aad = buildPageAad(
                                encryptor.tenantId(),
                                encryptor.namespaceId(),
                                expectedSegmentType,
                                (short) encryptor.dekVersion(),
                                pageIndex,
                                plainPageLen);

                        cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                        cipher.updateAAD(aad);
                        byte[] plainPage = cipher.doFinal(diskPage, NONCE_BYTES, diskPageLen - NONCE_BYTES);
                        MemorySegment.copy(plainPage, 0, masterSegment, ValueLayout.JAVA_BYTE, plainOffset, plainPage.length);
                        Arrays.fill(plainPage, (byte) 0);

                        filePos += diskPageLen;
                        plainOffset += plainPageLen;
                        pageIndex++;
                    }
                    return masterSegment;
                } catch (AEADBadTagException e) {
                    throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, e,
                            "GCM authentication tag or AAD mismatch in " + sourcePath);
                } catch (GeneralSecurityException e) {
                    throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                            "Failed to decrypt segment page in " + sourcePath);
                } finally {
                    Arrays.fill(rawKey, (byte) 0);
                }
            }
        }
    }

    /**
     * In-memory byte-array encryption using the ADR-0088 128-byte header + 64 KiB page format.
     */
    public static byte[] encrypt(byte[] plaintext, byte segmentType, PayloadEncryptor encryptor) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        if (encryptor == null || !encryptor.isEnabled()) {
            return plaintext;
        }
        int nsSalt = EnvelopeCipher.randomSalt32();
        short dekVersion = (short) encryptor.dekVersion();
        int pageSize = EncryptedSegmentHeader.DEFAULT_PAGE_SIZE;

        try (DestroyableKeyMaterial sdk = resolveSubkey(encryptor, segmentType)) {
            EncryptedSegmentHeader header = new EncryptedSegmentHeader(
                    EncryptedSegmentHeader.MAGIC,
                    EncryptedSegmentHeader.SCHEMA_VERSION_V1_2,
                    encryptor.encryptionMode(),
                    EncryptedSegmentHeader.CIPHER_AES_256_GCM,
                    EncryptedSegmentHeader.KDF_HKDF_SHA256,
                    EncryptedSegmentHeader.DEFAULT_PAGE_SIZE_LOG2,
                    dekVersion,
                    segmentType,
                    nsSalt,
                    encryptor.dekIdBytes(),
                    encryptor.kekKeyIdHashBytes(),
                    plaintext.length,
                    null);
            byte[] headerBytes = header.toBytesWithMac(sdk);

            int numPages = (plaintext.length + pageSize - 1) / pageSize;
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    EncryptedSegmentHeader.HEADER_BYTES + plaintext.length + Math.max(1, numPages) * PAGE_OVERHEAD_BYTES);
            out.writeBytes(headerBytes);

            byte[] rawKey = sdk.borrowRawBytes();
            SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
            try {
                Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                long pageIndex = 0;
                int offset = 0;
                if (plaintext.length == 0) {
                    byte[] nonce = new byte[NONCE_BYTES];
                    SECURE_RANDOM.nextBytes(nonce);
                    byte[] aad = buildPageAad(encryptor.tenantId(), encryptor.namespaceId(), segmentType, dekVersion, 0L, 0);
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                    cipher.updateAAD(aad);
                    out.writeBytes(nonce);
                    out.writeBytes(cipher.doFinal());
                } else {
                    while (offset < plaintext.length) {
                        int plainPageLen = Math.min(pageSize, plaintext.length - offset);
                        byte[] nonce = new byte[NONCE_BYTES];
                        SECURE_RANDOM.nextBytes(nonce);
                        byte[] aad = buildPageAad(
                                encryptor.tenantId(),
                                encryptor.namespaceId(),
                                segmentType,
                                dekVersion,
                                pageIndex,
                                plainPageLen);
                        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                        cipher.updateAAD(aad);
                        byte[] cipherAndTag = cipher.doFinal(plaintext, offset, plainPageLen);
                        out.writeBytes(nonce);
                        out.writeBytes(cipherAndTag);
                        offset += plainPageLen;
                        pageIndex++;
                    }
                }
                return out.toByteArray();
            } catch (GeneralSecurityException e) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                        "Failed to encrypt segment bytes");
            } finally {
                Arrays.fill(rawKey, (byte) 0);
            }
        }
    }

    /**
     * In-memory byte-array decryption using the ADR-0088 128-byte header + 64 KiB page format.
     */
    public static byte[] decrypt(byte[] fileBytes, byte expectedSegmentType, PayloadEncryptor encryptor) {
        if (fileBytes == null) {
            throw new IllegalArgumentException("fileBytes must not be null");
        }
        if (encryptor == null || !encryptor.isEnabled()) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING,
                    "Encrypted segment requires an active DataEncryptor");
        }
        EncryptedSegmentHeader header = EncryptedSegmentHeader.read(fileBytes);
        if (header.segmentType() != expectedSegmentType) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                    String.format("Segment type mismatch: header=0x%02X, expected=0x%02X",
                            header.segmentType(), expectedSegmentType));
        }

        try (DestroyableKeyMaterial sdk = resolveSubkey(encryptor, expectedSegmentType)) {
            header.verifyMac(fileBytes, sdk);
            if (header.dekVersion() != (short) encryptor.dekVersion()) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                        "DEK version mismatch");
            }
            int plaintextLen = Math.toIntExact(header.plaintextLen());
            int pageSize = 1 << header.pageSizeLog2();
            byte[] out = new byte[plaintextLen];

            byte[] rawKey = sdk.borrowRawBytes();
            SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
            try {
                Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                int filePos = EncryptedSegmentHeader.HEADER_BYTES;
                int plainOffset = 0;
                long pageIndex = 0;
                if (plaintextLen == 0) {
                    if (filePos + PAGE_OVERHEAD_BYTES > fileBytes.length) {
                        throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                                "Truncated empty encrypted page");
                    }
                    byte[] nonce = Arrays.copyOfRange(fileBytes, filePos, filePos + NONCE_BYTES);
                    byte[] aad = buildPageAad(encryptor.tenantId(), encryptor.namespaceId(),
                            expectedSegmentType, (short) encryptor.dekVersion(), 0L, 0);
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                    cipher.updateAAD(aad);
                    cipher.doFinal(fileBytes, filePos + NONCE_BYTES, TAG_BYTES);
                    return out;
                }
                while (plainOffset < plaintextLen) {
                    int plainPageLen = Math.min(pageSize, plaintextLen - plainOffset);
                    int diskPageLen = plainPageLen + PAGE_OVERHEAD_BYTES;
                    if (filePos + diskPageLen > fileBytes.length) {
                        throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                                "Truncated encrypted page " + pageIndex);
                    }
                    byte[] nonce = Arrays.copyOfRange(fileBytes, filePos, filePos + NONCE_BYTES);
                    byte[] aad = buildPageAad(
                            encryptor.tenantId(),
                            encryptor.namespaceId(),
                            expectedSegmentType,
                            (short) encryptor.dekVersion(),
                            pageIndex,
                            plainPageLen);
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                    cipher.updateAAD(aad);
                    byte[] plainPage = cipher.doFinal(fileBytes, filePos + NONCE_BYTES, diskPageLen - NONCE_BYTES);
                    System.arraycopy(plainPage, 0, out, plainOffset, plainPage.length);
                    Arrays.fill(plainPage, (byte) 0);

                    filePos += diskPageLen;
                    plainOffset += plainPageLen;
                    pageIndex++;
                }
                return out;
            } catch (AEADBadTagException e) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, e,
                        "GCM authentication tag or AAD mismatch");
            } catch (GeneralSecurityException e) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                        "Failed to decrypt segment bytes");
            } finally {
                Arrays.fill(rawKey, (byte) 0);
            }
        }
    }

    /**
     * Builds the structured AAD for an encrypted segment page per ADR-0088 §4.3:
     * {@code UTF8(tenantId) || 0x00 || UTF8(namespaceId) || 0x00 || segmentType (1B) || BE16(dekVersion) || BE64(pageIndex) || BE32(plainByteLen)}.
     */
    public static byte[] buildPageAad(
            String tenantId,
            String namespaceId,
            byte segmentType,
            short dekVersion,
            long pageIndex,
            int plainByteLen) {
        byte[] tenantBytes = (tenantId != null ? tenantId : "default").getBytes(StandardCharsets.UTF_8);
        byte[] nsBytes = (namespaceId != null ? namespaceId : "default").getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(tenantBytes.length + 1 + nsBytes.length + 1 + 1 + 2 + 8 + 4)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put(tenantBytes).put((byte) 0);
        buf.put(nsBytes).put((byte) 0);
        buf.put(segmentType);
        buf.putShort(dekVersion);
        buf.putLong(pageIndex);
        buf.putInt(plainByteLen);
        return buf.array();
    }

    private static DestroyableKeyMaterial resolveSubkey(PayloadEncryptor encryptor, byte segmentType) {
        DestroyableKeyMaterial sdk = encryptor.segmentSubkey(segmentType);
        if (sdk != null) {
            return sdk;
        }
        // Fallback for custom test lambdas implementing PayloadEncryptor without segmentSubkey
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update((byte) segmentType);
            sha256.update(encryptor.tenantId().getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) 0);
            sha256.update(encryptor.namespaceId().getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = sha256.digest();
            return DestroyableKeyMaterial.fromBytes(keyBytes);
        } catch (GeneralSecurityException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "Failed to derive fallback segment subkey");
        }
    }

    private static void writeFully(FileChannel fc, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            fc.write(buf);
        }
    }

    private static void readFully(FileChannel fc, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            int r = fc.read(buf, pos);
            if (r < 0) {
                throw new IOException("Unexpected EOF at offset " + pos);
            }
            pos += r;
        }
    }
}
