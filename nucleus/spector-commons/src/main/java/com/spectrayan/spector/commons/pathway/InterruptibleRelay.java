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

/**
 * Marker interface for relays whose {@link SynapticRelay#transmit transmit} can be safely
 * interrupted mid-flight (e.g., blocking HTTP calls that honour {@link Thread#interrupt()}).
 *
 * <p>The {@link PathwayComposer} rejects {@code .timeout(...)} on relays that do not implement
 * this interface — mmap writes and in-process compute loops do not observe interrupt, so a
 * timeout wrapper would report expiry while the work continues on a detached thread.</p>
 *
 * @see TimeoutRelay
 */
public interface InterruptibleRelay {

    /**
     * Returns {@code true} if this relay honours {@link Thread#interrupt()} during transmit,
     * making it safe to wrap with a timeout budget.
     *
     * @return true if interruptible
     */
    default boolean interruptible() {
        return true;
    }
}
