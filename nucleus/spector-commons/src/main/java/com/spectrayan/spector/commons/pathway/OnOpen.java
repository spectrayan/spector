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
 * Action to take when a circuit breaker is in {@link CircuitBreaker.State#OPEN} state
 * or half-open probe capacity is saturated.
 */
public enum OnOpen {
    /**
     * Throw {@link CircuitOpenException} to fail fast.
     */
    FAIL,

    /**
     * Bypass execution gracefully and continue without executing the protected relay.
     */
    BYPASS
}
