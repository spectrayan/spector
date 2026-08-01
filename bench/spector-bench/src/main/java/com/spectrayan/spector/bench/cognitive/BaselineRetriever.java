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
package com.spectrayan.spector.bench.cognitive;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.spectrayan.spector.bench.cognitive.model.ScoredResult;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;

/**
 * Baseline retriever that scores corpus memories using <b>only</b> L2 vector distance
 * converted to similarity, without any cognitive scoring phases.
 *
 * <p>This serves as the control condition in the benchmark: it demonstrates what
 * vanilla semantic search achieves without tag gating, valence filtering, importance
 * weighting, temporal decay, or graph augmentation.</p>
 *
 * <p>The scoring formula is simply:
 * <pre>
 *   similarity = 1.0 / (1.0 + L2_distance)
 * </pre>
 * where L2_distance is computed via calibrated INT8 asymmetric quantization
 * using {@link SimilarityFunction#EUCLIDEAN}.</p>
 *
 * <p>Tombstoned records (flags bit 0 = 1) are excluded from scoring to ensure
 * fair comparison with the cognitive retriever which also excludes them.</p>
 */
public final class BaselineRetriever {

    private final List<TierDescriptor> tiers;
    private final float[] calibrationMins;
    private final float[] calibrationScales;

    /**
     * Descriptor representing a memory tier to scan.
     */
    public static final class TierDescriptor {
        public final MemorySegment segment;
        public final CognitiveRecordLayout layout;
        public final int recordCount;
        public final long dataOffset;
        public final String[] memoryIds;

        public TierDescriptor(MemorySegment segment,
                              CognitiveRecordLayout layout,
                              int recordCount,
                              long dataOffset,
                              String[] memoryIds) {
            this.segment = segment;
            this.layout = layout;
            this.recordCount = recordCount;
            this.dataOffset = dataOffset;
            this.memoryIds = memoryIds;
        }
    }

    /**
     * Creates a new BaselineRetriever scanning multiple tiers.
     *
     * @param tiers              the list of tier descriptors to scan
     * @param calibrationMins    per-dimension minimum values from scalar quantizer calibration
     * @param calibrationScales   per-dimension scale values from scalar quantizer calibration
     */
    public BaselineRetriever(List<TierDescriptor> tiers,
                             float[] calibrationMins,
                             float[] calibrationScales) {
        this.tiers = List.copyOf(tiers);
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    /**
     * Creates a new BaselineRetriever for vector-only scoring (single tier legacy compatibility).
     *
     * @param corpusSegment    the off-heap memory segment containing all records
     * @param layout           the cognitive record layout (header + vector payload)
     * @param recordCount      total number of records in the segment
     * @param calibrationMins  per-dimension minimum values from scalar quantizer calibration
     * @param calibrationScales per-dimension scale values from scalar quantizer calibration
     * @param memoryIds        array mapping record index to memory ID string
     */
    public BaselineRetriever(MemorySegment corpusSegment,
                             CognitiveRecordLayout layout,
                             int recordCount,
                             float[] calibrationMins,
                             float[] calibrationScales,
                             String[] memoryIds) {
        this(corpusSegment, layout, recordCount, calibrationMins, calibrationScales, memoryIds, 0L);
    }

    /**
     * Creates a new BaselineRetriever for vector-only scoring (single tier legacy compatibility).
     *
     * @param corpusSegment    the off-heap memory segment containing all records
     * @param layout           the cognitive record layout (header + vector payload)
     * @param recordCount      total number of records in the segment
     * @param calibrationMins  per-dimension minimum values from scalar quantizer calibration
     * @param calibrationScales per-dimension scale values from scalar quantizer calibration
     * @param memoryIds        array mapping record index to memory ID string
     * @param dataOffset       byte offset of the first record in the segment (skipping memory header)
     */
    public BaselineRetriever(MemorySegment corpusSegment,
                             CognitiveRecordLayout layout,
                             int recordCount,
                             float[] calibrationMins,
                             float[] calibrationScales,
                             String[] memoryIds,
                             long dataOffset) {
        this.tiers = List.of(new TierDescriptor(corpusSegment, layout, recordCount, dataOffset, memoryIds));
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    /**
     * Retrieves the top-K results using pure vector similarity (L2 distance) across all tiers.
     *
     * @param queryVector the query vector in float32
     * @param topK        the maximum number of results to return
     * @return results sorted by descending similarity score
     */
    public List<ScoredResult> retrieve(float[] queryVector, int topK) {
        // Min-heap: lowest score at head, so we can efficiently evict the worst candidate
        PriorityQueue<ScoredResult> heap = new PriorityQueue<>(topK + 1,
                Comparator.comparingDouble(ScoredResult::score));

        for (TierDescriptor tier : tiers) {
            int stride = tier.layout.stride();
            int count = tier.recordCount;
            MemorySegment segment = tier.segment;
            long dataOffset = tier.dataOffset;
            String[] memoryIds = tier.memoryIds;

            // Fail-fast guard: verify header magic for persistent memory segments
            if (dataOffset > 0 && count > 0) {
                int magic = segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0);
                if (magic != MemoryHeader.MAGIC) {
                    throw new IllegalStateException("Failed baseline assertion: expected header magic "
                            + Integer.toHexString(MemoryHeader.MAGIC) + ", got " + Integer.toHexString(magic));
                }
            }

            for (int i = 0; i < count; i++) {
                long offset = dataOffset + (long) i * stride;

                // Skip tombstoned records (bit 0 of flags byte)
                byte flags = segment.get(ValueLayout.JAVA_BYTE,
                        offset + SynapticHeaderConstants.OFFSET_FLAGS);
                if (SynapticHeaderConstants.isTombstoned(flags)) {
                    continue;
                }

                // Skip unmapped/null memory IDs (e.g. consolidation orphans or slot mismatches)
                if (memoryIds[i] == null) {
                    continue;
                }

                // Compute L2 distance using calibrated quantized vectors
                float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                        queryVector, segment, tier.layout.vectorOffset(offset),
                        calibrationMins, calibrationScales, tier.layout.quantizedVecBytes());

                // Convert to similarity: higher is better
                float similarity = 1.0f / (1.0f + l2dist);

                // Min-heap insertion: maintain only topK best results
                if (heap.size() < topK) {
                    heap.offer(new ScoredResult(memoryIds[i], similarity));
                } else if (similarity > heap.peek().score()) {
                    heap.poll();
                    heap.offer(new ScoredResult(memoryIds[i], similarity));
                }
            }
        }

        // Sort results by descending score for output
        List<ScoredResult> results = new ArrayList<>(heap);
        results.sort(Comparator.comparingDouble(ScoredResult::score).reversed());
        return results;
    }
}
