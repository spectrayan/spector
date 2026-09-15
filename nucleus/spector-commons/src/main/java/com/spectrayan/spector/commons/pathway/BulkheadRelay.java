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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator relay that limits concurrent in-flight calls via a {@link Semaphore} (ADR-0036 §10).
 *
 * <p>Virtual threads make this a logical cap, not a platform-thread cap. It exists so Dream
 * cannot enqueue 200 Remember conducts on top of live Recall.</p>
 *
 * <p>When a permit cannot be acquired within the configured wait period, the relay either
 * throws ({@link OnReject#FAIL}) or marks the outcome as bypassed ({@link OnReject#BYPASS}).</p>
 *
 * @param <S> signal type
 */
public final class BulkheadRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(BulkheadRelay.class);

    private final SynapticRelay<S> delegate;
    private final Semaphore semaphore;
    private final BulkheadConfig config;
    private final String relayName;
    private final String bulkheadName;

    /**
     * Creates a bulkhead decorator with a local semaphore fallback if not found in context.
     *
     * @param delegate      the inner relay to wrap
     * @param config        bulkhead configuration
     * @param relayName     relay name for diagnostics
     * @param bulkheadName  bulkhead name for diagnostics
     */
    public BulkheadRelay(SynapticRelay<S> delegate, BulkheadConfig config,
                          String relayName, String bulkheadName) {
        this(delegate, new Semaphore(config.maxInFlight(), true), config, relayName, bulkheadName);
    }

    /**
     * Creates a bulkhead decorator with an explicit semaphore.
     *
     * @param delegate      the inner relay to wrap
     * @param semaphore     the shared semaphore from {@link BulkheadRegistry}
     * @param config        bulkhead configuration
     * @param relayName     relay name for diagnostics
     * @param bulkheadName  bulkhead name for diagnostics
     */
    public BulkheadRelay(SynapticRelay<S> delegate, Semaphore semaphore,
                          BulkheadConfig config, String relayName, String bulkheadName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.semaphore = Objects.requireNonNull(semaphore, "semaphore cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.relayName = Objects.requireNonNull(relayName, "relayName cannot be null");
        this.bulkheadName = Objects.requireNonNull(bulkheadName, "bulkheadName cannot be null");
    }

    @Override
    public boolean transmit(S signal) throws Exception {
        Semaphore targetSem = this.semaphore;
        if (signal instanceof ContextualSignal cs && cs.context() != null) {
            targetSem = cs.context().find(BulkheadRegistry.class)
                    .map(r -> r.get(bulkheadName, config))
                    .orElse(this.semaphore);
        }

        boolean acquired;
        if (config.waitDuration().isZero()) {
            acquired = targetSem.tryAcquire();
        } else {
            acquired = targetSem.tryAcquire(config.waitDuration().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (!acquired) {
            if (config.onReject() == OnReject.BYPASS) {
                log.debug("[{}] Bulkhead '{}' full ({} permits), bypassing",
                        relayName, bulkheadName, config.maxInFlight());
                if (signal instanceof ContextualSignal cs && cs.context() != null) {
                    cs.context().outcome().markBypassed(
                            relayName, "bulkhead_full:" + bulkheadName);
                }
                return true; // continue relay chain
            } else {
                String pathwayName = "unknown";
                if (signal instanceof ContextualSignal cs && cs.context() != null
                        && cs.context().scope() != null) {
                    pathwayName = cs.context().scope().pathwayName();
                }
                throw new CognitivePathwayException(
                        ErrorCode.PATHWAY_BULKHEAD,
                        pathwayName, relayName, FaultKind.TRANSIENT, false, null);
            }
        }

        try {
            return delegate.transmit(signal);
        } finally {
            semaphore.release();
        }
    }

    /** Returns the wrapped delegate relay. */
    public SynapticRelay<S> delegate() { return delegate; }

    /** Returns the bulkhead configuration. */
    public BulkheadConfig config() { return config; }
}
