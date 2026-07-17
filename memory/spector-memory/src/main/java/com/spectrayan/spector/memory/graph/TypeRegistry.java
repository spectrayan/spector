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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, open-schema string ↔ integer type registry.
 *
 * <h3>Design</h3>
 * <p>Provides Neo4j-style open labeling: any string is accepted as a type
 * and automatically assigned a stable integer ID on first use. IDs are
 * sequential and never reused. Well-known types (e.g., "PERSON", "MANAGES")
 * are pre-seeded with stable IDs for backward compatibility.</p>
 *
 * <h3>Off-Heap Integration</h3>
 * <p>The integer IDs are stored in the off-heap {@link EntityGraph} structures
 * (4 bytes per entity type, 4 bytes per relation type). This keeps the graph
 * layout compact while allowing unlimited type extensibility.</p>
 *
 * <h3>Persistence</h3>
 * <p>The registry is saved/loaded alongside graph data as a simple binary file:
 * {@code [count:4B][{nameLen:4B, nameUtf8:varB, id:4B}...]}</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link ConcurrentHashMap} for lookups and {@link AtomicInteger} for
 * ID generation. Safe for concurrent ingestion from multiple virtual threads.</p>
 *
 * @see EntityGraph
 */
public final class TypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(TypeRegistry.class);

    /** File magic: "TREG" in ASCII. */
    private static final int FILE_MAGIC = 0x54524547;
    private static final int FILE_VERSION = 1;

    private final ConcurrentHashMap<String, Integer> nameToId;
    private final ConcurrentHashMap<Integer, String> idToName;
    private final AtomicInteger nextId;
    private final String label;  // "entity-type" or "relation-type", for logging

    /**
     * Creates a new empty registry.
     *
     * @param label descriptive label for logging (e.g., "entity-type", "relation-type")
     */
    public TypeRegistry(String label) {
        this.label = label;
        this.nameToId = new ConcurrentHashMap<>();
        this.idToName = new ConcurrentHashMap<>();
        this.nextId = new AtomicInteger(0);
    }

    /**
     * Creates a registry pre-seeded with the given well-known types.
     *
     * <p>Each type gets a stable ID based on its array index. This ensures
     * backward compatibility with existing persisted graphs that used
     * enum ordinals.</p>
     *
     * @param label     descriptive label
     * @param seedTypes well-known types to pre-register (index = ID)
     * @return a seeded registry
     */
    public static TypeRegistry seeded(String label, String... seedTypes) {
        TypeRegistry registry = new TypeRegistry(label);
        for (String type : seedTypes) {
            registry.register(type);
        }
        return registry;
    }

    /**
     * Returns the ID for the given type name, registering it if not yet known.
     *
     * <p>Names are case-insensitive and normalized to uppercase.</p>
     *
     * @param name type name (e.g., "PERSON", "SOFTWARE", "VEHICLE")
     * @return the stable integer ID for this type
     */
    public int getOrRegister(String name) {
        if (name == null || name.isBlank()) {
            return getOrRegister("OTHER");
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        Integer existing = nameToId.get(normalized);
        if (existing != null) return existing;

        // Double-check with putIfAbsent for thread safety
        int newId = nextId.getAndIncrement();
        Integer raced = nameToId.putIfAbsent(normalized, newId);
        if (raced != null) {
            // Another thread registered it first, revert our ID
            // (IDs may have gaps, which is fine — we never reuse them)
            return raced;
        }
        idToName.put(newId, normalized);
        log.debug("{} registry: registered '{}' → {}", label, normalized, newId);
        return newId;
    }

    /**
     * Returns the type name for a given ID, or "UNKNOWN" if not found.
     *
     * @param id the type ID
     * @return the type name string
     */
    public String nameOf(int id) {
        String name = idToName.get(id);
        return name != null ? name : "UNKNOWN";
    }

    /**
     * Returns the ID for a given type name, or -1 if not registered.
     *
     * @param name the type name
     * @return the ID, or -1
     */
    public int idOf(String name) {
        if (name == null || name.isBlank()) return -1;
        Integer id = nameToId.get(name.trim().toUpperCase(Locale.ROOT));
        return id != null ? id : -1;
    }

    /**
     * Returns the current number of registered types.
     */
    public int size() {
        return nameToId.size();
    }

    // ── Persistence ──────────────────────────────────────────────

    /**
     * Saves the registry to a binary file.
     *
     * <p>Format: {@code [magic:4B][version:4B][count:4B][{nameLen:4B, nameUtf8:varB, id:4B}...]}</p>
     *
     * @param filePath path to write
     * @throws IOException on I/O error
     */
    public void save(Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // Header
            ByteBuffer header = ByteBuffer.allocate(12);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(nameToId.size());
            header.flip();
            ch.write(header);

            // Entries
            for (var entry : nameToId.entrySet()) {
                byte[] nameBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                ByteBuffer entryBuf = ByteBuffer.allocate(4 + nameBytes.length + 4);
                entryBuf.putInt(nameBytes.length);
                entryBuf.put(nameBytes);
                entryBuf.putInt(entry.getValue());
                entryBuf.flip();
                ch.write(entryBuf);
            }
        }

        log.info("{} registry saved: {} types → {}", label, nameToId.size(), filePath);
    }

    /**
     * Loads a registry from a binary file, or returns a seeded registry if the file
     * doesn't exist.
     *
     * @param filePath  path to read
     * @param label     descriptive label
     * @param seedTypes fallback well-known types if file doesn't exist
     * @return the loaded or seeded registry
     */
    public static TypeRegistry load(Path filePath, String label, String... seedTypes) {
        if (!Files.exists(filePath)) {
            log.info("{} registry file not found, creating seeded registry with {} types",
                    label, seedTypes.length);
            return seeded(label, seedTypes);
        }

        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(12);
            ch.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int count = header.getInt();

            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                log.warn("{} registry file has invalid magic/version, creating fresh", label);
                return seeded(label, seedTypes);
            }

            TypeRegistry registry = new TypeRegistry(label);
            int maxId = -1;

            for (int i = 0; i < count; i++) {
                ByteBuffer lenBuf = ByteBuffer.allocate(4);
                ch.read(lenBuf);
                lenBuf.flip();
                int nameLen = lenBuf.getInt();

                ByteBuffer nameBuf = ByteBuffer.allocate(nameLen);
                ch.read(nameBuf);
                nameBuf.flip();
                String name = StandardCharsets.UTF_8.decode(nameBuf).toString();

                ByteBuffer idBuf = ByteBuffer.allocate(4);
                ch.read(idBuf);
                idBuf.flip();
                int id = idBuf.getInt();

                registry.nameToId.put(name, id);
                registry.idToName.put(id, name);
                maxId = Math.max(maxId, id);
            }

            registry.nextId.set(maxId + 1);

            // Ensure all seed types are present (for newly added well-known types)
            for (String seed : seedTypes) {
                String normalized = seed.trim().toUpperCase(Locale.ROOT);
                if (!registry.nameToId.containsKey(normalized)) {
                    registry.getOrRegister(normalized);
                }
            }

            log.info("{} registry loaded: {} types from {}", label, registry.size(), filePath);
            return registry;

        } catch (IOException e) {
            log.error("Failed to load {} registry, creating fresh: {}", label, e.getMessage());
            return seeded(label, seedTypes);
        }
    }

    // ── Internal ─────────────────────────────────────────────────

    /**
     * Registers a type and returns its ID. Used internally for seeding.
     */
    private int register(String name) {
        return getOrRegister(name);
    }

    @Override
    public String toString() {
        return "TypeRegistry[" + label + ", size=" + nameToId.size() + "]";
    }
}
