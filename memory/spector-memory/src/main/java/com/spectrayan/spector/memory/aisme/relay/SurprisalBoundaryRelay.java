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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.segmentation.BoundaryReason;
import com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment;
import com.spectrayan.spector.memory.aisme.segmentation.SurprisalBoundaryDetector;
import com.spectrayan.spector.memory.aisme.segmentation.SurprisalBoundaryDetector.BoundaryEvaluation;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Synaptic relay executing real-time Bayesian Online Change-Point and Surprisal Episode Boundary Segmentation.
 *
 * <h3>Biological Analog: Prefrontal-Hippocampal Event Boundary Consolidation</h3>
 * <p>Buffers incoming sensory frames. Upon detecting an episodic boundary cut, packages the active
 * frame buffer into an immutable {@link EpisodicSegment}, flushes buffer state, and resets active
 * prediction caches to eliminate cross-episode retroactive interference.</p>
 */
public final class SurprisalBoundaryRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(SurprisalBoundaryRelay.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final SurprisalBoundaryDetector detector;
    private final MentalStateTracker tracker;
    private final Consumer<EpisodicSegment> segmentConsumer;

    private final List<float[]> bufferedVectors = new ArrayList<>();
    private long episodeStartTimestampMs = 0L;
    private float peakSurprisal = 0.0f;

    public SurprisalBoundaryRelay(SurprisalBoundaryDetector detector, MentalStateTracker tracker) {
        this(detector, tracker, null);
    }

    public SurprisalBoundaryRelay(
            SurprisalBoundaryDetector detector,
            MentalStateTracker tracker,
            Consumer<EpisodicSegment> segmentConsumer
    ) {
        this.detector = detector;
        this.tracker = tracker;
        this.segmentConsumer = segmentConsumer;
    }

    @Override
    public String relayName() {
        return RelayNames.SURPRISAL_BOUNDARY_SEGMENTATION;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal == null || detector == null || tracker == null || signal.vector() == null) {
            return true;
        }

        lock.lock();
        try {
            float[] vector = signal.vector();
            long ts = signal.timestampMs() > 0 ? signal.timestampMs() : System.currentTimeMillis();

            if (bufferedVectors.isEmpty()) {
                // First frame of a new episode
                episodeStartTimestampMs = ts;
                peakSurprisal = 0.0f;
                bufferedVectors.add(vector.clone());
                tracker.resetToObservation(vector, ts);
                return true;
            }

            int currentCount = bufferedVectors.size() + 1;
            BoundaryEvaluation eval = detector.evaluate(
                    tracker.posterior().mean(),
                    vector,
                    tracker.selfModel().observationPrecision(),
                    currentCount
            );

            peakSurprisal = Math.max(peakSurprisal, eval.surprisal());

            if (eval.isBoundary()) {
                // Synthesize centroid vector for the completed episode using SIMD
                int d = vector.length;
                float[] centroid = com.spectrayan.spector.core.similarity.VectorOps.centroid(bufferedVectors, d);

                EpisodicSegment segment = new EpisodicSegment(
                        UUID.randomUUID().toString(),
                        episodeStartTimestampMs,
                        ts,
                        bufferedVectors.size(),
                        centroid,
                        peakSurprisal,
                        eval.reason()
                );

                signal.episodicSegment(segment);

                if (segmentConsumer != null) {
                    segmentConsumer.accept(segment);
                }

                if (log.isDebugEnabled()) {
                    log.debug("SurprisalBoundaryRelay: packaged episode segment (id={}, frames={}, reason={}, peakSurprisal={})",
                            segment.segmentId(), segment.frameCount(), segment.boundaryReason(),
                            String.format("%.4f", segment.peakSurprisal()));
                }

                // Flush buffer and initialize next episode with current frame
                bufferedVectors.clear();
                bufferedVectors.add(vector.clone());
                episodeStartTimestampMs = ts;
                peakSurprisal = 0.0f;
                detector.reset();
                tracker.resetToObservation(vector, ts);
            } else {
                bufferedVectors.add(vector.clone());
                tracker.updateWithObservation(vector, ts);
            }
        } catch (final RuntimeException e) {
            log.warn("SurprisalBoundaryRelay: evaluation failed; passing signal uninhibited: {}", e.getMessage(), e);
        } finally {
            lock.unlock();
        }

        return true;
    }

    public int bufferedFrameCount() {
        lock.lock();
        try {
            return bufferedVectors.size();
        } finally {
            lock.unlock();
        }
    }

    public SurprisalBoundaryDetector detector() {
        return detector;
    }

    public MentalStateTracker tracker() {
        return tracker;
    }
}
