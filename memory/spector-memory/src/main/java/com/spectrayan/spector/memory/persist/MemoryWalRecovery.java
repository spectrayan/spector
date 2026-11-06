/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.persist;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;

import com.spectrayan.spector.memory.bootstrap.SpectorMemoryFactory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.error.SpectorWalCorruptionException;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.shape.AppendMemory;
import com.spectrayan.spector.kernel.shape.RecordMemory;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.CheckpointEngine;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;

import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.memory.error.SpectorWalCorruptionException;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.CheckpointEngine;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the write-ahead log into freshly-assembled memory subsystems on
 * startup, dispatching shape mutations to their target segments and rebuilding
 * the on-heap {@link MemoryIndex} maps from high-level REMEMBER/FORGET events.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.performWalRecovery} as
 * part of the #437 god-class decomposition. The recovery algorithm, checkpoint
 * HWM handling and event dispatch are unchanged.</p>
 *
 * @since 1.1.0
 */
public final class MemoryWalRecovery {

    private static final Logger log = LoggerFactory.getLogger(MemoryWalRecovery.class);

    private MemoryWalRecovery() {}

    public static void recover(
            MemoryWal wal,
            CognitiveMemoryRouter cognitiveRouter,
            MemoryIndex index,
            HebbianGraphBase hebbianGraph,
            TemporalChainMemory temporalChain,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            CoActivationMemory coActivationTracker,
            RememberPathway cognitiveTarget,
            Path basePath,
            int activePartitionSeq,
            com.spectrayan.spector.kernel.bundle.RegionRef checkpointRef) {

        if (wal == null || !wal.isPersistent()) {
            return;
        }

        long checkpointHwm = 0;
        boolean hasExplicitCheckpointFence = false;
        if (checkpointRef != null) {
            long hwm = checkpointRef.readCheckpointHwm();
            if (hwm > 0) {
                checkpointHwm = hwm;
                hasExplicitCheckpointFence = true;
                log.info("WAL recovery: loaded checkpoint HWM {} from bundle region", checkpointHwm);
            }
        }
        if (checkpointHwm == 0 && basePath != null) {
            Path metaPath = StoragePaths.runtimeDir(basePath).resolve("checkpoint.meta");
            if (Files.exists(metaPath)) {
                long fileHwm = CheckpointEngine.readCheckpointHwm(metaPath);
                if (fileHwm > 0) {
                    checkpointHwm = fileHwm;
                    hasExplicitCheckpointFence = true;
                    log.info("WAL recovery: loaded checkpoint HWM {}", checkpointHwm);
                }
            }
        }

        if (!hasExplicitCheckpointFence && checkpointRef == null) {
            List<WalEvent> allWalEvents = wal.replay(0);
            for (int i = allWalEvents.size() - 1; i >= 0; i--) {
                WalEvent ev = allWalEvents.get(i);
                if (ev.type() == WalEvent.EventType.SNAPSHOT_MARK) {
                    byte[] markPayload = ev.payload();
                    long markFence = (markPayload != null && markPayload.length >= Long.BYTES)
                            ? ByteBuffer.wrap(markPayload).getLong()
                            : ev.sequence();
                    if (markFence > checkpointHwm) {
                        checkpointHwm = markFence;
                    }
                    break;
                }
            }
        }

        List<WalEvent> events = wal.replay(checkpointHwm);
        if (events.isEmpty()) {
            log.info("WAL recovery: no events to replay after HWM {}", checkpointHwm);
            return;
        }

        log.info("WAL recovery: replaying {} events after HWM {}", events.size(), checkpointHwm);

        // 1. Gather all shape-specific Memory instances
        java.util.Map<MemoryId, Memory<?>> memories = new java.util.HashMap<>();

        if (cognitiveRouter != null) {
            if (cognitiveRouter.working() != null) {
                memories.put(cognitiveRouter.working().id(), cognitiveRouter.working());
            }
            if (cognitiveRouter.semantic() != null) {
                memories.put(cognitiveRouter.semantic().id(), cognitiveRouter.semantic());
            }
            if (cognitiveRouter.procedural() != null) {
                memories.put(cognitiveRouter.procedural().id(), cognitiveRouter.procedural());
            }
            if (cognitiveRouter.episodic() != null) {
                memories.put(cognitiveRouter.episodic().id(), cognitiveRouter.episodic());
            }
        }

        // ADR-0003 #456: the directory is the WAL-recovered identity store; GRAPH_ADD_NODE /
        // GRAPH_LINK_MEMORY events (emitted under the directory's id) dispatch here.
        if (entityDirectory != null) {
            memories.put(entityDirectory.id(), entityDirectory);
        }
        // ADR-0003 #460 / #417: the hypergraph is now WAL-recovered (HYPEREDGE_ADD replay).
        if (hyperEntityGraph != null) {
            memories.put(hyperEntityGraph.id(), hyperEntityGraph);
        }
        if (hebbianGraph instanceof HebbianGraphMemory hg) {
            memories.put(hg.id(), hg);
        }
        if (temporalChain != null) {
            memories.put(SystemMemoryId.TEMPORAL_CHAIN.id(), temporalChain);
        }
        if (temporalKnowledgeGraph != null) {
            memories.put(temporalKnowledgeGraph.id(), temporalKnowledgeGraph.backing());
        }

        // Add textDataStore memory if available
        MemoryId textId = SystemMemoryId.CORTEX_TEXT.id();
        if (index.textDataStore() != null) {
            memories.put(textId, index.textDataStore());
        }

        long countBeforeRecovery = 0;
        Memory<?> textMem = memories.get(textId);
        if (textMem instanceof com.spectrayan.spector.kernel.shape.AppendMemory<?> am) {
            countBeforeRecovery = am.appendCursor();
        }

        // 2. Dispatch shape mutations directly to target memory segments
        WalRecoveryDispatcher.recover(wal, memories, hasExplicitCheckpointFence ? checkpointHwm : -1L);

        // 3. Rebuild MemoryIndex on-heap maps from high-level REMEMBER/FORGET/REINFORCE events
        long lastRecordOffset = -1;
        MemoryType lastRecordType = null;
        long lastTextOffset = -1;
        int lastTextLength = -1;
        long currentTextCursor = countBeforeRecovery;

        for (WalEvent event : events) {
            if (event.sequence() <= checkpointHwm) {
                continue;
            }

            try {
                switch (event.type()) {
                    case RECORD_WRITE -> {
                        ByteBuffer buf = java.nio.ByteBuffer.wrap(event.payload());
                        long recordId = buf.getLong();
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        Memory<?> target = memories.get(targetId);
                        if (target instanceof com.spectrayan.spector.kernel.shape.RecordMemory<?> rm) {
                            lastRecordOffset = rm.recordOffset(recordId);
                            String pathName = targetId.memoryName();
                            try {
                                lastRecordType = MemoryType.valueOf(pathName.toUpperCase(java.util.Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                // Unknown or legacy record memory name
                            }
                        }
                    }
                    case APPEND -> {
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        if (SystemMemoryId.CORTEX_TEXT.id().equals(targetId)) {
                            lastTextOffset = currentTextCursor;
                            lastTextLength = event.payload().length;
                            currentTextCursor += 4 + lastTextLength;
                        }
                    }
                    case REMEMBER -> {
                        byte[] remPayload = event.payload();
                        if (remPayload != null
                                && remPayload.length >= 32
                                && ByteBuffer.wrap(remPayload).getInt(0) == com.spectrayan.spector.memory.pathway.pipeline.PostIngestSync.REMEMBER_WAL_MAGIC) {
                            replayRememberEnvelope(event.memoryId(), remPayload, cognitiveRouter, index, activePartitionSeq);
                        } else if (lastRecordOffset != -1 && lastRecordType != null) {
                            // EngramLayout has dynamic stride
                            int stride = 164; // default
                            MemoryId targetId = switch (lastRecordType) {
                                case WORKING -> SystemMemoryId.WORKING.id();
                                case SEMANTIC -> SystemMemoryId.SEMANTIC.id();
                                case PROCEDURAL -> SystemMemoryId.PROCEDURAL.id();
                                case EPISODIC -> SystemMemoryId.EPISODIC.id();
                            };
                            Memory<?> target = memories.get(targetId);
                            if (target != null) {
                                stride = target.layout().recordStride();
                            }
                            int storeIndex = (int) (lastRecordOffset / stride);
                            com.spectrayan.spector.kernel.api.MemoryLocation loc =
                                    new com.spectrayan.spector.kernel.api.MemoryLocation(
                                            lastRecordType, lastRecordOffset, -1, activePartitionSeq,
                                            lastTextOffset, lastTextLength);

                            String text = "";
                            if (index.textDataStore() != null && lastTextOffset != -1) {
                                try {
                                    text = index.textDataStore().readTextDirect(lastTextOffset, lastTextLength);
                                } catch (Exception e) {
                                    throw new SpectorWalCorruptionException("Failed to read text from DataStore during WAL index recovery", e);
                                }
                            }

                            index.register(event.memoryId(), loc, text, MemorySource.OBSERVED, new String[0]);
                        }
                    }
                    case FORGET -> {
                        // Keep the location: the forgotten bytes are still on disk until a purge.
                        index.retire(event.memoryId());
                    }
                    case PURGE -> {
                        // This case is load-bearing for erasure, not bookkeeping. WalRecoveryDispatcher
                        // replays RECORD_WRITE by rewriting the record's bytes from the log payload, so
                        // recovery has already restored the exact content the purge destroyed. Re-zeroing
                        // here is what keeps the erasure durable across a crash; without it the WAL is a
                        // resurrection vector.
                        replayPurge(event, memories);
                        index.remove(event.memoryId());
                        index.clearTombstone(event.memoryId());
                    }
                    case MARK_CONTRADICTED -> replayConsolidationFlag(event, memories, false);
                    case MARK_RETRACTED -> replayConsolidationFlag(event, memories, true);
                    default -> {}
                }
            } catch (Exception e) {
                throw new SpectorWalCorruptionException("WAL recovery index sync failed for event seq=" 
                        + event.sequence() + ", type=" + event.type(), e);
            }
        }
    }

    /**
     * Reapplies a purge during recovery, re-zeroing the record's payload.
     *
     * <p>Payload layout is {@code [tierOrdinal:4][recordOffset:8]}, written by
     * {@code MemoryWal.appendPurge}. The offset is carried in the event rather than resolved through the
     * index because the index is still being rebuilt at this point in recovery — looking it up would be a
     * chicken-and-egg dependency, and an unresolvable lookup would silently skip the erasure.</p>
     *
     * <p>A failure here is escalated by the caller rather than swallowed. If recovery cannot reapply an
     * erasure, the store has content on disk that an operator was told was destroyed; that must surface as a
     * corruption error, not a debug line.</p>
     */
    private static void replayPurge(WalEvent event,
                                    java.util.Map<MemoryId, com.spectrayan.spector.kernel.shape.Memory<?>> memories) {
        byte[] payload = event.payload();
        if (payload == null || payload.length < 12) {
            throw new SpectorWalCorruptionException(
                    "PURGE event for '" + event.memoryId() + "' has a malformed payload ("
                            + (payload == null ? "null" : payload.length + " bytes, expected 12")
                            + "); cannot reapply the erasure");
        }
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int tierOrdinal = buf.getInt();
        long recordOffset = buf.getLong();
        MemoryType[] tiers = MemoryType.values();
        if (tierOrdinal < 0 || tierOrdinal >= tiers.length) {
            throw new SpectorWalCorruptionException(
                    "PURGE event for '" + event.memoryId() + "' names tier ordinal " + tierOrdinal
                            + ", which is not a known tier; cannot reapply the erasure");
        }
        MemoryType tier = tiers[tierOrdinal];
        MemoryId targetId = switch (tier) {
            case WORKING -> SystemMemoryId.WORKING.id();
            case SEMANTIC -> SystemMemoryId.SEMANTIC.id();
            case PROCEDURAL -> SystemMemoryId.PROCEDURAL.id();
            case EPISODIC -> SystemMemoryId.EPISODIC.id();
        };
        com.spectrayan.spector.kernel.shape.Memory<?> target = memories.get(targetId);
        if (target instanceof com.spectrayan.spector.kernel.store.EngramRegion region) {
            int zeroed = region.purge(recordOffset);
            log.info("[WalRecovery] Reapplied purge of '{}' in {} at offset {} ({} payload bytes re-zeroed)",
                    event.memoryId(), tier, recordOffset, zeroed);
        } else if (target instanceof com.spectrayan.spector.kernel.store.EpisodicMemory episodic) {
            int zeroed = episodic.purge(recordOffset);
            log.info("[WalRecovery] Reapplied purge of '{}' in EPISODIC at offset {} ({} payload bytes "
                    + "re-zeroed)", event.memoryId(), recordOffset, zeroed);
        } else {
            throw new SpectorWalCorruptionException(
                    "PURGE event for '" + event.memoryId() + "' names tier " + tier
                            + ", but no engram region is mounted for it; the erasure cannot be reapplied and "
                            + "the record's content may still be on disk");
        }
    }

    /**
     * Reapplies a contradicted or retracted bit after {@code RECORD_WRITE} replay.
     *
     * <p>Payload layout matches purge: {@code [tierOrdinal:4][recordOffset:8]}. The dispatcher has
     * already rewritten the record from the ingest image, which does not carry a flag set later, so
     * this write is what makes the bit durable across a crash that skipped the region flush.</p>
     *
     * @param retracted {@code true} for {@code MARK_RETRACTED}, {@code false} for {@code MARK_CONTRADICTED}
     */
    private static void replayConsolidationFlag(WalEvent event,
                                                java.util.Map<MemoryId, com.spectrayan.spector.kernel.shape.Memory<?>> memories,
                                                boolean retracted) {
        String kind = retracted ? "MARK_RETRACTED" : "MARK_CONTRADICTED";
        byte[] payload = event.payload();
        if (payload == null || payload.length < 12) {
            throw new SpectorWalCorruptionException(
                    kind + " event for '" + event.memoryId() + "' has a malformed payload ("
                            + (payload == null ? "null" : payload.length + " bytes, expected 12")
                            + "); cannot reapply the flag");
        }
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int tierOrdinal = buf.getInt();
        long recordOffset = buf.getLong();
        MemoryType[] tiers = MemoryType.values();
        if (tierOrdinal < 0 || tierOrdinal >= tiers.length) {
            throw new SpectorWalCorruptionException(
                    kind + " event for '" + event.memoryId() + "' names tier ordinal " + tierOrdinal
                            + ", which is not a known tier; cannot reapply the flag");
        }
        MemoryType tier = tiers[tierOrdinal];
        MemoryId targetId = switch (tier) {
            case WORKING -> SystemMemoryId.WORKING.id();
            case SEMANTIC -> SystemMemoryId.SEMANTIC.id();
            case PROCEDURAL -> SystemMemoryId.PROCEDURAL.id();
            case EPISODIC -> SystemMemoryId.EPISODIC.id();
        };
        com.spectrayan.spector.kernel.shape.Memory<?> target = memories.get(targetId);
        if (target instanceof com.spectrayan.spector.kernel.store.EngramRegion region) {
            if (retracted) {
                region.markRetracted(recordOffset);
            } else {
                region.markContradicted(recordOffset);
            }
            log.info("[WalRecovery] Reapplied {} of '{}' in {} at offset {}",
                    kind, event.memoryId(), tier, recordOffset);
        } else {
            throw new SpectorWalCorruptionException(
                    kind + " event for '" + event.memoryId() + "' names tier " + tier
                            + ", but no engram region is mounted for it; the flag cannot be reapplied");
        }
    }

    private static void replayRememberEnvelope(
            String memoryId,
            byte[] payload,
            CognitiveMemoryRouter cognitiveRouter,
            MemoryIndex index,
            int fallbackPartitionSeq) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        int magic = buf.getInt();
        if (magic != com.spectrayan.spector.memory.pathway.pipeline.PostIngestSync.REMEMBER_WAL_MAGIC) {
            return;
        }
        int typeOrdinal = buf.getInt();
        long offset = buf.getLong();
        int graphSlot = buf.getInt();
        int colocatedPartition = buf.getInt();
        int sourceOrdinal = buf.getInt();

        int recordBytesLen = buf.getInt();
        byte[] recordBytes = new byte[Math.max(0, recordBytesLen)];
        if (recordBytesLen > 0) {
            buf.get(recordBytes);
        }

        int textBytesLen = buf.getInt();
        byte[] textBytes = new byte[Math.max(0, textBytesLen)];
        if (textBytesLen > 0) {
            buf.get(textBytes);
        }
        String text = new String(textBytes, java.nio.charset.StandardCharsets.UTF_8);

        int tagCount = buf.getInt();
        String[] tags = new String[Math.max(0, tagCount)];
        for (int i = 0; i < tagCount; i++) {
            int tLen = buf.getInt();
            byte[] tb = new byte[Math.max(0, tLen)];
            if (tLen > 0) {
                buf.get(tb);
            }
            tags[i] = new String(tb, java.nio.charset.StandardCharsets.UTF_8);
        }

        int metaCount = buf.getInt();
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>(Math.max(0, metaCount));
        for (int i = 0; i < metaCount; i++) {
            int kLen = buf.getInt();
            byte[] kb = new byte[Math.max(0, kLen)];
            if (kLen > 0) {
                buf.get(kb);
            }
            int vLen = buf.getInt();
            byte[] vb = new byte[Math.max(0, vLen)];
            if (vLen > 0) {
                buf.get(vb);
            }
            metadata.put(
                    new String(kb, java.nio.charset.StandardCharsets.UTF_8),
                    new String(vb, java.nio.charset.StandardCharsets.UTF_8));
        }

        MemoryType[] types = MemoryType.values();
        if (typeOrdinal < 0 || typeOrdinal >= types.length) {
            return;
        }
        MemoryType type = types[typeOrdinal];
        MemorySource[] sources = MemorySource.values();
        MemorySource source = (sourceOrdinal >= 0 && sourceOrdinal < sources.length)
                ? sources[sourceOrdinal]
                : MemorySource.OBSERVED;

        if (cognitiveRouter != null && type != MemoryType.EPISODIC && offset >= 0 && recordBytes.length > 0) {
            var store = cognitiveRouter.get(type);
            if (store instanceof RecordMemory<?> rm) {
                int stride = store.layout().recordStride();
                if (recordBytes.length == stride && offset >= store.dataOffset()) {
                    long recordId = (offset - store.dataOffset()) / stride;
                    boolean oldBypass = rm.isBypassWal();
                    rm.setBypassWal(true);
                    try {
                        rm.write(recordId, recordBytes);
                    } finally {
                        rm.setBypassWal(oldBypass);
                    }
                }
            }
        }

        if (index != null) {
            MemoryLocation existing = index.locate(memoryId);
            long textOffset = existing != null ? existing.textOffset() : -1L;
            int textLength = existing != null ? existing.textLength() : -1;
            if ((textOffset < 0 || textLength < 0) && index.textDataStore() != null && !text.isEmpty()) {
                boolean oldBypass = index.textDataStore().isBypassWal();
                index.textDataStore().setBypassWal(true);
                try {
                    var pos = index.textDataStore().write(memoryId, type, text);
                    if (pos != null) {
                        textOffset = pos.textOffset();
                        textLength = pos.textLength();
                    }
                } finally {
                    index.textDataStore().setBypassWal(oldBypass);
                }
            }

            int partSeq = colocatedPartition >= 0 ? colocatedPartition : fallbackPartitionSeq;
            MemoryLocation loc = new MemoryLocation(type, offset, graphSlot, partSeq, textOffset, textLength);
            if (!metadata.isEmpty()) {
                index.register(memoryId, loc, text, source, tags, metadata);
            } else {
                index.register(memoryId, loc, text, source, tags);
            }
        }
    }
}
