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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator relay that retries a delegate on transient failures (ADR-0036 §8).
 *
 * <p>Retry loop with exponential backoff and jitter. Only retries fault kinds
 * specified in the {@link RetryPolicy}. The relay must implement {@link IdempotentRelay}
 * — the {@link PathwayComposer} enforces this at build time.</p>
 *
 * <p>If all attempts fail, the last exception is thrown. The circuit breaker
 * (outside retry in the decorator chain) sees <strong>one</strong> failure.</p>
 *
 * @param <S> signal type
 */
public final class RetryRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(RetryRelay.class);

    private final SynapticRelay<S> delegate;
    private final RetryPolicy policy;
    private final String relayName;

    /**
     * Creates a retry decorator.
     *
     * @param delegate  the inner relay to wrap
     * @param policy    retry policy
     * @param relayName name for diagnostics
     */
    public RetryRelay(SynapticRelay<S> delegate, RetryPolicy policy, String relayName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.relayName = Objects.requireNonNull(relayName, "relayName cannot be null");
    }

    @Override
    public boolean transmit(S signal) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                return delegate.transmit(signal);
            } catch (Exception e) {
                lastException = e;
                FaultKind kind = Faults.kindOf(e);

                if (attempt >= policy.maxAttempts() || !policy.shouldRetry(kind)) {
                    break;
                }

                Duration backoff = policy.backoffFor(attempt + 1);
                if (!backoff.isZero()) {
                    log.debug("[{}] Attempt {}/{} failed ({}), retrying after {}",
                            relayName, attempt, policy.maxAttempts(), kind, backoff);
                    LockSupport.parkNanos(backoff.toNanos());
                } else {
                    log.debug("[{}] Attempt {}/{} failed ({}), retrying immediately",
                            relayName, attempt, policy.maxAttempts(), kind);
                }
            }
        }

        throw lastException;
    }

    /** Returns the wrapped delegate relay. */
    public SynapticRelay<S> delegate() { return delegate; }

    /** Returns the retry policy. */
    public RetryPolicy policy() { return policy; }
}
