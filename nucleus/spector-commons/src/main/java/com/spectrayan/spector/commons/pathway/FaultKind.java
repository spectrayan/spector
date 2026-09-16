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
 * Categorization of cognitive pathway faults for circuit breaking, retry decisions, and metrics.
 */
public enum FaultKind {

    /** Input failed validation. Never retry. Never trip a breaker. */
    VALIDATION,

    /** Domain invariant (duplicate id with fail-on-dup, missing kernel). Never retry. Never trip. */
    CONTRACT,

    /** Expected empty / gated-off / short-circuit. Not a fault. Stage continues. */
    CONTROL,

    /**
     * Thread was interrupted. Distinct from {@link #CONTROL}: an interrupted
     * conduction ALWAYS stops, whereas CONTROL always continues.
     * Never retry. Never trip a breaker.
     */
    INTERRUPTED,

    /** Transient I/O, timeout, remote 429/503, embed provider hiccup. Retryable. Trips breaker. */
    TRANSIENT,

    /** Remote 4xx (other than 429), parse failure from LLM, corrupt optional payload. No retry. May trip. */
    DOWNSTREAM,

    /** Bug, NPE, assertion. No retry. Does not trip a downstream breaker. */
    INTERNAL
}
