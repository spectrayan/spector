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
 * Marker interface for relays whose {@link SynapticRelay#transmit transmit} is safe to call
 * again after a failed attempt (i.e., does not produce duplicate side-effects).
 *
 * <p>The {@link PathwayComposer} rejects {@code .retry(...)} on relays that do not implement
 * this interface — for example, {@code CorticalWriteTransactionRelay} writes to mmap'd regions
 * and the WAL, which are not idempotent.</p>
 *
 * @see RetryRelay
 */
public interface IdempotentRelay {

    /**
     * Returns {@code true} if this relay's {@code transmit()} may be called again after a throw
     * without producing duplicate side-effects.
     *
     * @return true if idempotent
     */
    default boolean idempotent() {
        return true;
    }
}
