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
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 128-byte on-disk header for ADR-0088 page-authenticated encrypted segments and bundles.
 *
 * <h3>Binary Layout (128 Bytes, Big-Endian)</h3>
 * <pre>
 * Offset  Size  Field
 * 0..3    4B    magic = 0x53504543 ("SPEC")
 * 4..5    2B    schemaVersion (0x0102 for V1.2 encrypted layout)
 * 6       1B    encryptionMode (0x00=Plaintext, 0x01=Platform Envelope, 0x02=BYOK Envelope)
 * 7       1B    cipherSuite (0x01 = AES-256-GCM)
 * 8       1B    kdfId (0x01 = HKDF-SHA256)
 * 9       1B    pageSizeLog2 (16 -> 65,536 bytes = 64 KiB)
 * 10..11  2B    dekVersion (uint16 >= 1)
 * 12      1B    segmentType (0x01=RuntimeBundle, 0x02=PartitionBundle, 0x03=Text, 0x04=Vector, 0x05=Index, 0x06=WAL)
 * 13..15  3B    reserved0 (0)
 * 16..19  4B    nsSalt (random 32-bit segment salt NSALT)
 * 20..35  16B   dekId (UUID / 128-bit identifier of the DEK)
 * 36..67  32B   kekKeyIdHash (SHA-256 of KEK key URI)
 * 68..75  8B    plaintextLen (total plaintext segment length in bytes)
 * 76..95  20B   reserved1 (0)
 * 96..127 32B   headerMac (HMAC-SHA256(SDK, header[0..95]))
 * </pre>
 */
public record EncryptedSegmentHeader(
        int magic,
        short schemaVersion,
        byte encryptionMode,
        byte cipherSuite,
        byte kdfId,
        byte pageSizeLog2,
        short dekVersion,
        byte segmentType,
        int nsSalt,
        byte[] dekId,
        byte[] kekKeyIdHash,
        long plaintextLen,
        byte[] headerMac
) {

    /** 4-byte ASCII magic {@code "SPEC"} ({@code 0x53504543}). */
    public static final int MAGIC = 0x53504543;

    /** Fixed header size in bytes. */
    public static final int HEADER_BYTES = 128;

    /** Offset of the 32-byte HMAC-SHA256 header MAC. */
    public static final int MAC_OFFSET = 96;

    /** Schema version 1.2 ({@code 0x0102}). */
    public static final short SCHEMA_VERSION_V1_2 = 0x0102;

    /** Encryption modes. */
    public static final byte MODE_PLAINTEXT = 0x00;
    public static final byte MODE_PLATFORM_ENVELOPE = 0x01;
    public static final byte MODE_BYOK_ENVELOPE = 0x02;

    /** Cipher suite and KDF identifiers. */
    public static final byte CIPHER_AES_256_GCM = 0x01;
    public static final byte KDF_HKDF_SHA256 = 0x01;

    /** Default page size exponent: $2^{16} = 65,536$ bytes (64 KiB). */
    public static final byte DEFAULT_PAGE_SIZE_LOG2 = 16;
    public static final int DEFAULT_PAGE_SIZE = 1 << DEFAULT_PAGE_SIZE_LOG2;

    /** Segment type discriminators. */
    public static final byte SEGMENT_TYPE_RUNTIME_BUNDLE = 0x01;
    public static final byte SEGMENT_TYPE_PARTITION_BUNDLE = 0x02;
    public static final byte SEGMENT_TYPE_TEXT = 0x03;
    public static final byte SEGMENT_TYPE_VECTOR = 0x04;
    public static final byte SEGMENT_TYPE_INDEX = 0x05;
    public static final byte SEGMENT_TYPE_WAL = 0x06;

    public EncryptedSegmentHeader {
        dekId = dekId != null ? Arrays.copyOf(dekId, 16) : new byte[16];
        kekKeyIdHash = kekKeyIdHash != null ? Arrays.copyOf(kekKeyIdHash, 32) : new byte[32];
        headerMac = headerMac != null ? Arrays.copyOf(headerMac, 32) : new byte[32];
    }

    /**
     * Checks whether the given file starts with the 4-byte {@code "SPEC"} encrypted header magic.
     */
    public static boolean isEncryptedFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            if (ch.size() < 4) {
                return false;
            }
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            int read = ch.read(buf, 0);
            if (read < 4) {
                return false;
            }
            buf.flip();
            return buf.getInt() == MAGIC;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks whether a byte array starts with the 4-byte {@code "SPEC"} magic.
     */
    public static boolean isEncryptedBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }
        return ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt() == MAGIC;
    }

    /**
     * Serializes the 128-byte header and computes its trailing 32-byte HMAC-SHA256 using {@code sdk}.
     */
    public byte[] toBytesWithMac(DestroyableKeyMaterial sdk) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        writePrefix(buf);
        byte[] prefix = new byte[MAC_OFFSET];
        buf.flip();
        buf.get(prefix);

        byte[] mac = computeHeaderMac(sdk, prefix);
        ByteBuffer full = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        full.put(prefix);
        full.put(mac);
        return full.array();
    }

    private void writePrefix(ByteBuffer buf) {
        buf.putInt(magic);
        buf.putShort(schemaVersion);
        buf.put(encryptionMode);
        buf.put(cipherSuite);
        buf.put(kdfId);
        buf.put(pageSizeLog2);
        buf.putShort(dekVersion);
        buf.put(segmentType);
        buf.put((byte) 0).put((byte) 0).put((byte) 0); // reserved0
        buf.putInt(nsSalt);
        buf.put(dekId, 0, 16);
        buf.put(kekKeyIdHash, 0, 32);
        buf.putLong(plaintextLen);
        buf.put(new byte[20]); // reserved1
    }

    /**
     * Parses a 128-byte header from the start of {@code fileBytes}.
     */
    public static EncryptedSegmentHeader read(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < HEADER_BYTES) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                    "Truncated encrypted segment header: expected at least " + HEADER_BYTES + " bytes");
        }
        ByteBuffer buf = ByteBuffer.wrap(fileBytes, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                    String.format("Invalid encrypted segment magic: 0x%08X (expected 0x%08X)", magic, MAGIC));
        }
        short schemaVersion = buf.getShort();
        byte encryptionMode = buf.get();
        byte cipherSuite = buf.get();
        byte kdfId = buf.get();
        byte pageSizeLog2 = buf.get();
        short dekVersion = buf.getShort();
        byte segmentType = buf.get();
        buf.get();
        buf.get();
        buf.get(); // reserved0
        int nsSalt = buf.getInt();
        byte[] dekId = new byte[16];
        buf.get(dekId);
        byte[] kekKeyIdHash = new byte[32];
        buf.get(kekKeyIdHash);
        long plaintextLen = buf.getLong();
        buf.position(MAC_OFFSET);
        byte[] headerMac = new byte[32];
        buf.get(headerMac);

        return new EncryptedSegmentHeader(
                magic, schemaVersion, encryptionMode, cipherSuite, kdfId, pageSizeLog2,
                dekVersion, segmentType, nsSalt, dekId, kekKeyIdHash, plaintextLen, headerMac);
    }

    /**
     * Verifies the 32-byte header MAC over bytes {@code 0..95} using {@code sdk}.
     *
     * @throws SpectorMemoryException with {@link ErrorCode#MEMORY_AUTH_TAG_MISMATCH} if verification fails
     */
    public void verifyMac(byte[] rawHeaderBytes, DestroyableKeyMaterial sdk) {
        byte[] prefix = Arrays.copyOfRange(rawHeaderBytes, 0, MAC_OFFSET);
        byte[] expectedMac = computeHeaderMac(sdk, prefix);
        if (!MessageDigest.isEqual(expectedMac, headerMac)) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                    "Encrypted segment header HMAC verification failed (wrong DEK or tampered header)");
        }
    }

    public static byte[] computeHeaderMac(DestroyableKeyMaterial sdk, byte[] headerPrefix96) {
        byte[] rawKey = sdk.borrowRawBytes();
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, "HmacSHA256");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(headerPrefix96);
        } catch (GeneralSecurityException e) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                    "Failed to compute encrypted segment header HMAC");
        } finally {
            Arrays.fill(rawKey, (byte) 0);
        }
    }
}
