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
import java.util.BitSet;

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
     * Lazily decrypted, dirty-page-tracked off-heap view of an ADR-0088 encrypted bundle file.
     *
     * <p>On open, validates the 128-byte {@link EncryptedSegmentHeader} HMAC and decrypts only page 0
     * (which holds the {@code BundleDirectory}). Subsequent region pages are decrypted on demand via
     * {@link #ensureRangeDecrypted(long, long)} before caller access. On {@link #flush(Path)}, only pages
     * whose content hash changed since load or last flush are re-encrypted with fresh random 96-bit nonces
     * and written in place; a clean session with zero mutated pages performs zero page encryptions and
     * zero disk writes.</p>
     */
    public static final class LazyEncryptedSegment {

        private final Path sourcePath;
        private final MemorySegment masterSegment;
        private final long plaintextLen;
        private final int pageSize;
        private final int pageCount;
        private final byte segmentType;
        private volatile PayloadEncryptor encryptor;
        private final BitSet decryptedPages;
        private final BitSet accessiblePages;
        private final long[] cleanPageHashLo;
        private final long[] cleanPageHashHi;
        private volatile boolean forceFullRewrite;
        private volatile int lastFlushDirtyPages;

        private LazyEncryptedSegment(
                Path sourcePath,
                MemorySegment masterSegment,
                long plaintextLen,
                int pageSize,
                byte segmentType,
                PayloadEncryptor encryptor,
                boolean initiallyAllDecrypted) {
            this.sourcePath = sourcePath;
            this.masterSegment = masterSegment;
            this.plaintextLen = plaintextLen;
            this.pageSize = pageSize;
            this.pageCount = (int) ((plaintextLen + pageSize - 1L) / pageSize);
            this.segmentType = segmentType;
            this.encryptor = encryptor;
            this.decryptedPages = new BitSet(this.pageCount);
            this.accessiblePages = new BitSet(this.pageCount);
            this.cleanPageHashLo = new long[this.pageCount];
            this.cleanPageHashHi = new long[this.pageCount];
            if (initiallyAllDecrypted) {
                this.decryptedPages.set(0, this.pageCount);
                this.accessiblePages.set(0, this.pageCount);
                for (int p = 0; p < this.pageCount; p++) {
                    long offset = (long) p * pageSize;
                    int len = (int) Math.min(pageSize, plaintextLen - offset);
                    this.cleanPageHashLo[p] = hashPageLo(masterSegment, offset, len);
                    this.cleanPageHashHi[p] = hashPageHi(masterSegment, offset, len);
                }
            }
        }

        /**
         * Creates a new encrypted segment file on disk from {@code masterSegment} and returns a
         * {@link LazyEncryptedSegment} with all pages marked clean against the initial on-disk image.
         */
        public static LazyEncryptedSegment create(
                Path targetPath,
                MemorySegment masterSegment,
                byte segmentType,
                PayloadEncryptor encryptor) throws IOException {
            writeEncryptedSegment(targetPath, masterSegment, segmentType, encryptor);
            return new LazyEncryptedSegment(
                    targetPath,
                    masterSegment,
                    masterSegment.byteSize(),
                    EncryptedSegmentHeader.DEFAULT_PAGE_SIZE,
                    segmentType,
                    encryptor,
                    true);
        }

        /**
         * Opens an existing encrypted segment file, authenticates its 128-byte header, allocates the
         * off-heap {@code masterSegment} in {@code arena}, and decrypts only page 0.
         */
        public static LazyEncryptedSegment open(
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
                    long expectedFileSize = expectedEncryptedFileSize(plaintextLen, pageSize);
                    if (fileSize < expectedFileSize) {
                        throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                                "Truncated encrypted segment in " + sourcePath
                                        + " (size=" + fileSize + ", expected=" + expectedFileSize + ")");
                    }

                    MemorySegment masterSegment = arena.allocate(Math.max(4096L, plaintextLen), 4096);
                    LazyEncryptedSegment lazy = new LazyEncryptedSegment(
                            sourcePath,
                            masterSegment,
                            plaintextLen,
                            pageSize,
                            expectedSegmentType,
                            encryptor,
                            false);
                    if (lazy.pageCount > 0) {
                        lazy.decryptPageRangeWithChannel(fc, sdk, 0, 0);
                    }
                    return lazy;
                }
            }
        }

        public MemorySegment masterSegment() {
            return masterSegment;
        }

        public int pageSize() {
            return pageSize;
        }

        public int pageCount() {
            return pageCount;
        }

        public synchronized int decryptedPageCount() {
            return decryptedPages.cardinality();
        }

        public int lastFlushDirtyPages() {
            return lastFlushDirtyPages;
        }

        /**
         * Marks the pages covering {@code [offset, offset + length)} as accessible to a caller slice
         * so any writes into previously unpopulated tail pages are checked during {@link #flush(Path)}.
         */
        public synchronized void markRangeAccessible(long offset, long length) {
            if (length <= 0 || pageCount == 0) {
                return;
            }
            long clampedStart = Math.max(0L, Math.min(offset, plaintextLen));
            long clampedEnd = Math.max(clampedStart, Math.min(offset + length, plaintextLen));
            if (clampedEnd <= clampedStart) {
                return;
            }
            int firstPage = (int) (clampedStart / pageSize);
            int lastPage = (int) ((clampedEnd - 1L) / pageSize);
            accessiblePages.set(firstPage, lastPage + 1);
        }

        /**
         * Ensures all encrypted pages intersecting {@code [offset, offset + length)} have been
         * decrypted and authenticated into {@code masterSegment}.
         */
        public synchronized void ensureRangeDecrypted(long offset, long length) {
            if (length <= 0 || pageCount == 0) {
                return;
            }
            long clampedStart = Math.max(0L, Math.min(offset, plaintextLen));
            long clampedEnd = Math.max(clampedStart, Math.min(offset + length, plaintextLen));
            if (clampedEnd <= clampedStart) {
                return;
            }
            int firstPage = (int) (clampedStart / pageSize);
            int lastPage = (int) ((clampedEnd - 1L) / pageSize);
            accessiblePages.set(firstPage, lastPage + 1);

            int firstMissing = -1;
            int lastMissing = -1;
            for (int p = firstPage; p <= lastPage; p++) {
                if (!decryptedPages.get(p)) {
                    if (firstMissing < 0) {
                        firstMissing = p;
                    }
                    lastMissing = p;
                }
            }
            if (firstMissing < 0) {
                return;
            }
            decryptMissingPages(firstMissing, lastMissing);
        }

        /**
         * Decrypts all remaining undecrypted pages in the bundle (used before full-segment operations
         * such as region relocation, compaction, or DEK rotation).
         */
        public synchronized void ensureAllPagesDecrypted() {
            if (pageCount == 0) {
                return;
            }
            accessiblePages.set(0, pageCount);
            int firstMissing = decryptedPages.nextClearBit(0);
            if (firstMissing >= pageCount) {
                return;
            }
            decryptMissingPages(firstMissing, pageCount - 1);
        }

        /**
         * Rebinds this segment to a new {@link PayloadEncryptor} (e.g. during DEK rotation).
         * Decrypts all remaining pages under the current key first and forces a full rewrite on next flush.
         */
        public synchronized void rebindEncryptor(PayloadEncryptor newEncryptor) {
            ensureAllPagesDecrypted();
            this.encryptor = newEncryptor;
            this.forceFullRewrite = true;
        }

        private void decryptMissingPages(int firstPage, int lastPage) {
            PayloadEncryptor enc = this.encryptor;
            if (enc == null || !enc.isEnabled()) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING,
                        "Encrypted segment requires an active DataEncryptor: " + sourcePath);
            }
            try (FileChannel fc = FileChannel.open(sourcePath, StandardOpenOption.READ);
                 DestroyableKeyMaterial sdk = resolveSubkey(enc, segmentType)) {
                decryptPageRangeWithChannel(fc, sdk, firstPage, lastPage);
            } catch (IOException e) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                        "Failed to read encrypted page from " + sourcePath);
            }
        }

        private void decryptPageRangeWithChannel(
                FileChannel fc,
                DestroyableKeyMaterial sdk,
                int firstPage,
                int lastPage) throws IOException {
            PayloadEncryptor enc = this.encryptor;
            long fileSize = fc.size();
            int fullPageDiskBytes = pageSize + PAGE_OVERHEAD_BYTES;
            byte[] rawKey = sdk.borrowRawBytes();
            SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
            try {
                Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                for (int p = firstPage; p <= lastPage; p++) {
                    if (decryptedPages.get(p)) {
                        continue;
                    }
                    long plainOffset = (long) p * pageSize;
                    int plainPageLen = (int) Math.min(pageSize, plaintextLen - plainOffset);
                    int diskPageLen = plainPageLen + PAGE_OVERHEAD_BYTES;
                    long filePos = EncryptedSegmentHeader.HEADER_BYTES + (long) p * fullPageDiskBytes;
                    if (filePos + diskPageLen > fileSize) {
                        throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                                "Truncated encrypted page " + p + " in " + sourcePath);
                    }

                    byte[] diskPage = new byte[diskPageLen];
                    readFully(fc, ByteBuffer.wrap(diskPage), filePos);

                    byte[] nonce = Arrays.copyOfRange(diskPage, 0, NONCE_BYTES);
                    byte[] aad = buildPageAad(
                            enc.tenantId(),
                            enc.namespaceId(),
                            segmentType,
                            (short) enc.dekVersion(),
                            p,
                            plainPageLen);

                    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                    cipher.updateAAD(aad);
                    byte[] plainPage = cipher.doFinal(diskPage, NONCE_BYTES, diskPageLen - NONCE_BYTES);
                    MemorySegment.copy(plainPage, 0, masterSegment, ValueLayout.JAVA_BYTE, plainOffset, plainPage.length);
                    Arrays.fill(plainPage, (byte) 0);

                    cleanPageHashLo[p] = hashPageLo(masterSegment, plainOffset, plainPageLen);
                    cleanPageHashHi[p] = hashPageHi(masterSegment, plainOffset, plainPageLen);
                    decryptedPages.set(p);
                    accessiblePages.set(p);
                }
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

        /**
         * Flushes dirty pages to {@code targetPath}. Returns the number of encrypted pages written.
         * If no pages are dirty and {@code !forceFullRewrite}, performs zero encryption and zero disk I/O.
         */
        public synchronized int flush(Path targetPath) throws IOException {
            PayloadEncryptor enc = this.encryptor;
            if (enc == null || !enc.isEnabled()) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED,
                        "Cannot write encrypted segment without an enabled PayloadEncryptor");
            }

            if (forceFullRewrite || !Files.exists(targetPath)) {
                ensureAllPagesDecrypted();
                writeEncryptedSegment(targetPath, masterSegment, segmentType, enc);
                for (int p = 0; p < pageCount; p++) {
                    long plainOffset = (long) p * pageSize;
                    int plainPageLen = (int) Math.min(pageSize, plaintextLen - plainOffset);
                    cleanPageHashLo[p] = hashPageLo(masterSegment, plainOffset, plainPageLen);
                    cleanPageHashHi[p] = hashPageHi(masterSegment, plainOffset, plainPageLen);
                }
                forceFullRewrite = false;
                lastFlushDirtyPages = pageCount;
                return pageCount;
            }

            BitSet dirty = new BitSet(pageCount);
            for (int p = 0; p < pageCount; p++) {
                long plainOffset = (long) p * pageSize;
                int plainPageLen = (int) Math.min(pageSize, plaintextLen - plainOffset);
                if (decryptedPages.get(p)) {
                    long lo = hashPageLo(masterSegment, plainOffset, plainPageLen);
                    long hi = hashPageHi(masterSegment, plainOffset, plainPageLen);
                    if (lo != cleanPageHashLo[p] || hi != cleanPageHashHi[p]) {
                        dirty.set(p);
                    }
                } else if (accessiblePages.get(p) && !isAllZero(masterSegment, plainOffset, plainPageLen)) {
                    dirty.set(p);
                }
            }

            int dirtyCount = dirty.cardinality();
            if (dirtyCount == 0) {
                lastFlushDirtyPages = 0;
                return 0;
            }

            short dekVersion = (short) enc.dekVersion();
            int fullPageDiskBytes = pageSize + PAGE_OVERHEAD_BYTES;
            try (DestroyableKeyMaterial sdk = resolveSubkey(enc, segmentType)) {
                byte[] rawKey = sdk.borrowRawBytes();
                SecretKeySpec keySpec = new SecretKeySpec(rawKey, "AES");
                try {
                    Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
                    try (FileChannel fc = FileChannel.open(targetPath,
                            StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                        byte[] plainPageBuf = new byte[pageSize];
                        for (int p = dirty.nextSetBit(0); p >= 0; p = dirty.nextSetBit(p + 1)) {
                            long plainOffset = (long) p * pageSize;
                            int plainPageLen = (int) Math.min(pageSize, plaintextLen - plainOffset);
                            MemorySegment.copy(masterSegment, ValueLayout.JAVA_BYTE, plainOffset,
                                    plainPageBuf, 0, plainPageLen);

                            byte[] nonce = new byte[NONCE_BYTES];
                            SECURE_RANDOM.nextBytes(nonce);
                            byte[] aad = buildPageAad(
                                    enc.tenantId(),
                                    enc.namespaceId(),
                                    segmentType,
                                    dekVersion,
                                    p,
                                    plainPageLen);

                            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
                            cipher.updateAAD(aad);
                            byte[] cipherAndTag = cipher.doFinal(plainPageBuf, 0, plainPageLen);

                            ByteBuffer pageOut = ByteBuffer.allocate(NONCE_BYTES + cipherAndTag.length);
                            pageOut.put(nonce);
                            pageOut.put(cipherAndTag);
                            pageOut.flip();

                            long filePos = EncryptedSegmentHeader.HEADER_BYTES + (long) p * fullPageDiskBytes;
                            writeFullyAt(fc, pageOut, filePos);

                            cleanPageHashLo[p] = hashPageLo(masterSegment, plainOffset, plainPageLen);
                            cleanPageHashHi[p] = hashPageHi(masterSegment, plainOffset, plainPageLen);
                            decryptedPages.set(p);
                            accessiblePages.set(p);
                        }
                        Arrays.fill(plainPageBuf, (byte) 0);
                        fc.force(false);
                    }
                    lastFlushDirtyPages = dirtyCount;
                    return dirtyCount;
                } catch (GeneralSecurityException e) {
                    throw new SpectorMemoryException(ErrorCode.MEMORY_ENCRYPTION_FAILED, e,
                            "Failed to encrypt dirty segment page for " + targetPath);
                } finally {
                    Arrays.fill(rawKey, (byte) 0);
                }
            }
        }
    }

    private static long expectedEncryptedFileSize(long plaintextLen, int pageSize) {
        if (plaintextLen <= 0) {
            return EncryptedSegmentHeader.HEADER_BYTES;
        }
        long pages = (plaintextLen + pageSize - 1L) / pageSize;
        return EncryptedSegmentHeader.HEADER_BYTES + plaintextLen + pages * PAGE_OVERHEAD_BYTES;
    }

    private static boolean isAllZero(MemorySegment seg, long offset, int len) {
        int i = 0;
        int limit8 = len & ~7;
        for (; i < limit8; i += 8) {
            if (seg.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + i) != 0L) {
                return false;
            }
        }
        for (; i < len; i++) {
            if (seg.get(ValueLayout.JAVA_BYTE, offset + i) != 0) {
                return false;
            }
        }
        return true;
    }

    private static long hashPageLo(MemorySegment seg, long offset, int len) {
        long h = 0x9E3779B97F4A7C15L ^ len;
        int i = 0;
        int limit8 = len & ~7;
        for (; i < limit8; i += 8) {
            long w = seg.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + i);
            h ^= w;
            h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL + 0xBF58476D1CE4E5B9L;
        }
        for (; i < len; i++) {
            h ^= (seg.get(ValueLayout.JAVA_BYTE, offset + i) & 0xFFL) << ((i & 7) * 8);
            h *= 0x94D049BB133111EBL;
        }
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    private static long hashPageHi(MemorySegment seg, long offset, int len) {
        long h = 0xC2B2AE3D27D4EB4FL ^ (len * 0x9E3779B97F4A7C15L);
        int i = 0;
        int limit8 = len & ~7;
        for (; i < limit8; i += 8) {
            long w = seg.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + i);
            h ^= Long.rotateLeft(w, 13);
            h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        }
        for (; i < len; i++) {
            h ^= (seg.get(ValueLayout.JAVA_BYTE, offset + i) & 0xFFL) << ((i & 7) * 8);
            h *= 0xBF58476D1CE4E5B9L;
        }
        h ^= h >>> 31;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 31;
        return h;
    }

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

    private static void writeFullyAt(FileChannel fc, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            pos += fc.write(buf, pos);
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
