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
package com.spectrayan.spector.memory.session;

import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.EpisodeCodec;
import com.spectrayan.spector.memory.kernel.layout.EpisodeLayout;
import com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor;
import com.spectrayan.spector.memory.kernel.layout.compat.LegacyEpisodeHeaderReader;
import java.lang.foreign.ValueLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-session ordered offset index for episodic conversation turns.
 *
 * <h3>Purpose</h3>
 * <p>Provides O(1) paginated access to conversation turns by session ID.
 * Each session maps to an ordered list of byte offsets into the episodic
 * mmap region. Since turns are always appended chronologically, the offset
 * list is naturally sorted by {@code sequence_id}.</p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li><b>Write path</b>: {@link #appendTurn(long, long)} adds the new turn's
 *       byte offset to the session's list — O(1) amortized.</li>
 *   <li><b>Read path</b>: {@link #paginate(long, int, int)}, {@link #tailTurns(long, int)}
 *       — O(1) index lookup + O(pageSize) sublist copy.</li>
 *   <li><b>Startup</b>: {@link #rebuild(MemorySegment, long, long)} scans the
 *       episodic region sequentially, reading each 64B header to extract
 *       {@code session_id} and {@code body_length}, recording offsets.
 *       One-time O(N) linear scan.</li>
 * </ul>
 *
 * <h3>Memory Cost</h3>
 * <p>8 bytes per turn (one {@code long} offset). At 100K total turns across
 * all sessions, this costs ~800 KB of heap. At 1M turns, ~8 MB.</p>
 *
 * @since 1.3.0
 * @see com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor
 */
public final class EpisodicSessionIndex {

    private static final Logger log = LoggerFactory.getLogger(EpisodicSessionIndex.class);

    /**
     * Session ID → ordered list of byte offsets in the episodic mmap region.
     * Each offset points to the start of a 64B episodic header.
     */
    private final ConcurrentHashMap<Long, List<Long>> sessionOffsets = new ConcurrentHashMap<>();

    /**
     * Appends a turn's byte offset to the session's ordered list.
     *
     * <p>Since turns are always appended chronologically, the list
     * maintains natural sequence order without explicit sorting.</p>
     *
     * @param sessionId the 8B TSID hash identifying the session
     * @param offset    the byte offset of the turn's 64B header in the episodic region
     */
    public void appendTurn(long sessionId, long offset) {
        sessionOffsets.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                .add(offset);
    }

    /**
     * Returns the full ordered list of byte offsets for a session.
     *
     * @param sessionId the 8B TSID hash
     * @return unmodifiable list of offsets, or empty list if session unknown
     */
    public List<Long> getSessionTurns(long sessionId) {
        List<Long> offsets = sessionOffsets.get(sessionId);
        return (offsets != null) ? Collections.unmodifiableList(offsets) : List.of();
    }

    /**
     * Returns a paginated slice of byte offsets for a session.
     *
     * @param sessionId the 8B TSID hash
     * @param offset    zero-based start index (first turn = 0)
     * @param limit     maximum number of offsets to return
     * @return list of offsets for the requested page, or empty list
     */
    public List<Long> paginate(long sessionId, int offset, int limit) {
        List<Long> offsets = sessionOffsets.get(sessionId);
        if (offsets == null || offset >= offsets.size()) {
            return List.of();
        }
        int end = Math.min(offset + limit, offsets.size());
        // Return a copy to avoid concurrent modification
        return List.copyOf(offsets.subList(offset, end));
    }

    /**
     * Returns the last N turn offsets for a session (for LLM context window assembly).
     *
     * @param sessionId the 8B TSID hash
     * @param count     number of recent turns to return
     * @return list of the most recent offsets, or empty list
     */
    public List<Long> tailTurns(long sessionId, int count) {
        List<Long> offsets = sessionOffsets.get(sessionId);
        if (offsets == null || offsets.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, offsets.size() - count);
        return List.copyOf(offsets.subList(start, offsets.size()));
    }

    /**
     * Returns the total number of turns in a session.
     *
     * @param sessionId the 8B TSID hash
     * @return turn count, or 0 if session unknown
     */
    public int turnCount(long sessionId) {
        List<Long> offsets = sessionOffsets.get(sessionId);
        return (offsets != null) ? offsets.size() : 0;
    }

    /**
     * Returns all known session IDs.
     *
     * @return unmodifiable set of session ID hashes
     */
    public Set<Long> listSessions() {
        return Collections.unmodifiableSet(sessionOffsets.keySet());
    }

    /**
     * Returns the total number of active sessions.
     */
    public int sessionCount() {
        return sessionOffsets.size();
    }

    /**
     * Returns the total number of turns across all sessions.
     */
    public int totalTurnCount() {
        int total = 0;
        for (List<Long> offsets : sessionOffsets.values()) {
            total += offsets.size();
        }
        return total;
    }

    /**
     * Removes a session and all its turn offsets from the index.
     *
     * <p>Note: this only removes the in-memory index entry. The underlying
     * mmap records must be separately tombstoned via
     * {@link com.spectrayan.spector.memory.cortex.EpisodicMemory#tombstone}.</p>
     *
     * @param sessionId the 8B TSID hash
     * @return the removed offset list, or null if session was unknown
     */
    public List<Long> removeSession(long sessionId) {
        return sessionOffsets.remove(sessionId);
    }

    /**
     * Clears the entire index. Used before {@link #rebuild}.
     */
    public void clear() {
        sessionOffsets.clear();
    }

    /**
     * Rebuilds the session index by scanning the episodic mmap region sequentially.
     *
     * <p>Reads each 64B header, extracts {@code session_id} and {@code body_length},
     * and records the byte offset. Tombstoned records are skipped. The scan is
     * O(N) where N is the number of records.</p>
     *
     * <p>For 100K records at ~300B average, this scans ~30MB of sequential
     * mmap reads — well under a second on modern hardware.</p>
     *
     * @param segment      the episodic region's mmap segment
     * @param dataOffset   byte offset where data records begin (after metadata header)
     * @param writePosition current write cursor position (exclusive end)
     * @return the number of live (non-tombstoned) records indexed
     */
    public int rebuild(MemorySegment segment, long dataOffset, long writePosition) {
        clear();
        int liveCount = 0;
        int tombstoneCount = 0;
        long cursor = dataOffset;

        while (cursor + EncodingHeaderFields.HEADER_BYTES <= writePosition) {
            byte flags;
            long sessionId;
            long recordEnd;

            if (EpisodicHeaderAccessor.isOptionBRecord(segment, cursor)) {
                int payloadBytes = EpisodicHeaderAccessor.readPayloadBytes(segment, cursor);
                if (payloadBytes < 0) {
                    log.warn("Negative payloadBytes {} at offset {} — stopping rebuild", payloadBytes, cursor);
                    break;
                }
                recordEnd = cursor + EpisodeLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
                if (recordEnd > writePosition) {
                    log.warn("Record at offset {} extends beyond write position ({} > {}) — stopping rebuild",
                            cursor, recordEnd, writePosition);
                    break;
                }
                flags = EpisodicHeaderAccessor.readFlags(segment, cursor);
                long payloadOffset = cursor + EpisodeLayout.FIXED_OVERHEAD_BYTES;
                sessionId = (payloadBytes >= EpisodeCodec.PAYLOAD_METADATA_BYTES)
                        ? segment.get(ValueLayout.JAVA_LONG_UNALIGNED, payloadOffset + EpisodeCodec.OFFSET_SESSION_ID)
                        : 0L;
            } else {
                flags = LegacyEpisodeHeaderReader.readFlags(segment, cursor);
                int bodyLength = LegacyEpisodeHeaderReader.readBodyLength(segment, cursor);
                if (bodyLength < 0) {
                    log.warn("Negative body_length {} at offset {} — stopping rebuild", bodyLength, cursor);
                    break;
                }
                recordEnd = cursor + EncodingHeaderFields.HEADER_BYTES + bodyLength;
                if (recordEnd > writePosition) {
                    log.warn("Record at offset {} extends beyond write position ({} > {}) — stopping rebuild",
                            cursor, recordEnd, writePosition);
                    break;
                }
                sessionId = LegacyEpisodeHeaderReader.readSessionId(segment, cursor);
            }

            if (!EncodingHeaderFields.isTombstoned(flags)) {
                appendTurn(sessionId, cursor);
                liveCount++;
            } else {
                tombstoneCount++;
            }

            cursor = recordEnd;
        }

        log.info("Rebuilt episodic session index: {} live records, {} tombstoned, {} sessions",
                liveCount, tombstoneCount, sessionOffsets.size());
        return liveCount;
    }

    /**
     * Returns the internal map for testing/inspection purposes.
     *
     * @return unmodifiable view of the session offsets map
     */
    Map<Long, List<Long>> sessionOffsetsView() {
        return Collections.unmodifiableMap(sessionOffsets);
    }
}
