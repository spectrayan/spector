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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.kernel.id.MemoryId;
import com.spectrayan.spector.memory.kernel.shape.MemoryShape;
import com.spectrayan.spector.memory.kernel.region.RegionPreamble;
import com.spectrayan.spector.memory.kernel.id.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractRegistryMemory;
import com.spectrayan.spector.memory.kernel.shape.DefaultRegistryMemory;

/**
 * Thread-safe, open-schema string ↔ integer type registry, extending {@link AbstractRegistryMemory}.
 */
public final class TypeRegistryMemory extends AbstractRegistryMemory {

    private static final Logger log = LoggerFactory.getLogger(TypeRegistryMemory.class);

    /** Legacy File magic: "TREG" in ASCII. */
    private static final int LEGACY_FILE_MAGIC = 0x54524547;

    private final String label;

    /**
     * Creates a new empty registry (volatile).
     *
     * @param systemMemoryId system memory identifier containing the label for logging
     */
    public TypeRegistryMemory(SystemMemoryId systemMemoryId) {
        super(systemMemoryId.id(), new RegistryLayout(), 1024, 256 * 1024);
        this.label = systemMemoryId.id().memoryName();
    }

    /**
     * Creates or opens a file-backed registry.
     */
    public TypeRegistryMemory(SystemMemoryId systemMemoryId, Path filePath) {
        super(systemMemoryId.id(), new RegistryLayout(), 0, 0, filePath);
        this.label = systemMemoryId.id().memoryName();
    }

    private TypeRegistryMemory(SystemMemoryId systemMemoryId, RegistryLayout layout, int capacity,
                               Arena arena, MemorySegment segment, int count,
                               boolean persistent, Path filePath, FileChannel fileChannel,
                               boolean bundleManaged) {
        super(systemMemoryId.id(), layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
        this.label = systemMemoryId.id().memoryName();
    }

    private TypeRegistryMemory(SystemMemoryId systemMemoryId, RegistryLayout layout, int capacity,
                               com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, int count,
                               boolean persistent, Path filePath) {
        super(systemMemoryId.id(), layout, capacity, regionRef, count, persistent, filePath);
        this.label = systemMemoryId.id().memoryName();
    }

    public static TypeRegistryMemory fromRegionRef(SystemMemoryId systemMemoryId, com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, Path bundlePath, boolean isNew, String... seedTypes) {
        RegistryLayout layout = new RegistryLayout();
        MemorySegment regionSlice = regionRef.resolve();
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(regionSlice, 0L, layout.schemaVersion(), MemoryShape.REGISTRY, 0,
                    (int) regionSlice.byteSize(), 0, 0, layout.layoutId(), now, now);
        }

        TypeRegistryMemory reg = new TypeRegistryMemory(systemMemoryId, layout, 1024, regionRef,
                isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0L),
                true, bundlePath);

        if (seedTypes != null) {
            for (String seed : seedTypes) {
                if (seed != null) {
                    reg.intern(seed);
                }
            }
        }
        return reg;
    }

    public static TypeRegistryMemory seeded(SystemMemoryId systemMemoryId, String... seedTypes) {
        TypeRegistryMemory registry = new TypeRegistryMemory(systemMemoryId);
        if (seedTypes != null) {
            for (String type : seedTypes) {
                if (type != null) {
                    registry.intern(type);
                }
            }
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

    @Override
    public String nameOf(int id) {
        String name = super.nameOf(id);
        return name != null ? name : "UNKNOWN";
    }

    // ── Persistence: save / load with transparent legacy support ──

    public static TypeRegistryMemory fromBundle(SystemMemoryId systemMemoryId, Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew, String... seedTypes) {
        RegistryLayout layout = new RegistryLayout();
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(regionSlice, 0L, layout.schemaVersion(), MemoryShape.REGISTRY, 0,
                    (int) regionSlice.byteSize(), 0, 0, layout.layoutId(), now, now);
        }

        TypeRegistryMemory reg = new TypeRegistryMemory(systemMemoryId, layout, 1024, arena, regionSlice,
                isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0L),
                true, bundlePath, null, true);

        if (seedTypes != null) {
            for (String seed : seedTypes) {
                if (seed != null) {
                    reg.intern(seed);
                }
            }
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
                            .forEach(entry -> reg.putDirect(entry.getKey(), entry.getValue()));
                    reg.flush();
                    legacy.close();
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
        if (isBundleManaged()) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment, 0L, layout().schemaVersion(), MemoryShape.REGISTRY, 0,
                    capacity(), size(), layout().recordStride(), layout().layoutId(), now, now);
            flush();
            log.info("{} registry saved to bundle: {} types", label, size());
            return;
        }

        Files.deleteIfExists(filePath);
        Files.createDirectories(filePath.getParent());

        MemoryId registryId = id();
        RegistryLayout layout = new RegistryLayout();

        // Calculate total size required for the new persistent registry memory segment
        long totalDataBytes = 0;
        Map<String, Integer> currentEntries = entries();
        for (String name : currentEntries.keySet()) {
            totalDataBytes += 2 + name.getBytes(StandardCharsets.UTF_8).length + 4;
        }

        try (DefaultRegistryMemory fileBacking = new DefaultRegistryMemory(
                registryId, layout, currentEntries.size(), RegionPreamble.PREAMBLE_BYTES + totalDataBytes, filePath)) {
            
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

            boolean isStandard = (magic == RegionPreamble.MAGIC || magic == 0x4D4B4D53);
            boolean isLegacy = (magic == LEGACY_FILE_MAGIC || magic == 0x47455254);

            if (isStandard) {
                TypeRegistryMemory registry = new TypeRegistryMemory(systemMemoryId, filePath);

                // Ensure all seed types are present (e.g. if new seed types were added)
                if (seedTypes != null) {
                    for (String seed : seedTypes) {
                        if (seed != null) {
                            registry.intern(seed);
                        }
                    }
                }
                log.info("{} registry loaded (SMKM V1): {} types from {}", label, registry.size(), filePath.getFileName());
                return registry;
            } else if (isLegacy) {
                TypeRegistryMemory registry = new TypeRegistryMemory(systemMemoryId);
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

                        registry.putDirect(name, id);
                    }
                }

                // Ensure all seed types are present
                if (seedTypes != null) {
                    for (String seed : seedTypes) {
                        if (seed != null) {
                            registry.intern(seed);
                        }
                    }
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

    @Override
    public String toString() {
        return "TypeRegistry[" + label + ", size=" + size() + "]";
    }
}
