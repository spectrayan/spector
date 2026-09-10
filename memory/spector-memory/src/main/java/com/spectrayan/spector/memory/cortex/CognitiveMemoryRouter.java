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

import com.spectrayan.spector.memory.cortex.index.IndexEntryMemory;

import com.spectrayan.spector.memory.kernel.store.EngramRegion;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.cortex.index.IndexEntryMemory.MemoryLocation;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.model.EpisodeRecord;

import java.util.EnumMap;
import java.util.Objects;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.kernel.engram.FloatUnaryOperator;
import com.spectrayan.spector.memory.synapse.DecayStrategy;

/**
 * Cognitive record memory store registry and polymorphic routing — zero switch statements.
 *
 * <h3>Design Pattern: Strategy + Registry</h3>
 * <p>Holds an {@code EnumMap<MemoryType, EngramRegion>} for fixed-stride tiers and provides direct
 * typed access to {@link EpisodicMemory} (variable-length append log). Realizes R5.1 (single wrapper per
 * region slice), R5.2 (unconditional store registration), and R5.3 (layout-mismatch fence).</p>
 *
 * @since 1.0.0
 */
public final class CognitiveMemoryRouter implements AutoCloseable {

    private final EnumMap<MemoryType, EngramRegion> stores = new EnumMap<>(MemoryType.class);

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

        // Registration for all cognitive engram stores (ADR-0030)
        if (workingStore != null) stores.put(MemoryType.WORKING, workingStore);
        if (semanticStore != null) stores.put(MemoryType.SEMANTIC, semanticStore);
        if (proceduralStore != null) stores.put(MemoryType.PROCEDURAL, proceduralStore);
        if (episodicStore != null) stores.put(MemoryType.EPISODIC, episodicStore);
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
     * Returns the {@link EngramRegion} for a given memory type.
     *
     * @throws SpectorValidationException if no store is registered for the type
     */
    public EngramRegion get(MemoryType type) {
        EngramRegion store = stores.get(type);
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
     * Returns the layout for a given memory type.
     */
    public FixedEngramLayout layoutFor(MemoryType type) {
        EngramRegion store = stores.get(type);
        return store != null && store.layout() instanceof FixedEngramLayout fel ? fel : null;
    }

    /**
     * Returns the record count for a given memory type.
     */
    public int countFor(MemoryType type) {
        EngramRegion store = stores.get(type);
        return store != null ? store.size() : 0;
    }

    /**
     * Returns the nearest distance between the candidate vector and existing records in Working Memory,
     * or -1.0f if working memory is unavailable or empty.
     */
    public float nearestWorkingDistance(float[] vector, float[] mins, float[] scales) {
        if (workingStore != null && workingStore.visibleCount() > 0) {
            return workingStore.nearestDistance(vector, mins, scales);
        }
        return -1.0f;
    }

    /**
     * Returns the total memory count across all registered memory stores.
     */
    public int totalCount() {
        int total = 0;
        for (EngramRegion store : stores.values()) {
            if (store != null) {
                total += store.size();
            }
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
     */
    public void tombstone(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.tombstone(loc.offset());
            }
            return;
        }
        EngramRegion store = stores.get(loc.type());
        if (store != null) {
            store.tombstone(loc.offset());
        }
        if (strengthStore != null && loc.type() != MemoryType.WORKING && layoutFor(loc.type()) != null && store != null) {
            int slotIndex = (int) ((loc.offset() - store.dataOffset()) / layoutFor(loc.type()).stride());
            strengthStore.resetRecord(loc.type(), slotIndex);
        }
    }

    /**
     * Sets the tombstone flag for a record identified by memory type and byte offset.
     */
    public void tombstone(MemoryType type, long offset) {
        tombstone(new MemoryLocation(type, offset, 0));
    }

    /** Sets the resolved flag (Zeigarnik Effect) for the record at the given location. */
    public void markResolved(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.markResolved(loc.offset());
            }
            return;
        }
        EngramRegion store = stores.get(loc.type());
        if (store != null) {
            store.markResolved(loc.offset());
        }
    }

    /** Clears the resolved flag (Zeigarnik Effect) for the record at the given location. */
    public void markUnresolved(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.markUnresolved(loc.offset());
            }
            return;
        }
        EngramRegion store = stores.get(loc.type());
        if (store != null) {
            store.markUnresolved(loc.offset());
        }
    }

    /** Marks the record at the given location as contradicted. */
    public void markContradicted(MemoryLocation loc) {
        markContradicted(loc.type(), loc.offset());
    }

    /** Marks the record at the given memory type and byte offset as contradicted. */
    public void markContradicted(MemoryType type, long offset) {
        EngramRegion store = stores.get(type);
        if (store != null) {
            store.markContradicted(offset);
        }
    }

    /**
     * Returns {@code true} if the record at the given location has the tombstone flag set.
     */
    public boolean isTombstoned(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            return episodicStore != null && episodicStore.isTombstoned(loc.offset());
        }
        EngramRegion store = stores.get(loc.type());
        return store != null && store.isTombstoned(loc.offset());
    }

    /**
     * Reads the decoded encoding header for the record at the given location.
     */
    public EncodingHeader readHeader(MemoryLocation loc) {
        CognitiveRecordBody body = readRecordBody(loc, false);
        return body != null ? body.header() : null;
    }

    /**
     * Reads the encoding header flags for the record at the given location.
     */
    public byte readFlags(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            return episodicStore != null ? episodicStore.readFlags(loc.offset()) : 0;
        }
        EngramRegion store = stores.get(loc.type());
        return store != null ? store.readFlags(loc.offset()) : 0;
    }

    /**
     * Reads the quantized vector payload for the record at the given location,
     * or null if not present or unsupported.
     */
    public byte[] readVector(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            return (episodicStore != null && episodicStore.isFixedRecordLayout())
                    ? episodicStore.readVector(loc.offset())
                    : null;
        }
        EngramRegion store = stores.get(loc.type());
        return store != null ? store.readVector(loc.offset()) : null;
    }

    /**
     * Writes the last recall profile ordinal for the record at the given location.
     */
    public void writeLastRecallProfile(MemoryLocation loc, byte profileOrdinal) {
        if (strengthStore != null && loc.type() != MemoryType.WORKING && layoutFor(loc.type()) != null) {
            EngramRegion store = stores.get(loc.type());
            if (store != null) {
                int slotIndex = (int) ((loc.offset() - store.dataOffset()) / layoutFor(loc.type()).stride());
                long strengthOff = strengthStore.strengthOffset(loc.type(), slotIndex);
                StrengthLayout.INSTANCE.writeLastRecallProfile(strengthStore.segment(), strengthOff, profileOrdinal);
            }
        } else {
            EngramRegion store = stores.get(loc.type());
            if (store instanceof AbstractEngramMemory<?> aem) {
                aem.writeLastRecallProfile(loc.offset(), profileOrdinal);
            }
        }
    }

    /**
     * Reads the last recall profile ordinal for the record at the given location.
     */
    public byte readLastRecallProfile(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            return -1;
        }
        if (strengthStore != null && loc.type() != MemoryType.WORKING && layoutFor(loc.type()) != null) {
            EngramRegion store = stores.get(loc.type());
            if (store != null) {
                int slotIndex = (int) ((loc.offset() - store.dataOffset()) / layoutFor(loc.type()).stride());
                return strengthStore.readLastRecallProfile(loc.type(), slotIndex);
            }
        }
        EngramRegion store = stores.get(loc.type());
        if (store instanceof AbstractEngramMemory<?> aem) {
            return aem.readLastRecallProfile(loc.offset());
        }
        return -1;
    }

    /**
     * Reinforces a cognitive memory record (valence, LTP, ACT-R, and two-factor storage strength).
     */
    public void reinforce(MemoryLocation loc, byte valence, float learningRate, float sGain, float sMax) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore != null) {
                episodicStore.reinforceValence(loc.offset(), valence, learningRate);
            }
            return;
        }
        EngramRegion store = stores.get(loc.type());
        if (store instanceof AbstractEngramMemory<?> abstractStore) {
            FixedEngramLayout layout = layoutFor(loc.type());
            if (layout == null) return;

            abstractStore.reinforceValence(loc.offset(), valence, learningRate);

            int slotIndex = (int) ((loc.offset() - abstractStore.dataOffset()) / layout.stride());
            long creationTs = abstractStore.readTimestamp(loc.offset());
            long nowMs = System.currentTimeMillis();

            if (strengthStore != null && loc.type() != MemoryType.WORKING) {
                strengthStore.incrementAgentRecallCount(loc.type(), slotIndex);
                strengthStore.recordRecall(loc.type(), slotIndex, creationTs, nowMs, (byte) 0, 0);

                int rawBucket = DecayStrategy.ageToBucket(creationTs, nowMs);
                float currentR = DecayStrategy.decay(rawBucket);
                float deltaS = sGain * (1.0f - currentR);
                strengthStore.casStorageStrength(loc.type(), slotIndex,
                        currentS -> Math.min(sMax,
                                Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MIN, currentS + deltaS)));
            } else {
                abstractStore.reinforceInSitu(loc.offset(), creationTs, nowMs, sGain, sMax);
            }
        }
    }

    /**
     * Reads the importance value for the record at the given location.
     */
    public float readImportance(MemoryLocation loc) {
        if (loc.type() == MemoryType.EPISODIC) {
            return episodicStore != null ? episodicStore.readImportance(loc.offset()) : 0f;
        }
        EngramRegion store = stores.get(loc.type());
        return store instanceof AbstractEngramMemory<?> abstractStore ? abstractStore.readImportance(loc.offset()) : 0f;
    }

    /**
     * Atomically updates importance for the record at the given location, updating
     * effective importance in the strength store if present.
     */
    public float casImportance(MemoryLocation loc, FloatUnaryOperator updateOp) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore == null) return 0f;
            float oldVal = episodicStore.readImportance(loc.offset());
            float newVal = updateOp.applyAsFloat(oldVal);
            episodicStore.writeImportance(loc.offset(), newVal);
            return newVal;
        }
        EngramRegion store = stores.get(loc.type());
        if (!(store instanceof AbstractEngramMemory<?> abstractStore)) return 0f;
        FixedEngramLayout layout = layoutFor(loc.type());
        if (layout == null) return 0f;

        float oldImportance = abstractStore.readImportance(loc.offset());
        float finalImportance = abstractStore.casImportance(loc.offset(), updateOp);
        if (Math.abs(finalImportance - oldImportance) > 0.001f) {
            if (strengthStore != null && loc.type() != MemoryType.WORKING) {
                int slotIndex = (int) ((loc.offset() - abstractStore.dataOffset()) / layout.stride());
                strengthStore.casEffectiveImportance(loc.type(), slotIndex, current -> finalImportance);
            }
        }
        return finalImportance;
    }

    /**
     * Reads the cognitive record body (header, extended fields, and optionally the
     * quantized vector) for the record at the given location from a single store snapshot.
     */
    public CognitiveRecordBody readRecordBody(MemoryLocation loc, boolean includeVector) {
        if (loc.type() == MemoryType.EPISODIC) {
            if (episodicStore == null) return null;
            EncodingHeader h = episodicStore.readHeader(loc.offset());
            if (h == null) return null;
            byte[] quantizedVec = null;
            if (includeVector && episodicStore.isFixedRecordLayout()) {
                quantizedVec = episodicStore.readVector(loc.offset());
            }
            return new CognitiveRecordBody(h, quantizedVec, 0, (byte) 0);
        }
        FixedEngramLayout layout = layoutFor(loc.type());
        EngramRegion store = stores.get(loc.type());
        if (layout == null || store == null) return null;

        long offset = loc.offset();
        EncodingHeader header = store.readHeader(offset);
        if (header == null) return null;

        if (strengthStore != null && loc.type() != MemoryType.WORKING) {
            int slotIndex = (int) ((offset - store.dataOffset()) / layout.stride());
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
            quantizedVec = store.readVector(offset);
        }

        int spectorRecallCount = (strengthStore != null && loc.type() != MemoryType.WORKING)
                ? strengthStore.readSpectorRecallCount(loc.type(), (int) ((offset - store.dataOffset()) / layout.stride()))
                : header.agentRecallCount();
        byte consolidationFlags = header.consolidationFlags();
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

    /**
     * Backward-compatible alias for {@link #episodic()}.
     *
     * @return the episodic memory store
     * @deprecated Use {@link #episodic()} instead.
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    public EpisodicMemory episodicLog() { return episodic(); }

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
     * Used by {@code CheckpointEngine} before recording a WAL checkpoint.
     */
    public void forceAll() {
        for (EngramRegion store : stores.values()) {
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
