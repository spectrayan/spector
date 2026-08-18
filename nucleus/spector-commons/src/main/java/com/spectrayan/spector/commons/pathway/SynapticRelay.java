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
 * Represents a relay unit in a cognitive pathway capable of transmitting a signal.
 *
 * @param <S> the type of the signal
 */
@FunctionalInterface
public interface SynapticRelay<S> {

    /**
     * Transmits the given signal through this relay.
     *
     * @param signal the signal to transmit
     * @return true if the transmission was successful and the pathway should continue, false otherwise
     * @throws Exception if an error occurs during transmission
     */
    boolean transmit(S signal) throws Exception;

    /**
     * Retrieves the name of this relay.
     *
     * @return the name of the relay
     */
    default String relayName() {
        return getClass().getSimpleName().replaceAll("Relay$", "").toLowerCase();
    }
}
