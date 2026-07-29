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
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;

import java.lang.foreign.MemorySegment;
import java.util.EnumMap;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Cognitive record memory store registry and polymorphic routing — zero switch statements.
 *
 * <h3>Design Pattern: Strategy + Registry</h3>
 * <p>Holds a {@code EnumMap<MemoryType, CognitiveRecordMemory>} and dispatches all operations
 * polymorphically via the {@link CognitiveRecordMemory} interface. Adding a new memory store
 * requires: (1) implement {@link CognitiveRecordMemory}, (2) register here.
 * Zero changes to SpectorMemory, RecallPipeline, or IngestionPipeline.</p>
 *
 * <h3>SOLID Compliance</h3>
 * <ul>
 *   <li><b>OCP</b>: Open for extension (new memory stores), closed for modification</li>
 *   <li><b>DIP</b>: Depends on {@link CognitiveRecordMemory} abstraction, not concrete stores</li>
 *   <li><b>LSP</b>: All stores are substitutable via the common interface</li>
 * </ul>
 */
public final class CognitiveMemoryRouter implements AutoCloseable {

    private final EnumMap<MemoryType, CognitiveRecordMemory> stores = new EnumMap<>(MemoryType.class);

    // ── Typed accessors for store-specific operations ──
    private final WorkingRecordMemory workingStore;
    private final EpisodicMemoryStore episodicStore;
    private final SemanticRecordMemory semanticStore;
    private final ProceduralRecordMemory proceduralStore;

    /**
     * Creates a CognitiveMemoryRouter with all four cognitive memory stores.
     */
    public CognitiveMemoryRouter(WorkingRecordMemory workingStore,
                                 EpisodicMemoryStore episodicStore,
                                 SemanticRecordMemory semanticStore,
                                 ProceduralRecordMemory proceduralStore) {
        this.workingStore = workingStore;
        this.episodicStore = episodicStore;
        this.semanticStore = semanticStore;
        this.proceduralStore = proceduralStore;

        // Register in EnumMap for polymorphic dispatch
        stores.put(MemoryType.WORKING, workingStore);
        stores.put(MemoryType.EPISODIC, episodicStore);
        stores.put(MemoryType.SEMANTIC, semanticStore);
        stores.put(MemoryType.PROCEDURAL, proceduralStore);
    }

    // ══════════════════════════════════════════════════════════════
    // POLYMORPHIC DISPATCH (zero switch statements)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the {@link CognitiveRecordMemory} for a given memory type.
     *
     * @throws SpectorValidationException if no store is registered for the type
     */
    public CognitiveRecordMemory get(MemoryType type) {
        CognitiveRecordMemory store = stores.get(type);
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
        return get(type).write(header, quantized);
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
        return get(type).size();
    }

    /**
     * Returns the total memory count across all registered memory stores.
     */
    public int totalCount() {
        int total = 0;
        for (CognitiveRecordMemory store : stores.values()) {
            total += store.size();
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
    // TYPED ACCESSORS (for store-specific operations)
    // ══════════════════════════════════════════════════════════════

    /** Returns the Working Memory store (for circular buffer scan). */
    public WorkingRecordMemory working() { return workingStore; }

    /** Returns the Episodic Memory store (for partition iteration). */
    public EpisodicMemoryStore episodic() { return episodicStore; }

    /** Returns the Semantic Memory store (for header slab access). */
    public SemanticRecordMemory semantic() { return semanticStore; }

    /** Returns the Procedural Memory store (for flat scan). */
    public ProceduralRecordMemory procedural() { return proceduralStore; }

    /**
     * Forces all persistent memory store segments to be written to disk.
     * Used by {@code CheckpointDaemon} before recording a WAL checkpoint.
     */
    public void forceAll() {
        for (CognitiveRecordMemory store : stores.values()) {
            if (store.isPersistent()) {
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
