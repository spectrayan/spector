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
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.shape.RegistryMemory;
import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;
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
    public TypeRegistryMemory(SystemMemoryId systemMemoryId) {
        this.label = systemMemoryId.id().memoryName();
        RegistryLayout layout = new RegistryLayout();
        this.backing = new DefaultRegistryMemory(systemMemoryId.id(), layout, 1024, 256 * 1024);
    }

    @Deprecated
    public TypeRegistryMemory(String label) {
        this.label = label;
        MemoryId registryId = "entity-type".equals(label)
                ? SystemMemoryId.ENTITY_TYPE.id()
                : "relation-type".equals(label)
                        ? SystemMemoryId.RELATION_TYPE.id()
                        : MemoryId.of("graph", label);
        RegistryLayout layout = new RegistryLayout();
        this.backing = new DefaultRegistryMemory(registryId, layout, 1024, 256 * 1024);
    }

    public static TypeRegistryMemory seeded(SystemMemoryId systemMemoryId, String... seedTypes) {
        TypeRegistryMemory registry = new TypeRegistryMemory(systemMemoryId);
        for (String type : seedTypes) {
            registry.intern(type);
        }
        return registry;
    }

    @Deprecated
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

    private transient MemorySegment bundleSlice;
    private transient boolean bundleManaged = false;

    public static TypeRegistryMemory fromBundle(SystemMemoryId systemMemoryId, Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew, String... seedTypes) {
        TypeRegistryMemory reg = new TypeRegistryMemory(systemMemoryId);
        reg.bundleSlice = regionSlice;
        reg.bundleManaged = true;

        reg.backing.close();
        RegistryLayout layout = new RegistryLayout();
        reg.backing = new DefaultRegistryMemory(systemMemoryId.id(), layout, 1024, arena, regionSlice,
                isNew ? 0 : (int) MemoryHeader.readCount(regionSlice, 0L),
                true, bundlePath, null, true); // bundleManaged=true

        if (isNew) {
            long now = System.currentTimeMillis();
            MemoryHeader.write(regionSlice, 0L, layout.schemaVersion(), MemoryShape.REGISTRY, 0,
                    (int) regionSlice.byteSize(), 0, 0, layout.layoutId(), now, now);
        }

        for (String seed : seedTypes) {
            reg.intern(seed);
        }

        // Migrate legacy standalone TypeRegistry if it exists
        if (isNew && bundlePath != null) {
            Path legacyPath = "entity-type".equals(reg.label)
                    ? bundlePath.resolveSibling("entity-types.dat")
                    : bundlePath.resolveSibling("relation-types.dat");
            if (Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone {} registry to bundle region...", reg.label);
                try {
                    TypeRegistryMemory legacy = TypeRegistryMemory.load(legacyPath, systemMemoryId, seedTypes);
                    Map<String, Integer> currentEntries = legacy.entries();
                    currentEntries.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue())
                            .forEach(entry -> reg.backing.putDirect(entry.getKey(), entry.getValue()));
                    reg.backing.flush();
                    legacy.backing.close();
                    Files.deleteIfExists(legacyPath);
                } catch (Exception e) {
                    log.warn("Failed to migrate legacy {} registry: {}", reg.label, e.getMessage());
                }
            }
        }

        log.info("{} registry initialized (bundle): {} types", reg.label, reg.size());
        return reg;
    }

    public void save(Path filePath) throws IOException {
        if (bundleManaged) {
            long now = System.currentTimeMillis();
            MemoryHeader.write(bundleSlice, 0L, backing.layout().schemaVersion(), MemoryShape.REGISTRY, backing.size(),
                    (int) bundleSlice.byteSize(), 0, 0, backing.layout().layoutId(), now, now);
            backing.flush();
            log.info("{} registry saved to bundle: {} types", label, backing.size());
            return;
        }

        Files.deleteIfExists(filePath);
        Files.createDirectories(filePath.getParent());

        MemoryId registryId = backing.id();
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

    public static TypeRegistryMemory load(Path filePath, SystemMemoryId systemMemoryId, String... seedTypes) {
        String label = systemMemoryId.id().memoryName();
        if (!Files.exists(filePath)) {
            log.info("{} registry file not found, creating seeded registry with {} types",
                    label, seedTypes.length);
            return seeded(systemMemoryId, seedTypes);
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

            TypeRegistryMemory registry = new TypeRegistryMemory(systemMemoryId);

            if (isStandard) {
                RegistryLayout layout = new RegistryLayout();
                registry.backing.close();
                registry.backing = new DefaultRegistryMemory(systemMemoryId.id(), layout, 0, 0, filePath);

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
                return seeded(systemMemoryId, seedTypes);
            }
        } catch (Exception e) {
            log.error("Failed to load {} registry, creating fresh: {}", label, e.getMessage());
            return seeded(systemMemoryId, seedTypes);
        }
    }

    @Deprecated
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
                MemoryId registryId = "entity-type".equals(label)
                        ? SystemMemoryId.ENTITY_TYPE.id()
                        : "relation-type".equals(label)
                                ? SystemMemoryId.RELATION_TYPE.id()
                                : MemoryId.of("graph", label);
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
