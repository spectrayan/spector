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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.storage.PayloadEncryptor;
import com.spectrayan.spector.kernel.store.TextBlobMemory.TextEntry;
import com.spectrayan.spector.kernel.store.TextBlobMemory.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for issue #1190: {@link TextBlobMemory} must track ciphertext vs plaintext
 * out-of-band in record metadata rather than relying on an in-band {@code "\u0000ENC1:"} text prefix,
 * while keeping a backward-compatible read path for legacy prefix-marked records.
 */
@DisplayName("TextBlobMemory out-of-band ciphertext marker (#1190)")
class TextBlobCiphertextMarkerTest {

    private static final String LEGACY_PREFIX = "\u0000ENC1:";

    @TempDir
    Path tempDir;

    private static PayloadEncryptor aesGcmEncryptor(byte keyByte) {
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, keyByte);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        SecureRandom rng = new SecureRandom();
        return new PayloadEncryptor() {
            @Override
            public byte[] encrypt(byte[] plaintext) {
                try {
                    byte[] iv = new byte[12];
                    rng.nextBytes(iv);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
                    byte[] encrypted = cipher.doFinal(plaintext);
                    byte[] out = new byte[iv.length + encrypted.length];
                    System.arraycopy(iv, 0, out, 0, iv.length);
                    System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
                    return out;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public byte[] decrypt(byte[] ciphertext) {
                try {
                    if (ciphertext.length < 12 + 16) {
                        throw new IllegalArgumentException("Ciphertext too short");
                    }
                    byte[] iv = Arrays.copyOfRange(ciphertext, 0, 12);
                    byte[] body = Arrays.copyOfRange(ciphertext, 12, ciphertext.length);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
                    return cipher.doFinal(body);
                } catch (Exception e) {
                    throw new SpectorMemoryException(ErrorCode.MEMORY_AUTH_TAG_MISMATCH, e, "GCM auth failed");
                }
            }
        };
    }

    @Test
    @DisplayName("With encryption on, text starting with \\0ENC1: is encrypted on disk and recalls original text")
    void encryptedStoreAlwaysEncryptsPrefixedUserText() {
        Path file = tempDir.resolve("encrypted-prefixed.dat");
        PayloadEncryptor encryptor = aesGcmEncryptor((byte) 0x42);
        String trickyText = LEGACY_PREFIX + "sensitive-user-payload-that-starts-with-marker";

        TextPosition pos;
        try (TextBlobMemory store = new TextBlobMemory(file, encryptor)) {
            pos = store.write("mem-enc-1", MemoryType.SEMANTIC, trickyText);

            // Inspect raw bytes on disk: must be ciphertext, not plaintext
            byte[] onDisk = new byte[pos.textLength()];
            MemorySegment.copy(store.segment(), ValueLayout.JAVA_BYTE, pos.textOffset(), onDisk, 0, pos.textLength());
            String rawStored = new String(onDisk, StandardCharsets.UTF_8);
            assertThat(rawStored)
                    .as("stored payload on disk must be encrypted, never cleartext")
                    .doesNotContain("sensitive-user-payload")
                    .isNotEqualTo(trickyText);

            assertThat(store.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isEqualTo(trickyText);
            assertThat(store.readAll().get("mem-enc-1").text())
                    .isEqualTo(trickyText);
            store.flush();
        }

        // Reopen with same encryptor: point read before readAll() and full readAll() both return original text
        try (TextBlobMemory reopened = new TextBlobMemory(file, encryptor)) {
            assertThat(reopened.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isEqualTo(trickyText);
            Map<String, TextEntry> entries = reopened.readAll();
            assertThat(entries.get("mem-enc-1").text()).isEqualTo(trickyText);
        }

        // Reopen without encryptor: reading encrypted entry must fail with MEMORY_ENCRYPTION_KEY_MISSING
        try (TextBlobMemory unkeyed = new TextBlobMemory(file)) {
            assertThatThrownBy(() -> unkeyed.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isInstanceOf(SpectorMemoryException.class)
                    .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING));
        }
    }

    @Test
    @DisplayName("With encryption off, text starting with \\0ENC1: is stored and read back unchanged without key-missing error")
    void unencryptedStoreReadsPrefixedPlaintextWithoutFalseDecryption() {
        Path file = tempDir.resolve("unencrypted-prefixed.dat");
        String trickyText = LEGACY_PREFIX + "not-ciphertext-just-literal-user-text";

        TextPosition pos;
        try (TextBlobMemory store = new TextBlobMemory(file)) {
            pos = store.write("mem-plain-1", MemoryType.EPISODIC, trickyText);

            assertThat(store.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isEqualTo(trickyText);
            Map<String, TextEntry> entries = store.readAll();
            assertThat(entries).containsKey("mem-plain-1");
            assertThat(entries.get("mem-plain-1").text()).isEqualTo(trickyText);
            store.flush();
        }

        // Reopen without encryptor
        try (TextBlobMemory reopened = new TextBlobMemory(file)) {
            assertThat(reopened.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isEqualTo(trickyText);
            assertThat(reopened.readAll().get("mem-plain-1").text())
                    .isEqualTo(trickyText);
        }

        // Even if opened with an active encryptor, a record marked plaintext in metadata must not be decrypted
        PayloadEncryptor encryptor = aesGcmEncryptor((byte) 0x55);
        try (TextBlobMemory openedWithKey = new TextBlobMemory(file, encryptor)) {
            assertThat(openedWithKey.readTextDirect(pos.textOffset(), pos.textLength()))
                    .isEqualTo(trickyText);
            assertThat(openedWithKey.readAll().get("mem-plain-1").text())
                    .isEqualTo(trickyText);
        }
    }

    @Test
    @DisplayName("Existing legacy encrypted records written with \\0ENC1: prefix remain readable and rekeyable")
    void legacyPrefixedCiphertextRemainsReadableAndRekeyable() {
        Path file = tempDir.resolve("legacy-compat.dat");
        PayloadEncryptor oldKey = aesGcmEncryptor((byte) 0x11);
        PayloadEncryptor newKey = aesGcmEncryptor((byte) 0x22);

        String legacyPlain = "Legacy encrypted text stored with prefix";
        String v3PrefixedPlain = LEGACY_PREFIX + "New v3 encrypted text starting with prefix";
        String v3UnencryptedPrefixed = LEGACY_PREFIX + "Unencrypted v3 entry that must not be rekeyed";

        long legacyTextOffset;
        int legacyTextLen;
        TextPosition v3EncryptedPos;
        TextPosition v3PlainPos;

        try (TextBlobMemory store = new TextBlobMemory(file)) {
            // 1. Manually append a legacy v2 encrypted record (V2_ENTRY_FLAG = 0x40, V2_CRC_TRAILER_MAGIC, in-band prefix)
            byte[] idBytes = "legacy-enc".getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = oldKey.encryptText(legacyPlain.getBytes(StandardCharsets.UTF_8));
            String legacyStored = LEGACY_PREFIX + Base64.getEncoder().encodeToString(cipherBytes);
            byte[] textBytes = legacyStored.getBytes(StandardCharsets.UTF_8);
            legacyTextLen = textBytes.length;

            int bodySize = 1 + 4 + idBytes.length + 4 + textBytes.length;
            int entrySize = bodySize + TextBlobMemory.V2_CRC_TRAILER_BYTES;
            MemorySegment entrySeg = MemorySegment.ofArray(new byte[entrySize]);
            entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) (MemoryType.SEMANTIC.ordinal() | 0x40));
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 9 + idBytes.length, textBytes.length);
            int textCrc = AbstractRecordMemory.nonZeroCrc32c(entrySeg, 9 + idBytes.length, textBytes.length);
            int entryCrc = AbstractRecordMemory.nonZeroCrc32c(entrySeg, 0, bodySize);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, bodySize, TextBlobMemory.V2_CRC_TRAILER_MAGIC);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, bodySize + 4, textCrc);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, bodySize + 8, entryCrc);

            long payloadOffset = store.append(entrySeg);
            legacyTextOffset = store.dataOffset() + payloadOffset + 9 + idBytes.length;

            // 2. Write an unencrypted v3 entry whose plaintext starts with LEGACY_PREFIX
            v3PlainPos = store.write("v3-plain", MemoryType.EPISODIC, v3UnencryptedPrefixed);
            store.flush();
        }

        // 3. Reopen with oldKey, write a v3 encrypted entry whose plaintext starts with LEGACY_PREFIX, and verify all 3
        try (TextBlobMemory keyedStore = new TextBlobMemory(file, oldKey)) {
            v3EncryptedPos = keyedStore.write("v3-enc", MemoryType.SEMANTIC, v3PrefixedPlain);

            assertThat(keyedStore.readTextDirect(legacyTextOffset, legacyTextLen)).isEqualTo(legacyPlain);
            assertThat(keyedStore.readTextDirect(v3PlainPos.textOffset(), v3PlainPos.textLength()))
                    .isEqualTo(v3UnencryptedPrefixed);
            assertThat(keyedStore.readTextDirect(v3EncryptedPos.textOffset(), v3EncryptedPos.textLength()))
                    .isEqualTo(v3PrefixedPlain);

            Map<String, TextEntry> all = keyedStore.readAll();
            assertThat(all.get("legacy-enc").text()).isEqualTo(legacyPlain);
            assertThat(all.get("v3-plain").text()).isEqualTo(v3UnencryptedPrefixed);
            assertThat(all.get("v3-enc").text()).isEqualTo(v3PrefixedPlain);

            // 4. Rekey from oldKey to newKey: only legacy-enc and v3-enc should be rekeyed (2 entries)
            int rekeyed = keyedStore.rekeyEncryptedEntries(oldKey, newKey);
            assertThat(rekeyed).isEqualTo(2);

            assertThat(keyedStore.readTextDirect(legacyTextOffset, legacyTextLen)).isEqualTo(legacyPlain);
            assertThat(keyedStore.readTextDirect(v3PlainPos.textOffset(), v3PlainPos.textLength()))
                    .isEqualTo(v3UnencryptedPrefixed);
            assertThat(keyedStore.readTextDirect(v3EncryptedPos.textOffset(), v3EncryptedPos.textLength()))
                    .isEqualTo(v3PrefixedPlain);

            Map<String, TextEntry> afterRekey = keyedStore.readAll();
            assertThat(afterRekey.get("legacy-enc").text()).isEqualTo(legacyPlain);
            assertThat(afterRekey.get("v3-plain").text()).isEqualTo(v3UnencryptedPrefixed);
            assertThat(afterRekey.get("v3-enc").text()).isEqualTo(v3PrefixedPlain);
        }
    }
}
