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
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.EpisodeRecord;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.EnumMap;
import java.util.Objects;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Cognitive record memory store registry and polymorphic routing — zero switch statements.
 *
 * <h3>Design Pattern: Strategy + Registry</h3>
 * <p>Holds an {@code EnumMap<MemoryType, EngramMemory>} for fixed-stride tiers and provides direct
 * typed access to {@link EpisodicMemory} (variable-length append log). Realizes R5.1 (single wrapper per
 * region slice), R5.2 (unconditional store registration), and R5.3 (layout-mismatch fence).</p>
 *
 * @since 1.0.0
 */
public final class CognitiveMemoryRouter implements AutoCloseable {

    private final EnumMap<MemoryType, EngramMemory> stores = new EnumMap<>(MemoryType.class);

    // ── Typed accessors for store-specific operations ──
    private final WorkingMemory workingStore;
    private final SemanticMemory semanticStore;
    private final ProceduralMemory proceduralStore;
    private final EpisodicMemory episodicStore;
    private final StrengthMemory strengthStore;

    /**
     * Creates a CognitiveMemoryRouter with the four cognitive memory stores and unified Strength store.
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore,
                                 EpisodicMemory episodicStore,
                                 StrengthMemory strengthStore) {
        this.workingStore = workingStore;
        this.semanticStore = semanticStore;
        this.proceduralStore = proceduralStore;
        this.episodicStore = episodicStore;
        this.strengthStore = strengthStore;

        // Registration for fixed-stride cognitive stores (R5.2)
        if (workingStore != null) stores.put(MemoryType.WORKING, workingStore);
        if (semanticStore != null) stores.put(MemoryType.SEMANTIC, semanticStore);
        if (proceduralStore != null) stores.put(MemoryType.PROCEDURAL, proceduralStore);
    }

    /**
     * Creates a CognitiveMemoryRouter without a Strength store.
     */
    public CognitiveMemoryRouter(WorkingMemory workingStore,
                                 SemanticMemory semanticStore,
                                 ProceduralMemory proceduralStore,
                                 EpisodicMemory episodicStore) {
        this(workingStore, semanticStore, proceduralStore, episodicStore, null);
    }

    // ══════════════════════════════════════════════════════════════
    // POLYMORPHIC DISPATCH (zero switch statements)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the {@link EngramMemory} for a given memory type.
     *
     * @throws SpectorValidationException if no store is registered or if type is EPISODIC (layout mismatch)
     */
    public EngramMemory get(MemoryType type) {
        if (type == MemoryType.EPISODIC) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "storeType",
                    "MemoryType.EPISODIC is variable-length and cannot be accessed via fixed-stride EngramMemory; use router.episodic()");
        }
        EngramMemory store = stores.get(type);
        if (store == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "storeType", type);
        }
        return store;
    }

    /**
     * Routes a memory write to the appropriate memory store.
     * Rejects layout-mismatched writes to EPISODIC tier (R5.3 / P0.1 fence).
     *
     * @param type       target memory type
     * @param header     cognitive header
     * @param quantized  quantized vector bytes
     * @return byte offset where the record was written
     */
    public long write(MemoryType type, EncodingHeader header, byte[] quantized) {
        if (type == MemoryType.EPISODIC) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "type",
                    "Cannot route fixed-stride write to variable-length EPISODIC tier; use rememberEpisodic/EpisodicMemory.appendTurn");
        }
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
        if (type == MemoryType.EPISODIC) {
            return episodicStore.segment();
        }
        return get(type).primarySegment();
    }

    /**
     * Returns the layout for a given memory type.
     */
    public EngramLayout layoutFor(MemoryType type) {
        return get(type).cognitiveLayout();
    }

    /**
     * Returns the record count for a given memory type.
     */
    public int countFor(MemoryType type) {
        if (type == MemoryType.EPISODIC) {
            return episodicStore != null ? episodicStore.size() : 0;
        }
        EngramMemory store = stores.get(type);
        return store != null ? store.size() : 0;
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
        if (episodicStore != null) {
            total += episodicStore.size();
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
    // ══════════════════════════════════════════════════════════════

    /**
     * Sets the tombstone flag (logical deletion) for the record at the given location.
     * No-op if the tier segment is unavailable.
     */
    public void tombstone(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            episodicStore.tombstone(loc.offset());
            return;
        }
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
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.markResolved(loc.offset());
            }
            return;
        }
        layoutFor(loc.type()).markResolved(segmentFor(loc.type()), loc.offset());
    }

    /** Clears the resolved flag (Zeigarnik Effect) for the record at the given location. */
    public void markUnresolved(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.markUnresolved(loc.offset());
            }
            return;
        }
        layoutFor(loc.type()).markUnresolved(segmentFor(loc.type()), loc.offset());
    }

    /**
     * Returns {@code true} if the record at the given location has the tombstone flag set.
     * Returns {@code false} when the tier segment/layout is unavailable.
     */
    public boolean isTombstoned(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore == null) return false;
            EpisodeRecord rec = episodicStore.readTurn(loc.offset(), false);
            return rec != null && rec.isTombstoned();
        }
        EngramLayout layout = layoutFor(loc.type());
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
     */
    public CognitiveRecordBody readRecordBody(MemoryLocation loc, boolean includeVector) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore == null) return null;
            EpisodeRecord ep = episodicStore.readTurn(loc.offset(), false);
            if (ep == null) return null;
            EncodingHeader h = new EncodingHeader(
                    ep.timestampMs(), ep.sessionId(), 0.0f, ep.importance(),
                    0, ep.modelId(), ep.valence(), ep.flags(), ep.arousal(),
                    1.0f, (byte) 0, (byte) 0, (byte) 0, ep.soulVersion(),
                    0.0f, (byte) 0, ep.source()
            );
            return new CognitiveRecordBody(h, null, 0, (byte) 0);
        }
        EngramLayout layout = layoutFor(loc.type());
        MemorySegment segment = segmentFor(loc.type());
        if (layout == null || segment == null) return null;

        long offset = loc.offset();
        EncodingHeader header = layout.readHeader(segment, offset);
        if (strengthStore != null && loc.type() != MemoryType.WORKING) {
            int slotIndex = (int) ((offset - get(loc.type()).dataOffset()) / layout.stride());
            header = new EncodingHeader(
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
     * Immutable value carrying the decoded body of a cognitive record read from a single segment snapshot.
     */
    public record CognitiveRecordBody(EncodingHeader header,
                                      byte[] quantizedVector,
                                      int spectorRecallCount,
                                      byte consolidationFlags) {
    }

    // ══════════════════════════════════════════════════════════════
    // TYPED ACCESSORS (for store-specific operations)
    // ══════════════════════════════════════════════════════════════

    /** Returns the Working Memory store (for circular buffer scan). */
    public WorkingMemory working() { return workingStore; }

    /** Returns the log-structured Episodic Memory store. Never null in normal operation. */
    public EpisodicMemory episodic() { return episodicStore; }

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
        if (episodicStore != null && episodicStore.isPersistent()) {
            episodicStore.force();
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
        if (episodicStore != null) {
            try {
                episodicStore.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
