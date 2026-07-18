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
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binary serializer for {@link EntityGraph} — handles save/load with optional
 * AES-256-GCM encryption of the name index.
 *
 * <h3>File Format (EGPH v1)</h3>
 * <pre>
 *   Header (24 bytes):
 *     [magic:4B "EGPH"][version:4B][entityCap:4B][edgeCap:4B]
 *     [entityCount:4B][edgeCount:4B]
 *
 *   Entity Segment:  entityCap × 64 bytes
 *   Edge Segment:    edgeCap × 12 bytes
 *   Adjacency Header: [adjCap:4B][adjHwm:4B]
 *   Adjacency Data:  adjHwm × 8 bytes
 *   Name Index Flag: [0x00=plain | 0x01=encrypted]
 *   Name Index:      count + (len + name + id) entries
 * </pre>
 *
 * <p>Extracted from {@link EntityGraph} to separate persistence concerns
 * from graph data structure and traversal logic.</p>
 */
final class EntityGraphSerializer {

    private static final Logger log = LoggerFactory.getLogger(EntityGraphSerializer.class);

    /** File magic: "EGPH" in ASCII. */
    private static final int FILE_MAGIC = 0x45475048;
    /** File format version. */
    private static final int FILE_VERSION = 1;
    private static final int FILE_HEADER_BYTES = 24;

    private EntityGraphSerializer() {} // utility class

    // ══════════════════════════════════════════════════════════════
    // SAVE
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the graph to a binary file with optional name index encryption.
     *
     * <p>When a non-null, enabled {@link DataEncryptor} is provided, the name
     * index section is encrypted as a single AES-256-GCM blob. A 1-byte flag
     * precedes the name index data: {@code 0x00} = plaintext, {@code 0x01} = encrypted.
     * This ensures backward compatibility — files saved without encryption can
     * still be loaded by newer code that expects the flag.</p>
     *
     * @param graph     the graph to save
     * @param filePath  path to write
     * @param encryptor optional encryptor for name index (null = no encryption)
     */
    static void save(EntityGraph graph, Path filePath, DataEncryptor encryptor) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityGraph", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Header: magic + version + entityCap + edgeCap + entityCount + edgeCount
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(graph.entityCapacity());
            header.putInt(graph.edgeCapacity());
            header.putInt(graph.entityCount());
            header.putInt(graph.edgeCount());
            header.flip();
            ch.write(header);

            // Write entity segment
            writeSegment(ch, graph.entitySegment(),
                    (long) EntityGraph.ENTITY_NODE_BYTES * graph.entityCapacity());

            // Write edge segment
            writeSegment(ch, graph.edgeSegment(),
                    (long) EntityGraph.EDGE_BYTES * graph.edgeCapacity());

            // Write adjacency segment header: [adjSegmentCapacity:4B][adjHighWaterMark:4B]
            ByteBuffer adjHeader = ByteBuffer.allocate(8);
            adjHeader.putInt(graph.adjSegmentCapacity());
            adjHeader.putInt(graph.adjHighWaterMark());
            adjHeader.flip();
            ch.write(adjHeader);

            // Write adjacency segment data (only up to high water mark)
            if (graph.adjHighWaterMark() > 0) {
                writeSegment(ch, graph.adjacencySegment(),
                        (long) EntityGraph.ADJ_ENTRY_BYTES * graph.adjHighWaterMark());
            }

            // Write name index (on-heap → serialized, optionally encrypted)
            boolean encrypt = encryptor != null && encryptor.isEnabled();
            writeNameIndex(ch, graph.nameIndexInternal(), encrypt, encryptor);

            ch.force(true);
            log.info("EntityGraph saved: entities={}, edges={}, adjEntries={}, nameIndexEncrypted={} → {}",
                    graph.entityCount(), graph.edgeCount(), graph.adjHighWaterMark(), encrypt, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
        }

        // Persist TypeRegistries alongside the graph file
        if (parent != null) {
            saveRegistries(graph, parent);
        }
    }

    /**
     * Saves only the nameIndex and TypeRegistries as sidecar files.
     * Used by mmap-backed EntityGraph where segments are already on disk.
     */
    static void saveNameIndexAndRegistries(EntityGraph graph, Path filePath, DataEncryptor encryptor) {
        Path parent = filePath.getParent();
        if (parent == null) return;

        // Write nameIndex as sidecar file: entity-names.idx
        Path nameIndexPath = parent.resolve("entity-names.idx");
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", parent, e);
        }

        boolean encrypt = encryptor != null && encryptor.isEnabled();
        try (FileChannel ch = FileChannel.open(nameIndexPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            writeNameIndex(ch, graph.nameIndexInternal(), encrypt, encryptor);
            ch.force(true);
            log.info("EntityGraph name index saved: {} names, encrypted={} → {}",
                    graph.nameIndexInternal().size(), encrypt, nameIndexPath);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", nameIndexPath, e);
        }

        saveRegistries(graph, parent);
    }

    // ══════════════════════════════════════════════════════════════
    // LOAD
    // ══════════════════════════════════════════════════════════════

    /**
     * Loads a graph from a binary file, or returns a new empty graph.
     *
     * @param filePath          path to the graph file
     * @param defaultEntityCap  entity capacity if file doesn't exist
     * @param defaultEdgeCap    edge capacity if file doesn't exist
     * @param encryptor         optional encryptor for name index decryption (null = no encryption)
     * @return an EntityGraph (loaded or new)
     */
    static EntityGraph load(Path filePath, int defaultEntityCap, int defaultEdgeCap,
                            DataEncryptor encryptor) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("EntityGraph file not found, creating fresh: {}", filePath);
            return new EntityGraph(defaultEntityCap, defaultEdgeCap);
        }

        // Peek at magic to determine format: EGMM (mmap) vs EGPH (serialized)
        try (FileChannel peekCh = FileChannel.open(filePath, StandardOpenOption.READ)) {
            if (peekCh.size() < 4) {
                log.warn("EntityGraph file too small ({}B), creating fresh", peekCh.size());
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }
            ByteBuffer magicBuf = ByteBuffer.allocate(4);
            peekCh.read(magicBuf);
            magicBuf.flip();
            int magic = magicBuf.getInt();

            if (magic == 0x45474D4D) { // EGMM — mmap format
                peekCh.close();
                EntityGraph graph = new EntityGraph(filePath, defaultEntityCap, defaultEdgeCap);
                // Load nameIndex from sidecar file
                Path nameIndexPath = filePath.getParent() != null
                        ? filePath.getParent().resolve("entity-names.idx") : null;
                if (nameIndexPath != null && Files.exists(nameIndexPath)) {
                    try (FileChannel nameCh = FileChannel.open(nameIndexPath, StandardOpenOption.READ)) {
                        ConcurrentHashMap<String, Integer> names = readNameIndex(nameCh, encryptor);
                        graph.nameIndexInternal().putAll(names);
                        log.info("EntityGraph name index loaded: {} names from {}",
                                names.size(), nameIndexPath);
                    }
                }
                graph.setDataEncryptor(encryptor);
                return graph;
            }
        } catch (Exception e) {
            log.error("Failed to peek EntityGraph file: {}", e.getMessage());
        }

        // Fall through to EGPH (legacy serialized) format
        return loadLegacy(filePath, defaultEntityCap, defaultEdgeCap, encryptor);
    }

    /**
     * Loads a graph from the legacy EGPH binary format (heap-allocated segments).
     */
    private static EntityGraph loadLegacy(Path filePath, int defaultEntityCap, int defaultEdgeCap,
                                          DataEncryptor encryptor) {

        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < FILE_HEADER_BYTES) {
                log.warn("EntityGraph file too small ({}B), creating fresh", fileSize);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            ch.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int entityCap = header.getInt();
            int edgeCap = header.getInt();
            int entCount = header.getInt();
            int edgCount = header.getInt();

            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                log.warn("Incompatible EntityGraph file (magic={}, version={}), creating fresh",
                        Integer.toHexString(magic), version);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            // Validate file has enough data for the segments declared in the header
            long minExpectedBytes = FILE_HEADER_BYTES
                    + (long) EntityGraph.ENTITY_NODE_BYTES * entityCap
                    + (long) EntityGraph.EDGE_BYTES * edgeCap
                    + 8; // adjacency header (adjCap + adjHwm)
            if (fileSize < minExpectedBytes) {
                log.warn("EntityGraph file truncated ({}B < expected {}B), creating fresh",
                        fileSize, minExpectedBytes);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            Arena arena = Arena.ofShared();

            // Read entity segment
            long entityBytes = (long) EntityGraph.ENTITY_NODE_BYTES * entityCap;
            MemorySegment entSeg = arena.allocate(entityBytes);
            readSegment(ch, entSeg, entityBytes);

            // Read edge segment
            long edgeBytes = (long) EntityGraph.EDGE_BYTES * edgeCap;
            MemorySegment edgSeg = arena.allocate(edgeBytes);
            readSegment(ch, edgSeg, edgeBytes);

            // Read adjacency header
            ByteBuffer adjHeaderBuf = ByteBuffer.allocate(8);
            ch.read(adjHeaderBuf);
            adjHeaderBuf.flip();
            int adjCap = adjHeaderBuf.getInt();
            int adjHwm = adjHeaderBuf.getInt();

            // Read adjacency segment
            MemorySegment adjSeg = arena.allocate((long) EntityGraph.ADJ_ENTRY_BYTES * adjCap);
            adjSeg.fill((byte) 0);
            if (adjHwm > 0) {
                readSegment(ch, adjSeg, (long) EntityGraph.ADJ_ENTRY_BYTES * adjHwm);
            }

            // Read name index (with encryption flag detection)
            ConcurrentHashMap<String, Integer> names = readNameIndex(ch, encryptor);

            // Load TypeRegistries — use persisted files if available, else seed from defaults
            Path graphParent = filePath.getParent();
            TypeRegistry entityTypes = TypeRegistry.load(
                    StorageLayout.entityTypes(graphParent),
                    "entity-type", EntityType.SEED);
            TypeRegistry relationTypes = TypeRegistry.load(
                    StorageLayout.relationTypes(graphParent),
                    "relation-type", RelationType.SEED);

            EntityGraph graph = EntityGraph.fromLoaded(entityCap, edgeCap, entCount, edgCount,
                    arena, entSeg, edgSeg, adjSeg, adjCap, adjHwm, names,
                    entityTypes, relationTypes);
            graph.setDataEncryptor(encryptor);
            log.info("EntityGraph loaded (legacy EGPH): entities={}, edges={}, adjEntries={}, encryptor={} from {}",
                    entCount, edgCount, adjHwm,
                    encryptor != null && encryptor.isEnabled() ? "enabled" : "none", filePath);
            return graph;

        } catch (Exception e) {
            log.error("Failed to load EntityGraph, creating fresh: {}", e.getMessage());
            return new EntityGraph(defaultEntityCap, defaultEdgeCap);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // NAME INDEX SERIALIZATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Writes the name index to the file channel, optionally encrypted.
     */
    private static void writeNameIndex(FileChannel ch, ConcurrentHashMap<String, Integer> nameIndex,
                                       boolean encrypt, DataEncryptor encryptor) throws IOException {
        // Serialize name index to a byte array first
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

        // Write encryption flag: 0x00 = plaintext, 0x01 = encrypted
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
            log.info("EntityGraph name index encrypted: {} names, {} plaintext bytes → {} encrypted bytes",
                    nameIndex.size(), nameIndexBytes.length, encrypted.length);
        } else {
            ch.write(ByteBuffer.wrap(nameIndexBytes));
        }
    }

    /**
     * Reads the name index from a file channel, handling both encrypted and plaintext formats.
     *
     * <p>Detects the encryption flag byte: {@code 0x01} = encrypted (read blob, decrypt, parse),
     * {@code 0x00} = plaintext (parse inline). For backward compatibility with files saved before
     * the encryption flag was added, if the first byte looks like a valid name count (not 0x00 or 0x01),
     * it falls back to legacy parsing.</p>
     */
    private static ConcurrentHashMap<String, Integer> readNameIndex(
            FileChannel ch, DataEncryptor encryptor) throws IOException {
        ByteBuffer flagBuf = ByteBuffer.allocate(1);
        ch.read(flagBuf);
        flagBuf.flip();
        byte flag = flagBuf.get();

        if (flag == 0x01) {
            // Encrypted name index — read blob length + blob, decrypt, parse
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
                log.error("EntityGraph name index is encrypted but no encryptor available — names will be empty");
                return new ConcurrentHashMap<>();
            }

            byte[] decrypted = encryptor.decryptText(encrypted);
            return parseNameIndexBytes(decrypted);

        } else if (flag == 0x00) {
            return readNameIndexFromChannel(ch);

        } else {
            // Legacy format (no flag byte) — seek back 1 byte
            ch.position(ch.position() - 1);
            return readNameIndexFromChannel(ch);
        }
    }

    /** Reads a plaintext name index from the current file channel position. */
    private static ConcurrentHashMap<String, Integer> readNameIndexFromChannel(
            FileChannel ch) throws IOException {
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

    /** Parses a name index from a decrypted byte array. */
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

    private static void saveRegistries(EntityGraph graph, Path parent) {
        try {
            graph.entityTypeRegistry().save(StorageLayout.entityTypes(parent));
            graph.relationTypeRegistry().save(StorageLayout.relationTypes(parent));
        } catch (IOException e) {
            log.error("Failed to save TypeRegistries alongside EntityGraph: {}", e.getMessage());
        }
    }

    private static void writeSegment(FileChannel ch, MemorySegment seg, long totalBytes)
            throws IOException {
        long written = 0;
        int chunkSize = 64 * 1024;
        while (written < totalBytes) {
            int toWrite = (int) Math.min(chunkSize, totalBytes - written);
            ByteBuffer buf = seg.asSlice(written, toWrite).asByteBuffer().asReadOnlyBuffer();
            ch.write(buf);
            written += toWrite;
        }
    }

    private static void readSegment(FileChannel ch, MemorySegment seg, long totalBytes)
            throws IOException {
        long read = 0;
        int chunkSize = 64 * 1024;
        while (read < totalBytes) {
            int toRead = (int) Math.min(chunkSize, totalBytes - read);
            ByteBuffer buf = ByteBuffer.allocate(toRead);
            ch.read(buf);
            buf.flip();
            MemorySegment.copy(MemorySegment.ofBuffer(buf), 0, seg, read, toRead);
            read += toRead;
        }
    }
}
