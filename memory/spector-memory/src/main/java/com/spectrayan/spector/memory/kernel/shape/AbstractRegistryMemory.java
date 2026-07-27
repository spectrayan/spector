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
package com.spectrayan.spector.memory.kernel.shape;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;

/**
 * Abstract base class for small string-to-int registries.
 */
public abstract class AbstractRegistryMemory extends AbstractMemory<RegistryLayout> implements RegistryMemory {

    private final ConcurrentHashMap<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idToName = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);
    private long writeOffset = 0; // Current write position relative to dataOffset()

    protected AbstractRegistryMemory(MemoryId id, RegistryLayout layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
        initializeFromSegment();
    }

    protected AbstractRegistryMemory(MemoryId id, RegistryLayout layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
        initializeFromSegment();
    }

    protected AbstractRegistryMemory(MemoryId id, RegistryLayout layout, int capacity,
                                      Arena arena, MemorySegment segment, int count,
                                      boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
        initializeFromSegment();
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.REGISTRY;
    }

    private synchronized void initializeFromSegment() {
        nameToId.clear();
        idToName.clear();
        writeOffset = 0;
        int entryCount = count; // count field in header tracks the number of entries
        
        long base = dataOffset();
        int maxId = -1;
        
        for (int i = 0; i < entryCount; i++) {
            if (base + writeOffset + 6 > segment().byteSize()) {
                break; // Corrupted file bounds check
            }
            int nameLen = Short.toUnsignedInt(segment().get(ValueLayout.JAVA_SHORT_UNALIGNED, base + writeOffset));
            if (base + writeOffset + 2 + nameLen + 4 > segment().byteSize()) {
                break; // Corrupted file bounds check
            }
            
            // Read name
            byte[] nameBytes = new byte[nameLen];
            MemorySegment.copy(segment(), ValueLayout.JAVA_BYTE, base + writeOffset + 2, nameBytes, 0, nameLen);
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            
            // Read ID
            int id = segment().get(ValueLayout.JAVA_INT_UNALIGNED, base + writeOffset + 2 + nameLen);
            
            nameToId.put(name, id);
            idToName.put(id, name);
            maxId = Math.max(maxId, id);
            
            writeOffset += 2 + nameLen + 4;
        }
        
        nextId.set(maxId + 1);
    }

    @Override
    public synchronized int intern(String name) {
        if (name == null || name.isBlank()) {
            return intern("OTHER");
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        Integer existing = nameToId.get(normalized);
        if (existing != null) {
            return existing;
        }

        int newId = nextId.getAndIncrement();
        byte[] nameBytes = normalized.getBytes(StandardCharsets.UTF_8);
        int nameLen = nameBytes.length;

        // Verify segment bounds
        long base = dataOffset();
        if (base + writeOffset + 2 + nameLen + 4 > segment().byteSize()) {
            throw new IndexOutOfBoundsException("Registry memory segment is full: " + id());
        }

        if (wal != null && !bypassWal) {
            wal.appendRegistryIntern(id.toString(), newId, normalized);
        }

        // Write name length
        segment().set(ValueLayout.JAVA_SHORT_UNALIGNED, base + writeOffset, (short) nameLen);
        // Write name bytes
        MemorySegment.copy(MemorySegment.ofArray(nameBytes), 0, segment(), base + writeOffset + 2, nameLen);
        // Write ID
        segment().set(ValueLayout.JAVA_INT_UNALIGNED, base + writeOffset + 2 + nameLen, newId);

        // Update counts
        writeOffset += 2 + nameLen + 4;
        count++;
        persistCount();

        nameToId.put(normalized, newId);
        idToName.put(newId, normalized);

        return newId;
    }

    /**
     * Directly inserts a name-to-ID mapping without allocating a new ID.
     * Used for loading/migration.
     */
    public synchronized void putDirect(String name, int id) {
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        nameToId.put(normalized, id);
        idToName.put(id, normalized);
        
        int maxId = nextId.get();
        if (id >= maxId) {
            nextId.set(id + 1);
        }
        
        byte[] nameBytes = normalized.getBytes(StandardCharsets.UTF_8);
        int nameLen = nameBytes.length;
        long base = dataOffset();
        if (base + writeOffset + 2 + nameLen + 4 <= segment().byteSize()) {
            segment().set(ValueLayout.JAVA_SHORT_UNALIGNED, base + writeOffset, (short) nameLen);
            MemorySegment.copy(MemorySegment.ofArray(nameBytes), 0, segment(), base + writeOffset + 2, nameLen);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, base + writeOffset + 2 + nameLen, id);
            writeOffset += 2 + nameLen + 4;
            count++;
            persistCount();
        }
    }

    @Override
    public String nameOf(int id) {
        return idToName.get(id);
    }

    @Override
    public int idOf(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        Integer id = nameToId.get(normalized);
        return id != null ? id : -1;
    }

    @Override
    public int size() {
        return nameToId.size();
    }

    @Override
    public java.util.Map<String, Integer> entries() {
        return java.util.Collections.unmodifiableMap(nameToId);
    }
}
