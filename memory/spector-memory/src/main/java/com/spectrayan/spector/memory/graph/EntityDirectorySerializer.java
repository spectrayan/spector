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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name-index sidecar codec for {@link EntityDirectory}. The byte format is identical to the
 * {@code entity-names.idx} codec used by {@code EntityGraphSerializer} (a leading encryption-flag
 * byte followed by either an AES-256-GCM blob or the plaintext {@code count + (len + name + id)}
 * entries), so the P3 migration can reuse it. The directory writes its sidecar as
 * {@value EntityDirectory#NAME_INDEX_SIDECAR} to avoid colliding with the legacy graph's
 * {@code entity-names.idx} while both coexist during the graduation.
 */
final class EntityDirectorySerializer {

    private static final Logger log = LoggerFactory.getLogger(EntityDirectorySerializer.class);

    private EntityDirectorySerializer() {} // utility class

    /** Writes the directory's name index as a sidecar next to its {@code .edir} container. */
    static void saveNameIndexSidecar(EntityDirectory directory, Path filePath, DataEncryptor encryptor) {
        Path parent = filePath.getParent();
        if (parent == null) return;
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityDirectory", parent, e);
        }
        Path nameIndexPath = parent.resolve(EntityDirectory.NAME_INDEX_SIDECAR);
        boolean encrypt = encryptor != null && encryptor.isEnabled();
        try (FileChannel ch = FileChannel.open(nameIndexPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            writeNameIndex(ch, directory.nameIndexInternal(), encrypt, encryptor);
            ch.force(true);
            log.info("EntityDirectory name index saved: {} names, encrypted={} → {}",
                    directory.nameIndexInternal().size(), encrypt, nameIndexPath);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityDirectory", nameIndexPath, e);
        }
    }

    /**
     * Loads the {@value EntityDirectory#NAME_INDEX_SIDECAR} sidecar next to a directory container,
     * or an empty map when absent.
     */
    static ConcurrentHashMap<String, Integer> loadNameIndexSidecar(Path graphFile, DataEncryptor encryptor) {
        Path parent = graphFile.getParent();
        Path nameIndexPath = parent != null ? parent.resolve(EntityDirectory.NAME_INDEX_SIDECAR) : null;
        if (nameIndexPath == null || !Files.exists(nameIndexPath)) {
            return new ConcurrentHashMap<>();
        }
        try (FileChannel nameCh = FileChannel.open(nameIndexPath, StandardOpenOption.READ)) {
            ConcurrentHashMap<String, Integer> names = readNameIndex(nameCh, encryptor);
            log.info("EntityDirectory name index loaded: {} names from {}", names.size(), nameIndexPath);
            return names;
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityDirectory", nameIndexPath, e);
        }
    }

    // ── Name-index byte codec (mirror of EntityGraphSerializer) ──

    private static void writeNameIndex(FileChannel ch, ConcurrentHashMap<String, Integer> nameIndex,
                                       boolean encrypt, DataEncryptor encryptor) throws IOException {
        ByteArrayOutputStream nameStream = new ByteArrayOutputStream();
        ByteBuffer nameCountBuf = ByteBuffer.allocate(4);
        nameCountBuf.putInt(nameIndex.size());
        nameCountBuf.flip();
        nameStream.write(nameCountBuf.array());

        for (Map.Entry<String, Integer> entry : nameIndex.entrySet()) {
            byte[] nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            ByteBuffer entryBuf = ByteBuffer.allocate(4 + nameBytes.length + 4);
            entryBuf.putInt(nameBytes.length);
            entryBuf.put(nameBytes);
            entryBuf.putInt(entry.getValue());
            entryBuf.flip();
            nameStream.write(entryBuf.array());
        }

        byte[] nameIndexBytes = nameStream.toByteArray();

        ByteBuffer flagBuf = ByteBuffer.allocate(1);
        flagBuf.put(encrypt ? (byte) 0x01 : (byte) 0x00);
        flagBuf.flip();
        ch.write(flagBuf);

        if (encrypt) {
            byte[] encrypted = encryptor.encryptText(nameIndexBytes);
            ByteBuffer blobLenBuf = ByteBuffer.allocate(4);
            blobLenBuf.putInt(encrypted.length);
            blobLenBuf.flip();
            ch.write(blobLenBuf);
            ch.write(ByteBuffer.wrap(encrypted));
        } else {
            ch.write(ByteBuffer.wrap(nameIndexBytes));
        }
    }

    private static ConcurrentHashMap<String, Integer> readNameIndex(
            FileChannel ch, DataEncryptor encryptor) throws IOException {
        ByteBuffer flagBuf = ByteBuffer.allocate(1);
        ch.read(flagBuf);
        flagBuf.flip();
        byte flag = flagBuf.get();

        if (flag == 0x01) {
            ByteBuffer blobLenBuf = ByteBuffer.allocate(4);
            ch.read(blobLenBuf);
            blobLenBuf.flip();
            int blobLen = blobLenBuf.getInt();

            ByteBuffer blobBuf = ByteBuffer.allocate(blobLen);
            ch.read(blobBuf);
            blobBuf.flip();
            byte[] encrypted = new byte[blobLen];
            blobBuf.get(encrypted);

            if (encryptor == null || !encryptor.isEnabled()) {
                log.error("EntityDirectory name index is encrypted but no encryptor available — names will be empty");
                return new ConcurrentHashMap<>();
            }
            return parseNameIndexBytes(encryptor.decryptText(encrypted));
        } else if (flag == 0x00) {
            return readNameIndexFromChannel(ch);
        } else {
            // Legacy format (no flag byte) — seek back 1 byte.
            ch.position(ch.position() - 1);
            return readNameIndexFromChannel(ch);
        }
    }

    private static ConcurrentHashMap<String, Integer> readNameIndexFromChannel(FileChannel ch)
            throws IOException {
        ConcurrentHashMap<String, Integer> names = new ConcurrentHashMap<>();
        ByteBuffer countBuf = ByteBuffer.allocate(4);
        ch.read(countBuf);
        countBuf.flip();
        int nameCount = countBuf.getInt();

        for (int i = 0; i < nameCount; i++) {
            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            ch.read(lenBuf);
            lenBuf.flip();
            int len = lenBuf.getInt();

            ByteBuffer nameBuf = ByteBuffer.allocate(len);
            ch.read(nameBuf);
            nameBuf.flip();
            String name = new String(nameBuf.array(), 0, len, StandardCharsets.UTF_8);

            ByteBuffer idBuf = ByteBuffer.allocate(4);
            ch.read(idBuf);
            idBuf.flip();
            int id = idBuf.getInt();

            names.put(name, id);
        }
        return names;
    }

    private static ConcurrentHashMap<String, Integer> parseNameIndexBytes(byte[] data) {
        ConcurrentHashMap<String, Integer> names = new ConcurrentHashMap<>();
        ByteBuffer buf = ByteBuffer.wrap(data);
        int nameCount = buf.getInt();
        for (int i = 0; i < nameCount; i++) {
            int len = buf.getInt();
            byte[] nameBytes = new byte[len];
            buf.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            int id = buf.getInt();
            names.put(name, id);
        }
        return names;
    }
}
