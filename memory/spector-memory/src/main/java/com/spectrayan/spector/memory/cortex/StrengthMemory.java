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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout.AuditRecord;
import com.spectrayan.spector.memory.kernel.FloatUnaryOperator;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.memory.model.MemoryType;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Off-heap store managing the single unified Recall Audit Region per partition bundle (ADR-0028).
 *
 * <h3>Design &amp; Addressing</h3>
 * <p>Stores fixed-stride {@link StrengthLayout} records for all tiers (Semantic, Episodic,
 * Procedural) in a single contiguous memory segment. Offsets are mapped deterministically via
 * cumulative tier base slots:</p>
 * <pre>
 *   [0 .. N_sem - 1]     : Semantic Audit Records
 *   [N_sem .. N_sem+epi] : Episodic Audit Records
 *   [N_sem+epi .. Total] : Procedural Audit Records
 * </pre>
 *
 * @see StrengthLayout
 * @see AbstractRecordMemory
 */
public final class StrengthMemory extends AbstractRecordMemory<StrengthLayout> {

    private final int semanticCapacity;
    private final int episodicCapacity;
    private final int proceduralCapacity;

    public StrengthMemory(MemoryId id, StrengthLayout layout,
                          int semanticCapacity, int episodicCapacity, int proceduralCapacity,
                          Arena arena, MemorySegment segment, int count,
                          boolean persistent, Path filePath,
                          FileChannel fileChannel, boolean bundleManaged) {
        super(id, layout, semanticCapacity + episodicCapacity + proceduralCapacity,
                arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
        this.semanticCapacity = semanticCapacity;
        this.episodicCapacity = episodicCapacity;
        this.proceduralCapacity = proceduralCapacity;
    }

    /**
     * Creates an in-memory heap-backed StrengthMemory for testing or transient runtime.
     */
    public static StrengthMemory heap(int semanticCapacity, int episodicCapacity, int proceduralCapacity) {
        Arena arena = Arena.ofShared();
        int totalCapacity = semanticCapacity + episodicCapacity + proceduralCapacity;
        long bytes = (long) totalCapacity * StrengthLayout.INSTANCE.recordStride();
        MemorySegment segment = arena.allocate(bytes, 32);
        return new StrengthMemory(
                MemoryId.of("default", "heap-strength"),
                StrengthLayout.INSTANCE,
                semanticCapacity,
                episodicCapacity,
                proceduralCapacity,
                arena,
                segment,
                0,
                false,
                null,
                null,
                false);
    }

    /**
     * Creates a bundle-backed StrengthMemory from a pre-sliced region segment.
     */
    public static StrengthMemory fromBundle(Arena arena, MemorySegment segment,
                                            int semanticCapacity, int episodicCapacity, int proceduralCapacity,
                                            Path bundlePath, String memoryName) {
        String name = (memoryName != null && !memoryName.isBlank()) ? memoryName : "bundle-strength";
        return new StrengthMemory(
                MemoryId.of("default", name),
                StrengthLayout.INSTANCE,
                semanticCapacity,
                episodicCapacity,
                proceduralCapacity,
                arena,
                segment,
                0,
                true,
                bundlePath,
                null,
                true);
    }

    /**
     * Creates a bundle-backed StrengthMemory from a pre-sliced region segment with default memory name.
     */
    public static StrengthMemory fromBundle(Arena arena, MemorySegment segment,
                                            int semanticCapacity, int episodicCapacity, int proceduralCapacity,
                                            Path bundlePath) {
        return fromBundle(arena, segment, semanticCapacity, episodicCapacity, proceduralCapacity, bundlePath, "bundle-strength");
    }

    public int semanticCapacity() {
        return semanticCapacity;
    }

    public int episodicCapacity() {
        return episodicCapacity;
    }

    public int proceduralCapacity() {
        return proceduralCapacity;
    }

    /**
     * Computes the base slot index for a given memory tier.
     */
    public int tierBaseSlot(MemoryType tier) {
        if (tier == null) return 0;
        return switch (tier) {
            case SEMANTIC -> 0;
            case EPISODIC -> semanticCapacity;
            case PROCEDURAL -> semanticCapacity + episodicCapacity;
            case WORKING -> 0;
        };
    }

    /**
     * Computes the global slot index for a (tier, slotIndex) pair.
     */
    public int globalSlotIndex(MemoryType tier, int slotIndex) {
        return tierBaseSlot(tier) + slotIndex;
    }

    /**
     * Computes the off-heap byte offset for a (tier, slotIndex) audit record.
     */
    public long strengthOffset(MemoryType tier, int slotIndex) {
        return recordOffset(globalSlotIndex(tier, slotIndex));
    }

    // ── Field Operations ──

    /**
     * Initializes default audit fields for a new memory at (tier, slotIndex).
     */
    public void initializeDefault(MemoryType tier, int slotIndex, float baseImportance) {
        long offset = strengthOffset(tier, slotIndex);
        layout.initializeDefaultRecord(segment, offset, tier, baseImportance);
    }

    /**
     * Reads the immutable {@link AuditRecord} snapshot for (tier, slotIndex).
     */
    public AuditRecord readAuditRecord(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.readRecord(segment, offset);
    }

    /**
     * Writes the {@link AuditRecord} snapshot to (tier, slotIndex).
     */
    public void writeAuditRecord(MemoryType tier, int slotIndex, AuditRecord record) {
        long offset = strengthOffset(tier, slotIndex);
        layout.writeRecord(segment, offset, record);
    }

    /**
     * Reads the explicit agent recall count for (tier, slotIndex).
     */
    public int readAgentRecallCount(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.readAgentRecallCount(segment, offset);
    }

    /**
     * Reads the passive spector recall count for (tier, slotIndex).
     */
    public int readSpectorRecallCount(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.readSpectorRecallCount(segment, offset);
    }

    /**
     * Reads the Two-Factor storage strength for (tier, slotIndex).
     */
    public float readStorageStrength(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.readStorageStrength(segment, offset);
    }

    /**
     * Atomically increments the explicit agent recall count for (tier, slotIndex).
     */
    public int incrementAgentRecallCount(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.incrementAgentRecallCount(segment, offset);
    }

    /**
     * Atomically increments the passive spector recall count for (tier, slotIndex).
     */
    public int incrementSpectorRecallCount(MemoryType tier, int slotIndex) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.incrementSpectorRecallCount(segment, offset);
    }

    /**
     * Atomically updates the effective importance for (tier, slotIndex).
     */
    public float casEffectiveImportance(MemoryType tier, int slotIndex, FloatUnaryOperator updateFn) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.casEffectiveImportance(segment, offset, updateFn);
    }

    /**
     * Atomically updates the Two-Factor storage strength for (tier, slotIndex).
     */
    public float casStorageStrength(MemoryType tier, int slotIndex, FloatUnaryOperator updateFn) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.casStorageStrength(segment, offset, updateFn);
    }

    /**
     * Records a recall event (ACT-R timestamp ring buffer, last recall timestamp, and profile).
     */
    public void recordRecall(MemoryType tier, int slotIndex, long creationMs, long nowMs, byte profileOrdinal, int agentHash) {
        long offset = strengthOffset(tier, slotIndex);
        layout.writeLastRecallTimestamp(segment, offset, nowMs);
        layout.writeLastRecallProfile(segment, offset, profileOrdinal);
        if (agentHash != 0) {
            layout.writeLastAgentHash(segment, offset, agentHash);
        }
        layout.recordActRRecall(segment, offset, creationMs, nowMs);
    }

    /**
     * Computes the ACT-R activation for (tier, slotIndex).
     */
    public float computeActRActivation(MemoryType tier, int slotIndex, long creationMs, long nowMs) {
        long offset = strengthOffset(tier, slotIndex);
        return layout.computeActRActivation(segment, offset, creationMs, nowMs);
    }
}
