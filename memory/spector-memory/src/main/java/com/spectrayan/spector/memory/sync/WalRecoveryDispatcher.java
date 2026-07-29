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
package com.spectrayan.spector.memory.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.shape.RecordMemory;
import com.spectrayan.spector.memory.kernel.shape.AppendMemory;
import com.spectrayan.spector.memory.kernel.shape.RegistryMemory;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCsr;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

/**
 * Replays shape-specific Write-Ahead Log (WAL) events and dispatches mutations
 * directly to target Memory segments.
 */
public final class WalRecoveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WalRecoveryDispatcher.class);

    private WalRecoveryDispatcher() {}

    /**
     * Replays all mutations from the WAL that occurred after the last completed checkpoint.
     *
     * @param wal      the write-ahead log to read from
     * @param memories mapping of target memory IDs to active open Memory instances
     */
    public static void recover(MemoryWal wal, Map<MemoryId, Memory<?>> memories) {
        List<WalEvent> allEvents = wal.replay(0);
        if (allEvents.isEmpty()) {
            log.info("WAL recovery: no events to replay");
            return;
        }

        // Find the last SNAPSHOT_MARK sequence number by scanning backwards
        long lastSnapshotSeq = -1;
        for (int i = allEvents.size() - 1; i >= 0; i--) {
            WalEvent event = allEvents.get(i);
            if (event.type() == WalEvent.EventType.SNAPSHOT_MARK) {
                lastSnapshotSeq = event.sequence();
                log.info("WAL recovery: found last SNAPSHOT_MARK at sequence {}", lastSnapshotSeq);
                break;
            }
        }

        long startSeq = lastSnapshotSeq;
        int replayCount = 0;

        // Bypass WAL logging on all active memory targets throughout recovery
        for (Memory<?> memory : memories.values()) {
            memory.setBypassWal(true);
        }

        try {
            for (WalEvent event : allEvents) {
                if (event.sequence() <= startSeq) {
                    continue; // Skip events that are already checkpointed
                }

                // Resolve target Memory instance
                MemoryId targetId = MemoryId.parse(event.memoryId());
                Memory<?> target = memories.get(targetId);
                if (target == null) {
                    // If it's a legacy or unmapped event, skip or log warning
                    if (event.type() != WalEvent.EventType.REFLECT &&
                        event.type() != WalEvent.EventType.TAG_MERGE &&
                        event.type() != WalEvent.EventType.RECALL_HIT) {
                        log.warn("WAL recovery: no active Memory found for ID '{}', skipping event type {}", event.memoryId(), event.type());
                    }
                    continue;
                }

                ByteBuffer payload = ByteBuffer.wrap(event.payload());
                try {
                    switch (event.type()) {
                        case RECORD_WRITE -> {
                            long recordId = payload.getLong();
                            byte[] recordBytes = new byte[event.payload().length - 8];
                            payload.get(recordBytes);
                            ((RecordMemory<?>) target).write(recordId, MemorySegment.ofArray(recordBytes));
                        }
                        case APPEND -> {
                            ((AppendMemory<?>) target).append(MemorySegment.ofArray(event.payload()));
                        }
                        case REGISTRY_INTERN -> {
                            int id = payload.getInt();
                            String name = new String(event.payload(), 4, event.payload().length - 4, StandardCharsets.UTF_8);
                            ((RegistryMemory) target).putDirect(name, id);
                        }
                        case ADJ_ADD_EDGE -> {
                            int from = payload.getInt();
                            int to = payload.getInt();
                            if (target instanceof EntityGraph eg) {
                                String relType = new String(event.payload(), 8, event.payload().length - 8, StandardCharsets.UTF_8);
                                eg.addRelation(from, to, relType);
                            } else if (target instanceof HebbianGraphCsr hg) {
                                float weightDelta = payload.getFloat();
                                hg.strengthen(from, to, weightDelta);
                            }
                        }
                        case GRAPH_ADD_NODE -> {
                            int nodeId = payload.getInt();
                            int nameLen = payload.getInt();
                            String name = new String(event.payload(), 8, nameLen, StandardCharsets.UTF_8);
                            int typeLen = payload.getInt(8 + nameLen);
                            String type = new String(event.payload(), 8 + nameLen + 4, typeLen, StandardCharsets.UTF_8);
                            if (target instanceof EntityGraph eg) {
                                eg.addEntity(name, type);
                            }
                        }
                        case GRAPH_LINK_MEMORY -> {
                            int entityId = payload.getInt();
                            int memoryIdx = payload.getInt();
                            if (target instanceof EntityGraph eg) {
                                eg.linkEntityToMemory(entityId, memoryIdx);
                            }
                        }
                        case CHAIN_LINK -> {
                            int fromIdx = payload.getInt();
                            int toIdx = payload.getInt();
                            int sessionId = payload.getInt();
                            if (target instanceof TemporalChainMemory tc) {
                                tc.link(fromIdx, toIdx, sessionId);
                            }
                        }
                        default -> {
                            // informational/legacy events ignored during direct recovery
                        }
                    }
                    replayCount++;
                } catch (Exception e) {
                    log.error("WAL recovery: failed to replay event seq={}, type={} on memory '{}': {}", 
                            event.sequence(), event.type(), event.memoryId(), e.getMessage(), e);
                }
            }
        } finally {
            // Re-enable WAL logging across all memories after recovery finishes
            for (Memory<?> memory : memories.values()) {
                memory.setBypassWal(false);
            }
        }

        log.info("WAL recovery: successfully replayed {} events", replayCount);
    }
}
