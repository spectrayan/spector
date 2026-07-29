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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.shape.RegistryMemory;
import com.spectrayan.spector.memory.kernel.shape.RegistryLayout;
import com.spectrayan.spector.memory.kernel.shape.DefaultRegistryMemory;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;

/**
 * Thread-safe, open-schema string ↔ integer type registry, backed by the Memory Kernel shape RegistryMemory.
 */
public final class TypeRegistryMemory implements RegistryMemory {

    private static final Logger log = LoggerFactory.getLogger(TypeRegistryMemory.class);

    /** Legacy File magic: "TREG" in ASCII. */
    private static final int LEGACY_FILE_MAGIC = 0x54524547;
    private static final int LEGACY_FILE_VERSION = 1;

    private final String label;
    private volatile DefaultRegistryMemory backing;

    /**
     * Creates a new empty registry (volatile).
     *
     * @param label descriptive label for logging (e.g., "entity-type", "relation-type")
     */
    public TypeRegistryMemory(String label) {
        this.label = label;
        MemoryId registryId = MemoryId.of("graph", label);
        RegistryLayout layout = new RegistryLayout();
        // Create volatile DefaultRegistryMemory
        this.backing = new DefaultRegistryMemory(registryId, layout, 1024, 256 * 1024);
    }

    /**
     * Creates a registry pre-seeded with the given well-known types.
     */
    public static TypeRegistryMemory seeded(String label, String... seedTypes) {
        TypeRegistryMemory registry = new TypeRegistryMemory(label);
        for (String type : seedTypes) {
            registry.intern(type);
        }
        return registry;
    }

    /**
     * Returns the ID for the given type name, registering it if not yet known.
     * Alias for intern(name) to preserve legacy codebase API.
     */
    public int getOrRegister(String name) {
        return intern(name);
    }

    // ── RegistryMemory Delegation ──

    @Override
    public int intern(String name) {
        return backing.intern(name);
    }

    @Override
    public void putDirect(String name, int id) {
        backing.putDirect(name, id);
    }

    @Override
    public String nameOf(int id) {
        String name = backing.nameOf(id);
        return name != null ? name : "UNKNOWN";
    }

    @Override
    public int idOf(String name) {
        return backing.idOf(name);
    }

    @Override
    public Map<String, Integer> entries() {
        return backing.entries();
    }

    @Override
    public MemoryId id() {
        return backing.id();
    }

    @Override
    public RegistryLayout layout() {
        return backing.layout();
    }

    @Override
    public Arena arena() {
        return backing.arena();
    }

    @Override
    public MemorySegment segment() {
        return backing.segment();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public int capacity() {
        return backing.capacity();
    }

    @Override
    public int schemaVersion() {
        return backing.schemaVersion();
    }



    @Override
    public MemoryShape shape() {
        return backing.shape();
    }

    @Override
    public void flush() {
        backing.flush();
    }

    @Override
    public void close() {
        backing.close();
    }

    // ── Persistence: save / load with transparent legacy support ──

    public void save(Path filePath) throws IOException {
        Files.deleteIfExists(filePath);
        Files.createDirectories(filePath.getParent());

        MemoryId registryId = MemoryId.of("graph", label);
        RegistryLayout layout = new RegistryLayout();

        // Calculate total size required for the new persistent registry memory segment
        long totalDataBytes = 0;
        Map<String, Integer> currentEntries = entries();
        for (String name : currentEntries.keySet()) {
            totalDataBytes += 2 + name.getBytes(StandardCharsets.UTF_8).length + 4;
        }

        try (DefaultRegistryMemory fileBacking = new DefaultRegistryMemory(
                registryId, layout, currentEntries.size(), MemoryHeader.HEADER_BYTES + totalDataBytes, filePath)) {
            
            // Re-intern all entries in order of their IDs
            currentEntries.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> fileBacking.putDirect(entry.getKey(), entry.getValue()));
            
            fileBacking.flush();
        }

        log.info("{} registry saved (SMKM V1): {} types → {}", label, currentEntries.size(), filePath);
    }

    public static TypeRegistryMemory load(Path filePath, String label, String... seedTypes) {
        if (!Files.exists(filePath)) {
            log.info("{} registry file not found, creating seeded registry with {} types",
                    label, seedTypes.length);
            return seeded(label, seedTypes);
        }

        try {
            int magic;
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                ByteBuffer mb = ByteBuffer.allocate(4);
                ch.read(mb);
                mb.flip();
                magic = mb.getInt();
            }

            boolean isStandard = (magic == MemoryHeader.MAGIC || magic == 0x4D4B4D53);
            boolean isLegacy = (magic == LEGACY_FILE_MAGIC || magic == 0x47455254);

            TypeRegistryMemory registry = new TypeRegistryMemory(label);

            if (isStandard) {
                MemoryId registryId = MemoryId.of("graph", label);
                RegistryLayout layout = new RegistryLayout();
                registry.backing.close();
                registry.backing = new DefaultRegistryMemory(registryId, layout, 0, 0, filePath);

                // Ensure all seed types are present (e.g. if new seed types were added)
                for (String seed : seedTypes) {
                    registry.intern(seed);
                }
                log.info("{} registry loaded (SMKM V1): {} types from {}", label, registry.size(), filePath.getFileName());
                return registry;
            } else if (isLegacy) {
                // Read legacy file format
                try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                    ByteBuffer header = ByteBuffer.allocate(12);
                    ch.read(header);
                    header.flip();
                    header.getInt(); // magic
                    header.getInt(); // version
                    int count = header.getInt();

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

                        registry.backing.putDirect(name, id);
                    }
                }

                // Ensure all seed types are present
                for (String seed : seedTypes) {
                    registry.intern(seed);
                }
                log.info("{} registry loaded (legacy V1): {} types from {}", label, registry.size(), filePath.getFileName());
                return registry;
            } else {
                log.warn("{} registry file has invalid magic: 0x{}, creating fresh", label, Integer.toHexString(magic));
                return seeded(label, seedTypes);
            }
        } catch (Exception e) {
            log.error("Failed to load {} registry, creating fresh: {}", label, e.getMessage());
            return seeded(label, seedTypes);
        }
    }

    @Override
    public String toString() {
        return "TypeRegistry[" + label + ", size=" + size() + "]";
    }
}
