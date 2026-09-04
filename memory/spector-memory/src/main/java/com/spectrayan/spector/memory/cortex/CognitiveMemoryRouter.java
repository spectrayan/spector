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

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.cortex.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.EpisodicFieldAccessor;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.EnumMap;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Cognitive record memory store registry and polymorphic routing — zero switch statements.
 *
 * <h3>Design Pattern: Strategy + Registry</h3>
 * <p>Holds a {@code EnumMap<MemoryType, EngramMemory>} and dispatches all operations
 * polymorphically via the {@link EngramMemory} interface. Adding a new memory store
 * requires: (1) implement {@link EngramMemory}, (2) register here.
 * Zero changes to SpectorMemory, RecallPipeline, or IngestionPipeline.</p>
 *
 * <h3>SOLID Compliance</h3>
 * <ul>
 *   <li><b>OCP</b>: Open for extension (new memory stores), closed for modification</li>
 *   <li><b>DIP</b>: Depends on {@link EngramMemory} abstraction, not concrete stores</li>
 *   <li><b>LSP</b>: All stores are substitutable via the common interface</li>
 * </ul>
 */
public final class CognitiveMemoryRouter implements AutoCloseable {

    private final EnumMap<MemoryType, EngramMemory> stores = new EnumMap<>(MemoryType.class);

    // ── Typed accessors for store-specific operations ──
    private final WorkingMemory workingStore;
    private final EpisodicRecordMemory episodicStore;
    private final SemanticMemory semanticStore;
    private final ProceduralMemory proceduralStore;
    private final EpisodicLogMemory episodicLogStore;
    private final StrengthMemory strengthStore;

    /**
     * Creates a CognitiveMemoryRouter with the modern log-structured episodic store.
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore,
                                 EpisodicLogMemory episodicLogStore) {
        this(workingStore, null, semanticStore, proceduralStore, episodicLogStore, null);
    }

    /**
     * Creates a CognitiveMemoryRouter with all four cognitive memory stores.
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 EpisodicRecordMemory episodicStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore) {
        this(workingStore, episodicStore, semanticStore, proceduralStore, null, null);
    }

    /**
     * Creates a CognitiveMemoryRouter with the new log-structured episodic store.
     *
     * <p>When {@code episodicLogStore} is non-null, the EPISODIC slot uses the
     * log-structured store and the legacy fixed-stride episodic store is not
     * registered in the EnumMap.</p>
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 EpisodicRecordMemory episodicStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore,
                                 EpisodicLogMemory episodicLogStore) {
        this(workingStore, episodicStore, semanticStore, proceduralStore, episodicLogStore, null);
    }

    /**
     * Creates a CognitiveMemoryRouter with all stores and the unified Recall Strength store.
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 EpisodicRecordMemory episodicStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore,
                                 EpisodicLogMemory episodicLogStore,
                                 StrengthMemory strengthStore) {
        this.workingStore = workingStore;
        this.episodicStore = episodicStore;
        this.semanticStore = semanticStore;
        this.proceduralStore = proceduralStore;
        this.episodicLogStore = episodicLogStore;
        this.strengthStore = strengthStore;

        // Register in EnumMap for polymorphic dispatch
        stores.put(MemoryType.WORKING, workingStore);
        if (episodicStore != null) {
            stores.put(MemoryType.EPISODIC, episodicStore);
        }
        stores.put(MemoryType.SEMANTIC, semanticStore);
        stores.put(MemoryType.PROCEDURAL, proceduralStore);
    }

    // ══════════════════════════════════════════════════════════════
    // POLYMORPHIC DISPATCH (zero switch statements)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the {@link EngramMemory} for a given memory type.
     *
     * @throws SpectorValidationException if no store is registered for the type
     */
    public EngramMemory get(MemoryType type) {
        EngramMemory store = stores.get(type);
        if (store == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "storeType", type);
        }
        return store;
    }

    /**
     * Routes a memory write to the appropriate memory store.
     *
     * @param type       target memory type
     * @param header     cognitive header
     * @param quantized  quantized vector bytes
     * @return byte offset where the record was written
     */
    public long write(MemoryType type, CognitiveHeader header, byte[] quantized) {
        long offset = get(type).write(header, quantized);
        if (strengthStore != null && type != MemoryType.WORKING) {
            int slotIndex = (int) ((offset - get(type).dataOffset()) / layoutFor(type).stride());
            strengthStore.initializeDefault(type, slotIndex, header.importance(), header.storageStrength(), header.agentRecallCount());
        }
        return offset;
    }

    /**
     * Returns the primary memory segment for a given memory type.
     */
    public MemorySegment segmentFor(MemoryType type) {
        return get(type).primarySegment();
    }

    /**
     * Returns the layout for a given memory type.
     */
    public CognitiveRecordLayout layoutFor(MemoryType type) {
        return get(type).cognitiveLayout();
    }

    /**
     * Returns the record count for a given memory type.
     */
    public int countFor(MemoryType type) {
        int count = 0;
        EngramMemory store = stores.get(type);
        if (store != null) {
            count += store.size();
        }
        if (type == MemoryType.EPISODIC && episodicLogStore != null) {
            count += episodicLogStore.unconsolidatedTurnOffsets().size();
        }
        return count;
    }

    /**
     * Returns the total memory count across all registered memory stores.
     */
    public int totalCount() {
        int total = 0;
        for (EngramMemory store : stores.values()) {
            if (store != null) {
                total += store.size();
            }
        }
        if (isEpisodicLogMode() && episodicLogStore != null) {
            total += episodicLogStore.unconsolidatedTurnOffsets().size();
        }
        return total;
    }

    /**
     * Checks if a given memory type should be scanned based on the target type filter.
     *
     * @param type        the type to check
     * @param targetTypes target type filter (null or empty = scan all)
     * @return true if this type should be scanned
     */
    public static boolean shouldScan(MemoryType type, MemoryType[] targetTypes) {
        if (targetTypes == null || targetTypes.length == 0) return true;
        for (MemoryType t : targetTypes) {
            if (t == type) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    // POINT-LOCATION DOMAIN OPERATIONS (issue #437, TD-12 / Law of Demeter)
    //
    // These intention-revealing methods encapsulate the low-level segment/offset/flag
    // byte access that façade callers (e.g. DefaultSpectorMemory) used to hand-poke.
    // A caller resolves the partition-correct router via
    // {@code PartitionRegistry.routerFor(loc.colocatedPartition())} (issue #443) and then
    // invokes these on that router, so point reads/writes stay on the partition the
    // memory actually lives in.
    // ══════════════════════════════════════════════════════════════

    /**
     * Sets the tombstone flag (logical deletion) for the record at the given location.
     * No-op if the tier segment is unavailable.
     */
    public void tombstone(MemoryLocation loc) {
        MemorySegment segment = segmentFor(loc.type());
        if (segment != null) {
            layoutFor(loc.type()).tombstone(segment, loc.offset());
        }
        if (strengthStore != null && loc.type() != MemoryType.WORKING) {
            int slotIndex = (int) ((loc.offset() - get(loc.type()).dataOffset()) / layoutFor(loc.type()).stride());
            strengthStore.resetRecord(loc.type(), slotIndex);
        }
    }

    /** Sets the resolved flag (Zeigarnik Effect) for the record at the given location. */
    public void markResolved(MemoryLocation loc) {
        layoutFor(loc.type()).markResolved(segmentFor(loc.type()), loc.offset());
    }

    /** Clears the resolved flag (Zeigarnik Effect) for the record at the given location. */
    public void markUnresolved(MemoryLocation loc) {
        layoutFor(loc.type()).markUnresolved(segmentFor(loc.type()), loc.offset());
    }

    /**
     * Returns {@code true} if the record at the given location has the tombstone flag set.
     * Returns {@code false} when the tier segment/layout is unavailable.
     */
    public boolean isTombstoned(MemoryLocation loc) {
        CognitiveRecordLayout layout = layoutFor(loc.type());
        MemorySegment segment = segmentFor(loc.type());
        if (layout == null || segment == null) return false;
        byte flags = segment.get(EncodingHeaderFields.LAYOUT_FLAGS,
                loc.offset() + EncodingHeaderFields.OFFSET_FLAGS);
        return EncodingHeaderFields.isTombstoned(flags);
    }

    /**
     * Reads the cognitive record body (header, extended fields, and optionally the
     * quantized vector) for the record at the given location from a single segment
     * snapshot. Returns {@code null} when the tier segment/layout is unavailable.
     *
     * @param loc           the record location (resolved to this router's partition)
     * @param includeVector when {@code true}, also copies the quantized vector payload;
     *                      when {@code false}, {@link CognitiveRecordBody#quantizedVector()}
     *                      is {@code null} (avoids a per-record copy in scan-style callers)
     */
    public CognitiveRecordBody readRecordBody(MemoryLocation loc, boolean includeVector) {
        CognitiveRecordLayout layout = layoutFor(loc.type());
        MemorySegment segment = segmentFor(loc.type());
        if (layout == null || segment == null) return null;

        long offset = loc.offset();
        CognitiveHeader header = layout.readHeader(segment, offset);
        if (strengthStore != null && loc.type() != MemoryType.WORKING) {
            int slotIndex = (int) ((offset - get(loc.type()).dataOffset()) / layout.stride());
            header = new CognitiveHeader(
                    header.timestampMs(),
                    header.synapticTags(),
                    header.exactNorm(),
                    strengthStore.readEffectiveImportance(loc.type(), slotIndex),
                    strengthStore.readAgentRecallCount(loc.type(), slotIndex),
                    header.centroidId(),
                    header.valence(),
                    header.flags(),
                    header.arousal(),
                    strengthStore.readStorageStrength(loc.type(), slotIndex),
                    header.encodingProfile(),
                    header.encodingAlpha(),
                    header.encodingBeta(),
                    header.soulVersion(),
                    header.encodingSurprise(),
                    header.consolidationFlags()
            );
        }

        byte[] quantizedVec = null;
        if (includeVector) {
            int vecBytes = layout.quantizedVecBytes();
            quantizedVec = new byte[vecBytes];
            long vecOffset = layout.vectorOffset(offset);
            MemorySegment.copy(
                    segment, ValueLayout.JAVA_BYTE, vecOffset,
                    MemorySegment.ofArray(quantizedVec),
                    ValueLayout.JAVA_BYTE, 0, vecBytes);
        }

        int spectorRecallCount = (strengthStore != null && loc.type() != MemoryType.WORKING)
                ? strengthStore.readSpectorRecallCount(loc.type(), (int) ((offset - get(loc.type()).dataOffset()) / layout.stride()))
                : layout.readSpectorRecallCount(segment, offset);
        byte consolidationFlags = layout.readConsolidationFlags(segment, offset);
        return new CognitiveRecordBody(header, quantizedVec, spectorRecallCount, consolidationFlags);
    }

    /**
     * Immutable value carrying the decoded body of a cognitive record read from a single
     * segment snapshot: the base {@link CognitiveHeader}, the optional quantized vector
     * payload ({@code null} when the caller requested header-only), and the extended fields
     * ({@code spectorRecallCount}, {@code consolidationFlags}) that live outside the base header.
     */
    public record CognitiveRecordBody(CognitiveHeader header,
                                      byte[] quantizedVector,
                                      int spectorRecallCount,
                                      byte consolidationFlags) {
    }

    // ══════════════════════════════════════════════════════════════
    // TYPED ACCESSORS (for store-specific operations)
    // ══════════════════════════════════════════════════════════════

    /** Returns the Working Memory store (for circular buffer scan). */
    public WorkingMemory working() { return workingStore; }

    /** Returns the Episodic Memory store (for partition iteration). Null when log mode active. */
    public EpisodicRecordMemory episodic() { return episodicStore; }

    /** Returns the log-structured Episodic store. Null in legacy mode. */
    public EpisodicLogMemory episodicLog() { return episodicLogStore; }

    /** Returns true when the new log-structured episodic store is active. */
    public boolean isEpisodicLogMode() { return episodicLogStore != null; }

    /** Returns the Semantic Memory store (for header slab access). */
    public SemanticMemory semantic() { return semanticStore; }

    /** Returns the Procedural Memory store (for flat scan). */
    public ProceduralMemory procedural() { return proceduralStore; }

    /** Returns the unified Strength memory store. Null if not configured. */
    public StrengthMemory strength() { return strengthStore; }

    /**
     * @deprecated Use {@link #strength()} instead.
     */
    @Deprecated
    public StrengthMemory audit() { return strengthStore; }

    /**
     * Forces all persistent, non-frozen memory store segments to be written to disk.
     * Used by {@code CheckpointDaemon} before recording a WAL checkpoint.
     */
    public void forceAll() {
        for (EngramMemory store : stores.values()) {
            if (store.isPersistent() && !store.isFrozen()) {
                store.force();
            }
        }
    }

    @Override
    public void close() {
        stores.values().forEach(store -> {
            try {
                store.close();
            } catch (Exception e) {
                // Log and continue closing remaining stores
            }
        });
    }
}
