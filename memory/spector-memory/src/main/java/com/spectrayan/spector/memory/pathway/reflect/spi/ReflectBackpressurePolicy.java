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
package com.spectrayan.spector.memory.pathway.reflect.spi;

import com.spectrayan.spector.memory.pathway.reflect.SessionWorkItem;

/**
 * Service Provider Interface governing rate-limiting, pacing, and fault tolerance
 * between session consolidation executions.
 *
 * <p>Decouples pacing (token bucket, provider health, 429 backoff) from core kernel relays.</p>
 *
 * @since 1.5.0
 */
public interface ReflectBackpressurePolicy {

    /**
     * Interceptor invoked immediately before consolidating a session.
     *
     * <p>Implementations may pause the thread (e.g. token bucket acquisition)
     * to prevent overwhelming downstream LLM providers.</p>
     *
     * @param item the session work item scheduled to be consolidated
     * @throws InterruptedException if interrupted while waiting for rate-limiting permits
     */
    void beforeSession(SessionWorkItem item) throws InterruptedException;

    /**
     * Callback invoked when an LLM provider or external dependency throws an error
     * (such as HTTP 429 Too Many Requests or timeout).
     *
     * @param t the exception encountered
     */
    void onProviderFailure(Throwable t);

    /**
     * Returns true if the policy determines the current sweep should be aborted early
     * (e.g. circuit breaker tripped due to consecutive failures).
     *
     * @return true if the sweep should abort
     */
    boolean shouldAbortSweep();
}
