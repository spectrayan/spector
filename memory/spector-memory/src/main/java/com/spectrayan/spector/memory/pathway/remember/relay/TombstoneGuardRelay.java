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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.pathway.RelayNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Tombstone guard relay that blocks re-ingestion of content semantically equivalent
 * to a previously forgotten (tombstoned) memory.
 *
 * <p>When a memory is forgotten via {@code memory_forget}, the record's
 * {@code FLAG_TOMBSTONE} bit is set but the quantized vector payload remains
 * intact in the slab until vacuum/purge. This relay scans tombstoned slots in
 * the target tier and rejects any incoming candidate whose embedding vector
 * falls within a configurable L2 distance threshold of a tombstoned record.</p>
 *
 * <h3>Separation of Concerns</h3>
 * <p>{@link DedupGuardRelay} guards against <b>ID-level</b> duplicates.
 * This relay guards against <b>content-level</b> re-ingestion of tombstoned
 * values. Keeping them separate follows the single-responsibility principle.</p>
 *
 * <h3>Relay Position</h3>
 * <p>Placed after {@link SynapticTagTransductionRelay} (which normalizes the
 * candidate vector) and before {@link DopaminergicSurpriseRelay} (to avoid
 * wasting surprise computation on candidates that will be rejected).</p>
 *
 * @since 1.6.0
 * @see DedupGuardRelay
 * @see com.spectrayan.spector.memory.cortex.consolidation.DuplicateDetector
 */
public final class TombstoneGuardRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(TombstoneGuardRelay.class);

    /** Default L2 distance threshold — matches {@code DuplicateDetector} default. */
    public static final float DEFAULT_DISTANCE_THRESHOLD = 0.05f;

    private final CognitiveMemoryRouter cognitiveRouter;
    private final ScalarQuantizer quantizer;
    private final float distanceThreshold;

    /**
     * Creates a tombstone guard relay with the default distance threshold (0.05).
     *
     * @param cognitiveRouter the cognitive memory router for accessing tier stores
     * @param quantizer       the scalar quantizer for decoding tombstoned vectors
     */
    public TombstoneGuardRelay(
            final CognitiveMemoryRouter cognitiveRouter,
            final ScalarQuantizer quantizer) {
        this(cognitiveRouter, quantizer, DEFAULT_DISTANCE_THRESHOLD);
    }

    /**
     * Creates a tombstone guard relay with a configurable distance threshold.
     *
     * @param cognitiveRouter   the cognitive memory router for accessing tier stores
     * @param quantizer         the scalar quantizer for decoding tombstoned vectors
     * @param distanceThreshold L2 distance threshold; candidates within this distance
     *                          of a tombstoned record are rejected
     */
    public TombstoneGuardRelay(
            final CognitiveMemoryRouter cognitiveRouter,
            final ScalarQuantizer quantizer,
            final float distanceThreshold) {
        this.cognitiveRouter = Objects.requireNonNull(cognitiveRouter, "cognitiveRouter cannot be null");
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer cannot be null");
        this.distanceThreshold = distanceThreshold;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        final float[] candidateVector = signal.vector();
        if (candidateVector == null) {
            // No vector available — skip guard (header-only re-ingestion or text-only record)
            return true;
        }

        final EngramRegion store;
        try {
            store = cognitiveRouter.get(signal.type());
        } catch (Exception e) {
            // Store not registered for this tier — proceed without guard
            log.debug("TombstoneGuard: no store registered for tier {}, skipping", signal.type());
            return true;
        }

        final int visibleCount = store.visibleCount();
        if (visibleCount == 0) {
            return true;
        }

        // Quick check: if no tombstoned records exist, skip the scan entirely
        if (store.tombstoneRatio() <= 0.0f) {
            return true;
        }

        final long baseOffset = store.dataOffset();
        final int stride = store.layout().recordStride();
        final int dimensions = quantizer.dimensions();
        final float[] decoded = new float[dimensions];

        for (int i = 0; i < visibleCount; i++) {
            final long offset = baseOffset + (long) i * stride;

            // Only examine tombstoned records
            if (!store.isTombstoned(offset)) {
                continue;
            }

            // Skip purged records — their vector payload has been physically zeroed
            if (store.isPurged(offset)) {
                continue;
            }

            final byte[] quantizedBuf = store.readVector(offset);
            if (quantizedBuf == null) {
                continue;
            }

            quantizer.decode(quantizedBuf, 0, decoded, 0);

            final float dist = SimilarityFunction.EUCLIDEAN.compute(candidateVector, decoded);
            if (dist <= distanceThreshold) {
                log.warn("TombstoneGuard: blocking re-ingestion of '{}' — L2 distance {}"
                        + " to tombstoned record at slot {} (threshold {})",
                        sanitize(signal.id()), dist, i, distanceThreshold);
                signal.tombstoneBlocked(true);
                return false; // Short-circuit pathway execution
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.TOMBSTONE_GUARD;
    }

    private static String sanitize(final String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\n', '_').replace('\r', '_');
    }
}
