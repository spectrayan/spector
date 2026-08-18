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

import java.util.Objects;

/**
 * Immutable record capturing execution metrics and outcome for a single synaptic relay.
 *
 * @param relayName     name of the executed relay
 * @param durationNanos elapsed wall-clock time in nanoseconds
 * @param status        outcome status (e.g. EXECUTED, BYPASSED, SHORT_CIRCUITED, DEGRADED, FAILED)
 * @param detail        optional diagnostic detail or unsatisfied reason
 */
public record RelayTrace(
        String relayName,
        long durationNanos,
        TraceStatus status,
        String detail
) {
    public RelayTrace {
        Objects.requireNonNull(relayName, "relayName cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    /**
     * Execution outcome status for a relay.
     */
    public enum TraceStatus {
        EXECUTED,
        BYPASSED,
        SHORT_CIRCUITED,
        DEGRADED,
        FAILED
    }
}
