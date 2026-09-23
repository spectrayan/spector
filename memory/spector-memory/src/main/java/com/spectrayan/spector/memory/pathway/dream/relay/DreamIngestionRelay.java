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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.commons.pathway.Faults;
import com.spectrayan.spector.commons.pathway.PathwayCatalog;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.RememberResult;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 12 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Dream-to-Memory Consolidation Gate &amp; Synaptic Downscaling</h3>
 * <p>Persists verified high-utility dream insights with {@code FLAG_DREAMED} provenance and applies
 * active Hebbian inhibition (\(\Delta w < 0\)) to the synaptic connections of failed dream fragment
 * pairs to prevent the cognitive engine from repeatedly simulating unproductive associations.</p>
 *
 * <p>As of Phase 7, this relay invokes Remember via {@link PathwayCatalog} when available,
 * via {@code catalog.invoke(RememberPathway.class, ...)}. Dream holds no reference to
 * Remember: the catalog resolves it and {@link DreamPorts} maps between the signals.</p>
 *
 * @since 1.4.0
 */
public final class DreamIngestionRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamIngestionRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) throws Exception {
        if (signal == null) return true;

        float threshold = signal.config().persistenceThreshold();
        int eligibleCount = 0;

        final short soulVersion = DreamPorts.resolveSoulVersion(signal);
        final PathwayCatalog catalog = signal.context() != null ? signal.context().catalog() : null;
        // Equivalent of PathwayRelay's required=false (ADR-0035 §6.7): when Remember is
        // not registered, dreaming still completes rather than throwing. Checking only
        // `catalog != null` is not enough — catalog.invoke() throws CONTRACT on a missing
        // registration, which would fail every Dream conduction in a standalone setup.
        final boolean rememberAvailable =
                catalog != null && catalog.find(RememberPathway.class).isPresent();

        // One dream conduction persists N scenes, so this loops over catalog.invoke
        // rather than using a 1:1 PathwayRelay (ADR-0035 §8.4 option b). The breaker
        // and bulkhead therefore sit on the enclosing "dream_ingestion" stage and cover
        // the whole batch — see DreamPathway.
        int attempted = 0;
        int failed = 0;
        Exception lastFailure = null;

        // 1. Ingest qualified surviving dream insights
        for (DreamSignal.DreamScene scene : signal.survivingScenes()) {
            if (scene.qualityScore() >= threshold) {
                // ADR-0086 §5.6: PRAGMATIC scenes are procedural skill candidates;
                // do not auto-persist unless explicit dream skill-auto-commit is enabled.
                if (scene.triageOutcome() == TriageOutcome.PRAGMATIC && (signal.config() == null || !signal.config().isSkillAutoCommit())) {
                    log.info("DreamIngestionRelay: PRAGMATIC dream scene [{}] proposed as skill candidate (auto-commit=false): {}",
                            scene.id(), scene.insightText());
                    continue;
                }

                if (scene.embedding() != null && rememberAvailable) {
                    attempted++;
                    try {
                        final String durableId = signal.nextId();
                        final RememberSignal rememberSignal = DreamPorts.toRememberSignal(
                                signal, scene, durableId, soulVersion);
                        final RememberResult result = catalog.invoke(
                                RememberPathway.class, signal.context(), rememberSignal);
                        DreamPorts.absorbRemembered(signal, result);
                    } catch (Exception e) {
                        // Per-scene failures do not abandon the rest of the batch, but they
                        // are NOT swallowed either: each one is recorded on the conduction
                        // outcome so it is visible and metered, and if every scene fails the
                        // exception is rethrown below so the conductor applies the stage's
                        // ErrorPolicy and the pathway:remember breaker registers a failure.
                        failed++;
                        lastFailure = e;
                        if (signal.context() != null) {
                            signal.context().outcome().markDegraded(
                                    "dream_ingestion/scene:" + scene.id(),
                                    Faults.kindOf(e), e);
                        }
                        log.warn("DreamIngestionRelay: failed to persist dream insight {}: {}",
                                scene.id(), e.getMessage());
                        continue;
                    }
                }
                log.info("DreamIngestionRelay: Ingested dream insight [{}] (Q={}, Mode={}): {}",
                        scene.id(), scene.qualityScore(), signal.mode(), scene.insightText());
                eligibleCount++;
            }
        }

        // Total failure of a non-empty batch means Remember itself is unhealthy — let it
        // out so the breaker trips rather than reporting a silently empty dream.
        if (lastFailure != null && attempted > 0 && failed == attempted) {
            throw lastFailure;
        }

        // Also account for Langevin discovery insights
        if (signal.extractedInsights() != null) {
            eligibleCount += signal.extractedInsights().size();
        }

        signal.dreamsIngested().set(eligibleCount);

        // 2. Active Hebbian inhibition on failed/noise dream pairings
        int failures = signal.failedPairs().get();
        if (failures > 0 && signal.hebbianGraph() != null) {
            HebbianGraphBase graph = signal.hebbianGraph();
            float inhibitionDelta = signal.config().hebbianInhibitionDelta();

            // Weaken synaptic association edges for failed seed combinations
            for (DreamSignal.DreamScene scene : signal.constructedScenes()) {
                if (scene.triageOutcome() == TriageOutcome.NOISE && scene.sourceIds().size() >= 2) {
                    int nodeA = parseNodeIndex(scene.sourceIds().get(0));
                    int nodeB = parseNodeIndex(scene.sourceIds().get(1));
                    if (nodeA >= 0 && nodeB >= 0 && nodeA < graph.capacity() && nodeB < graph.capacity() && nodeA != nodeB) {
                        graph.strengthen(nodeA, nodeB, inhibitionDelta);
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("DreamIngestionRelay: applied Hebbian synaptic inhibition (delta={}) to {} failed dream pairs",
                        inhibitionDelta, failures);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("DreamIngestionRelay: completed dream ingestion cycle — {} insights persisted, {} failed pairs inhibited",
                    eligibleCount, failures);
        }

        return true;
    }

    /**
     * Extracts the trailing numeric node index from a Hebbian source id
     * (e.g. {@code "scene-42"} yields {@code 42}).
     *
     * @param sourceId source id, possibly null or without a trailing index
     * @return the parsed index, or {@code -1} when absent or malformed
     */

    private static int parseNodeIndex(String sourceId) {
        if (sourceId == null) return -1;
        int dash = sourceId.lastIndexOf('-');
        if (dash >= 0 && dash < sourceId.length() - 1) {
            try {
                return Integer.parseInt(sourceId.substring(dash + 1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public String relayName() {
        return "dream_ingestion";
    }
}
