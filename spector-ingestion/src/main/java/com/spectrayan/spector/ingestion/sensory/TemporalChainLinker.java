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
package com.spectrayan.spector.ingestion.sensory;

import com.spectrayan.spector.ingestion.sensory.SensoryExtractor.ExtractionChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds temporal chain links between sequential extraction chunks.
 *
 * <p>When a video or audio file is broken into time-ordered chunks (e.g., keyframes
 * at 0s, 10s, 20s), the TemporalChainLinker creates predecessor → successor links
 * between adjacent chunks. These links can be stored as temporal chain entries in
 * Spector's cognitive memory system.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   List<ExtractionChunk> frames = extractor.extract(video, "video/mp4").toList();
 *   List<TemporalLink> links = TemporalChainLinker.linkSequential(frames, sessionId);
 *   // Each link connects chunk[i] → chunk[i+1]
 * }</pre>
 *
 * <h3>Link Structure</h3>
 * <p>A {@link TemporalLink} captures:</p>
 * <ul>
 *   <li>{@code predecessorChunkId} — the earlier chunk</li>
 *   <li>{@code successorChunkId} — the later chunk</li>
 *   <li>{@code sessionId} — groups links from the same media file</li>
 *   <li>{@code timestampSeconds} — temporal offset of the successor</li>
 * </ul>
 */
public final class TemporalChainLinker {

    private static final Logger log = LoggerFactory.getLogger(TemporalChainLinker.class);

    private TemporalChainLinker() {
        // utility class
    }

    /**
     * Creates temporal links between sequential chunks.
     *
     * <p>For N chunks, produces N-1 links connecting each pair of adjacent chunks.
     * Chunks are assumed to be already in temporal order (as produced by extractors).</p>
     *
     * @param chunks    ordered list of extraction chunks
     * @param sessionId session identifier (typically the parent memory ID or file hash)
     * @return list of temporal links, empty if fewer than 2 chunks
     */
    public static List<TemporalLink> linkSequential(List<ExtractionChunk> chunks, int sessionId) {
        if (chunks == null || chunks.size() < 2) {
            return Collections.emptyList();
        }

        List<TemporalLink> links = new ArrayList<>(chunks.size() - 1);
        for (int i = 0; i < chunks.size() - 1; i++) {
            ExtractionChunk predecessor = chunks.get(i);
            ExtractionChunk successor = chunks.get(i + 1);

            int timestampSeconds = parseTimestamp(successor.metadata());

            links.add(new TemporalLink(
                    predecessor.chunkId(),
                    successor.chunkId(),
                    sessionId,
                    timestampSeconds
            ));
        }

        log.debug("Created {} temporal links for session {} from {} chunks",
                links.size(), sessionId, chunks.size());
        return links;
    }

    /**
     * Creates temporal links with a custom chunk ID prefix.
     *
     * <p>Useful when chunk IDs need to be qualified with a parent memory ID:
     * e.g., {@code "mem-123::frame-0"} instead of just {@code "frame-0"}.</p>
     *
     * @param chunks       ordered list of extraction chunks
     * @param sessionId    session identifier
     * @param idPrefix     prefix to prepend to chunk IDs (e.g., "mem-123::")
     * @return list of temporal links with prefixed IDs
     */
    public static List<TemporalLink> linkSequential(List<ExtractionChunk> chunks,
                                                      int sessionId, String idPrefix) {
        if (chunks == null || chunks.size() < 2) {
            return Collections.emptyList();
        }

        String prefix = (idPrefix != null && !idPrefix.isEmpty()) ? idPrefix : "";
        List<TemporalLink> links = new ArrayList<>(chunks.size() - 1);

        for (int i = 0; i < chunks.size() - 1; i++) {
            ExtractionChunk predecessor = chunks.get(i);
            ExtractionChunk successor = chunks.get(i + 1);

            links.add(new TemporalLink(
                    prefix + predecessor.chunkId(),
                    prefix + successor.chunkId(),
                    sessionId,
                    parseTimestamp(successor.metadata())
            ));
        }

        return links;
    }

    /**
     * Parses the timestamp_seconds metadata value, defaulting to -1 if absent.
     */
    private static int parseTimestamp(Map<String, String> metadata) {
        if (metadata == null) return -1;
        String ts = metadata.get("timestamp_seconds");
        if (ts == null) return -1;
        try {
            return Integer.parseInt(ts);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * A temporal link between two sequential chunks.
     *
     * @param predecessorChunkId ID of the earlier chunk
     * @param successorChunkId   ID of the later chunk
     * @param sessionId          session grouping identifier
     * @param timestampSeconds   temporal offset of the successor (or -1 if unknown)
     */
    public record TemporalLink(
            String predecessorChunkId,
            String successorChunkId,
            int sessionId,
            int timestampSeconds
    ) {
        /**
         * Returns the gap in seconds between this link's endpoints,
         * or -1 if timestamps are unavailable.
         */
        public int durationGap(TemporalLink previous) {
            if (previous == null || previous.timestampSeconds < 0 || this.timestampSeconds < 0) {
                return -1;
            }
            return this.timestampSeconds - previous.timestampSeconds;
        }
    }
}
