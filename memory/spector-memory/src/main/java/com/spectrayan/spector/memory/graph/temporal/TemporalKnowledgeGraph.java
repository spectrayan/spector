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
package com.spectrayan.spector.memory.graph.temporal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32C;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.memory.kernel.shape.DefaultAppendMemory;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.graph.temporal.index.SubjectIndex;
import com.spectrayan.spector.memory.graph.temporal.index.ValidTimeIndex;

/**
 * Bitemporal knowledge graph storing structured facts with temporal validity.
 *
 * <h3>Biological Analog: Declarative Memory with Temporal Context</h3>
 * <p>The brain's semantic memory stores facts like "Alice works at Acme" as
 * declarative knowledge. Unlike raw episodic memories (captured by
 * {@link TemporalChainMemory}), these facts have explicit validity windows — you
 * know Alice worked at Acme from 2023 to 2025, not just that you learned
 * it at some point. The TKG captures this temporal dimension of knowledge.</p>
 *
 * <h3>Storage Architecture</h3>
 * <ul>
 *   <li>Durable storage: {@link DefaultAppendMemory} with 64-byte
 *       {@link TemporalFactLayout} records — append-only, WAL-protected</li>
 *   <li>In-memory subject index: entity ID → fact offsets (rebuilt on open)</li>
 *   <li>In-memory valid-time index: epoch millis → fact offsets (rebuilt on open)</li>
 *   <li>Predicate interning via existing {@link TypeRegistryMemory}</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>All mutation methods ({@link #assertFact}, {@link #retractFact}) are
 * guarded by a {@link ReentrantLock}. Query methods are lock-free and
 * operate on snapshot-consistent in-memory indexes.</p>
 *
 * @see TemporalFact
 * @see TemporalChainMemory
 * @see ContradictionResolver
 */
public final class TemporalKnowledgeGraph implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporalKnowledgeGraph.class);

    /** Memory ID for WAL registration and recovery. */
    private static final MemoryId MEMORY_ID = SystemMemoryId.TEMPORAL_FACTS.id();

    /** Default initial file size: 64 KB (room for ~1000 facts). */
    private static final long DEFAULT_INITIAL_SIZE = 64L * 1024;

    /** The 64-byte record layout. */
    private static final TemporalFactLayout LAYOUT = new TemporalFactLayout();

    // ── Storage ──
    private final TemporalFactsAppendMemory factLog;

    // ── In-memory indexes (rebuilt on open) ──
    private final SubjectIndex subjectIndex = new SubjectIndex();
    private final ValidTimeIndex validTimeIndex = new ValidTimeIndex();

    // ── Predicate interning ──
    private final TypeRegistryMemory predicateRegistry;

    // ── Contradiction resolution ──
    private final ContradictionResolver resolver;

    // ── Fact ID generator (monotonic) ──
    private int nextFactId;

    // ── Retraction cache ──
    private final Set<Integer> retractedCache = ConcurrentHashMap.newKeySet();

    // ── Concurrency ──
    private final ReentrantLock writeLock = new ReentrantLock();

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a heap-backed in-memory TKG with default contradiction resolver.
     *
     * @param predicateRegistry type registry for predicate name interning
     */
    public TemporalKnowledgeGraph(TypeRegistryMemory predicateRegistry) {
        this(predicateRegistry, new LatestTxWinsResolver());
    }

    /**
     * Creates a heap-backed in-memory TKG with a custom contradiction resolver.
     *
     * @param predicateRegistry type registry for predicate name interning
     * @param resolver          contradiction resolution strategy
     */
    public TemporalKnowledgeGraph(TypeRegistryMemory predicateRegistry,
                                   ContradictionResolver resolver) {
        this.factLog = new TemporalFactsAppendMemory(DEFAULT_INITIAL_SIZE);
        this.predicateRegistry = predicateRegistry;
        this.resolver = resolver;
        this.nextFactId = 1;
        rebuildIndexes();
    }

    /**
     * Creates a file-backed TKG with default contradiction resolver.
     *
     * @param filePath          path to the temporal-facts.tfacts file
     * @param initialSize       initial file size in bytes
     * @param predicateRegistry type registry for predicate name interning
     */
    public TemporalKnowledgeGraph(Path filePath, long initialSize,
                                   TypeRegistryMemory predicateRegistry) {
        this(filePath, initialSize, predicateRegistry, new LatestTxWinsResolver());
    }

    /**
     * Creates a file-backed TKG with a custom contradiction resolver.
     *
     * @param filePath          path to the temporal-facts.tfacts file
     * @param initialSize       initial file size in bytes
     * @param predicateRegistry type registry for predicate name interning
     * @param resolver          contradiction resolution strategy
     */
    public TemporalKnowledgeGraph(Path filePath, long initialSize,
                                   TypeRegistryMemory predicateRegistry,
                                   ContradictionResolver resolver) {
        this.factLog = new TemporalFactsAppendMemory(filePath, initialSize);
        this.predicateRegistry = predicateRegistry;
        this.resolver = resolver;
        this.nextFactId = 1;
        rebuildIndexes();
    }

    /**
     * Creates a bundle-backed TemporalKnowledgeGraph.
     */
    public static TemporalKnowledgeGraph fromBundle(TypeRegistryMemory predicateRegistry,
                                                    Arena arena, MemorySegment regionSlice,
                                                    Path bundlePath, boolean isNew) {
        return new TemporalKnowledgeGraph(predicateRegistry, arena, regionSlice, bundlePath, isNew);
    }

    private TemporalKnowledgeGraph(TypeRegistryMemory predicateRegistry,
                                   Arena arena, MemorySegment regionSlice,
                                   Path bundlePath, boolean isNew) {
        this.factLog = TemporalFactsAppendMemory.fromBundle(arena, regionSlice, bundlePath, isNew);
        this.predicateRegistry = predicateRegistry;
        this.resolver = new LatestTxWinsResolver();
        this.nextFactId = 1;

        if (isNew && bundlePath != null) {
            Path legacyPath = bundlePath.resolveSibling("temporal-facts.tfacts");
            if (java.nio.file.Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone temporal-facts.tfacts to bundle region...");
                try {
                    TemporalKnowledgeGraph legacy = new TemporalKnowledgeGraph(legacyPath, java.nio.file.Files.size(legacyPath) - com.spectrayan.spector.memory.kernel.RegionPreamble.PREAMBLE_BYTES, predicateRegistry);
                    long factCount = legacy.factLog.size();
                    for (long i = 0; i < factCount; i++) {
                        MemorySegment factSeg = legacy.factLog.read(i * 64, 64);
                        this.factLog.append(factSeg);
                    }
                    this.factLog.flush();
                    legacy.close();
                    java.nio.file.Files.deleteIfExists(legacyPath);
                } catch (Exception e) {
                    log.warn("Failed to migrate legacy temporal-facts.tfacts: {}", e.getMessage());
                }
            }
        }
        rebuildIndexes();
    }

    // ═══════════════════════════════════════════════════════════════
    // MUTATION — Assert / Retract
    // ═══════════════════════════════════════════════════════════════

    /**
     * Asserts a new temporal fact into the knowledge graph.
     *
     * <p>The fact is durably appended to the fact log, then indexed in
     * the subject and valid-time indexes for fast query evaluation.</p>
     *
     * @param subjectEntityId  entity ID of the subject (FK → EntityGraph)
     * @param predicateName    predicate name (interned via TypeRegistryMemory)
     * @param objectEntityId   entity ID of the object, or -1 for literal values
     * @param objectTextOffset text offset in TextDataStore, or -1 for entity objects
     * @param objectTextLength text length (max 32KB), or 0 for entity objects
     * @param validFrom        epoch millis when the fact becomes valid (inclusive)
     * @param validTo          epoch millis when the fact stops being valid (exclusive),
     *                         use {@code Long.MAX_VALUE} for ongoing facts
     * @param confidence       confidence score [0.0, 1.0]
     * @param inferred         true if this fact was LLM-inferred (not user-stated)
     * @return the assigned fact ID (monotonically increasing)
     */
    public int assertFact(int subjectEntityId, String predicateName,
                           int objectEntityId, long objectTextOffset,
                           short objectTextLength,
                           long validFrom, long validTo,
                           float confidence, boolean inferred) {
        writeLock.lock();
        try {
            return assertFactInternal(subjectEntityId, predicateName, objectEntityId,
                    objectTextOffset, objectTextLength, validFrom, validTo,
                    confidence, inferred);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retracts a previously asserted fact by appending a retraction record.
     *
     * <p>The original fact is not mutated or deleted. Instead, a new retraction
     * record is appended with {@code retractsFactId} pointing to the original.
     * Query evaluation filters out retracted facts at resolution time.</p>
     *
     * @param factIdToRetract the factId of the fact to retract
     * @return the factId of the retraction record
     */
    public int retractFact(int factIdToRetract) {
        writeLock.lock();
        try {
            return retractFactInternal(factIdToRetract);
        } finally {
            writeLock.unlock();
        }
    }

    // ── Internal mutation methods (caller MUST hold writeLock) ──

    /**
     * Core fact assertion — caller MUST hold {@code writeLock}.
     */
    private int assertFactInternal(int subjectEntityId, String predicateName,
                                    int objectEntityId, long objectTextOffset,
                                    short objectTextLength,
                                    long validFrom, long validTo,
                                    float confidence, boolean inferred) {
        int predicateId = predicateRegistry.getOrRegister(predicateName);
        int factId = nextFactId++;
        long txTime = System.currentTimeMillis();

        byte flags = inferred ? TemporalFactLayout.FLAG_INFERRED : 0;

        MemorySegment segment = writeFactSegment(
                factId, subjectEntityId, predicateId, objectEntityId,
                objectTextOffset, objectTextLength, flags,
                validFrom, validTo, txTime, confidence,
                TemporalFact.SENTINEL_FACT);

        long offset = factLog.append(segment);
        subjectIndex.add(subjectEntityId, offset);
        validTimeIndex.add(validFrom, offset);

        log.debug("TKG: asserted factId={} subject={} pred={} offset={}",
                factId, subjectEntityId, predicateName, offset);
        return factId;
    }

    /**
     * Core fact retraction — caller MUST hold {@code writeLock}.
     */
    private int retractFactInternal(int factIdToRetract) {
        int factId = nextFactId++;
        long txTime = System.currentTimeMillis();

        MemorySegment segment = writeFactSegment(
                factId, 0, 0, TemporalFact.SENTINEL_ENTITY,
                -1L, (short) 0, (byte) 0,
                0L, 0L, txTime, 0f,
                factIdToRetract);

        factLog.append(segment);
        retractedCache.add(factIdToRetract);

        log.debug("TKG: retracted factId={} (retraction={})", factIdToRetract, factId);
        return factId;
    }

    /**
     * Asserts a new fact with automatic supersession of prior active facts.
     *
     * <p>When {@code allowCoexisting} is {@code false} (the recommended default),
     * any existing active (non-retracted) fact with the same ({@code subjectEntityId},
     * {@code predicateName}) pair is automatically retracted before the new fact
     * is appended. This creates a linked supersession chain where each retraction
     * record's {@code retractsFactId} points to the superseded fact.</p>
     *
     * <p>When {@code allowCoexisting} is {@code true}, the new fact is appended
     * without retracting prior facts — useful for multi-valued predicates like
     * {@code speaks_language} or {@code has_skill} where multiple values are
     * simultaneously valid.</p>
     *
     * <h3>Example — Auto-Supersession</h3>
     * <pre>{@code
     *   // First assertion: Alice works at Meta
     *   tkg.assertFactWithSupersession(aliceId, "works_at", metaId,
     *       -1L, (short) 0, validFrom, Long.MAX_VALUE, 0.9f, false, false);
     *
     *   // Second assertion: Alice now works at Google — Meta fact auto-retracted
     *   tkg.assertFactWithSupersession(aliceId, "works_at", googleId,
     *       -1L, (short) 0, validFrom2, Long.MAX_VALUE, 0.95f, false, false);
     *
     *   // History preserved: factHistory(aliceId, "works_at") returns both versions
     * }</pre>
     *
     * @param subjectEntityId  entity ID of the subject
     * @param predicateName    predicate name (interned via TypeRegistryMemory)
     * @param objectEntityId   entity ID of the object, or -1 for literal values
     * @param objectTextOffset text offset in TextDataStore, or -1 for entity objects
     * @param objectTextLength text length, or 0 for entity objects
     * @param validFrom        epoch millis when the fact becomes valid (inclusive)
     * @param validTo          epoch millis when the fact stops being valid (exclusive)
     * @param confidence       confidence score [0.0, 1.0]
     * @param inferred         true if this fact was LLM-inferred
     * @param allowCoexisting  if true, skip auto-retraction (multi-valued predicates)
     * @return the assigned fact ID
     */
    public int assertFactWithSupersession(int subjectEntityId, String predicateName,
                                           int objectEntityId, long objectTextOffset,
                                           short objectTextLength,
                                           long validFrom, long validTo,
                                           float confidence, boolean inferred,
                                           boolean allowCoexisting) {
        writeLock.lock();
        try {
            if (!allowCoexisting) {
                int predicateId = predicateRegistry.getOrRegister(predicateName);
                Set<Integer> retracted = retractedFactIds();
                List<TemporalFact> existing = readFactsForEntity(subjectEntityId).stream()
                        .filter(f -> f.predicateId() == predicateId
                                && !f.isRetraction()
                                && !retracted.contains(f.factId()))
                        .toList();

                for (TemporalFact prior : existing) {
                    log.debug("TKG: auto-superseding factId={} (subject={}, pred={})",
                            prior.factId(), subjectEntityId, predicateName);
                    retractFactInternal(prior.factId());
                }
            }

            return assertFactInternal(subjectEntityId, predicateName, objectEntityId,
                    objectTextOffset, objectTextLength, validFrom, validTo,
                    confidence, inferred);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the complete supersession chain for a (subject, predicate) pair.
     *
     * <p>Returns all fact versions — active, retracted, and retraction records —
     * ordered by transaction time (newest first). Callers can reconstruct the
     * full history of how a fact evolved over time.</p>
     *
     * @param subjectEntityId the subject entity ID
     * @param predicateId     the predicate ID (from {@link TypeRegistryMemory})
     * @return all facts matching the (subject, predicate) pair, newest-first
     */
    public List<TemporalFact> factHistory(int subjectEntityId, int predicateId) {
        return readFactsForEntity(subjectEntityId).stream()
                .filter(f -> f.predicateId() == predicateId && !f.isRetraction())
                .sorted(java.util.Comparator.comparingLong(TemporalFact::txTime).reversed())
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERY — factsAbout / readFact
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a fluent query builder scoped to facts about the given entity.
     *
     * @param entityId the subject entity ID to query
     * @return a new query builder
     */
    public TemporalQuery factsAbout(int entityId) {
        return new TemporalQuery(this, entityId);
    }

    /**
     * Reads all facts for a given entity from the fact log.
     *
     * @param entityId the subject entity ID
     * @return list of temporal facts (may include retracted facts)
     */
    public List<TemporalFact> readFactsForEntity(int entityId) {
        List<Long> offsets = subjectIndex.offsetsFor(entityId);
        List<TemporalFact> facts = new ArrayList<>(offsets.size());
        for (long offset : offsets) {
            MemorySegment seg = factLog.read(offset, 64);
            facts.add(TemporalFact.readFrom(seg, 0, LAYOUT));
        }
        return facts;
    }

    /**
     * Returns the set of fact IDs that have been retracted.
     *
     * <p>This scans the entire fact log for retraction records. For large
     * fact stores, consider caching this set.</p>
     *
     * @return set of retracted fact IDs
     */
    public Set<Integer> retractedFactIds() {
        return Collections.unmodifiableSet(retractedCache);
    }

    /**
     * Returns the contradiction resolver for this TKG.
     *
     * @return the resolver
     */
    ContradictionResolver resolver() {
        return resolver;
    }

    /**
     * Returns the predicate registry for name resolution.
     *
     * @return the predicate type registry
     */
    public TypeRegistryMemory predicateRegistry() {
        return predicateRegistry;
    }

    // ═══════════════════════════════════════════════════════════════
    // METRICS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the total number of indexed (non-retraction) facts.
     *
     * @return fact count
     */
    public int factCount() {
        return subjectIndex.totalFacts();
    }

    /**
     * Returns the number of distinct entities with facts.
     *
     * @return entity count
     */
    public int entityCount() {
        return subjectIndex.entityCount();
    }

    /**
     * Returns the append cursor position (total bytes written).
     *
     * @return cursor position in bytes
     */
    public long appendCursor() {
        return factLog.appendCursor();
    }

    // ═══════════════════════════════════════════════════════════════
    // INDEX REBUILD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Rebuilds the in-memory indexes from the durable fact log.
     *
     * <p>Called on construction to restore indexes from persisted data.
     * Performs a single linear scan of the append log, reading each
     * length-prefixed record via {@link DefaultAppendMemory#replay(long)}.</p>
     */
    public void rebuildIndexes() {
        subjectIndex.clear();
        validTimeIndex.clear();
        retractedCache.clear();

        int maxFactId = 0;
        int factCount = 0;
        long currentOffset = 0;

        Iterator<MemorySegment> it = factLog.replay(0);
        while (it.hasNext()) {
            MemorySegment seg = it.next();
            long segSize = seg.byteSize();
            if (segSize < 64) {
                log.warn("TKG: skipping truncated record at offset {} (size={})",
                        currentOffset, segSize);
                currentOffset += 4 + segSize; // 4B length prefix + payload
                continue;
            }

            int factId = seg.get(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_FACT_ID);
            int retractsFactId = seg.get(ValueLayout.JAVA_INT_UNALIGNED,
                    TemporalFactLayout.OFF_RETRACTS_FACT_ID);

            if (retractsFactId == TemporalFact.SENTINEL_FACT) {
                // Normal fact — index it
                int subjectId = seg.get(ValueLayout.JAVA_INT_UNALIGNED,
                        TemporalFactLayout.OFF_SUBJECT_ENTITY_ID);
                long validFrom = seg.get(ValueLayout.JAVA_LONG_UNALIGNED,
                        TemporalFactLayout.OFF_VALID_FROM);

                // Offset in the read() coordinate space = currentOffset (after length prefix)
                subjectIndex.add(subjectId, currentOffset + 4);
                validTimeIndex.add(validFrom, currentOffset + 4);
                factCount++;
            } else {
                retractedCache.add(retractsFactId);
            }

            if (factId > maxFactId) {
                maxFactId = factId;
            }

            currentOffset += 4 + segSize; // 4B length prefix + payload
        }

        nextFactId = maxFactId + 1;
        log.info("TKG: rebuilt indexes — {} facts indexed, {} entities, nextFactId={}",
                factCount, subjectIndex.entityCount(), nextFactId);
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Binds a Write-Ahead Log for durability.
     *
     * @param wal the WAL instance
     */
    public void bindWal(MemoryWal wal) {
        factLog.bindWal(wal);
    }

    /**
     * Returns the underlying append memory for WAL recovery registration.
     *
     * @return the fact log memory
     */
    public TemporalFactsAppendMemory backing() {
        return factLog;
    }

    /**
     * Returns the memory ID for this TKG.
     *
     * @return the memory ID
     */
    public MemoryId id() {
        return MEMORY_ID;
    }

    @Override
    public void close() throws Exception {
        factLog.close();
    }

    /**
     * Flushes the knowledge graph to durable storage.
     */
    public void flush() {
        factLog.flush();
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Writes a 64-byte fact record to a fresh MemorySegment with CRC32C.
     */
    private MemorySegment writeFactSegment(
            int factId, int subjectEntityId, int predicateId, int objectEntityId,
            long objectTextOffset, short objectTextLength, byte flags,
            long validFrom, long validTo, long txTime, float confidence,
            int retractsFactId) {

        MemorySegment seg = MemorySegment.ofArray(new byte[64]);

        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_FACT_ID, factId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_SUBJECT_ENTITY_ID, subjectEntityId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_PREDICATE_ID, predicateId);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_OBJECT_ENTITY_ID, objectEntityId);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, TemporalFactLayout.OFF_OBJECT_TEXT_OFFSET, objectTextOffset);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, TemporalFactLayout.OFF_OBJECT_TEXT_LENGTH, objectTextLength);
        seg.set(ValueLayout.JAVA_BYTE, TemporalFactLayout.OFF_FLAGS, flags);
        seg.set(ValueLayout.JAVA_BYTE, TemporalFactLayout.OFF_RESERVED, (byte) 0);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, TemporalFactLayout.OFF_VALID_FROM, validFrom);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, TemporalFactLayout.OFF_VALID_TO, validTo);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, TemporalFactLayout.OFF_TX_TIME, txTime);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, TemporalFactLayout.OFF_CONFIDENCE, confidence);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_RETRACTS_FACT_ID, retractsFactId);

        // CRC32C over entire 64 bytes with CRC32C field zeroed out
        CRC32C crc = new CRC32C();
        byte[] recordBytes = new byte[64];
        MemorySegment.copy(seg, 0, MemorySegment.ofArray(recordBytes), 0, 64);
        recordBytes[56] = 0;
        recordBytes[57] = 0;
        recordBytes[58] = 0;
        recordBytes[59] = 0;
        crc.update(recordBytes);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_CRC32C, (int) crc.getValue());

        return seg;
    }
}
