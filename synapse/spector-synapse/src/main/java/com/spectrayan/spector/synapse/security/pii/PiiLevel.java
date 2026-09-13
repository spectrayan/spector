/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.security.pii;

/**
 * Sensitivity level for PII detection ({@code spector.security.pii.level}).
 *
 * <p>Each level selects a classpath Phileas PhiSQL policy under {@code /security/}:</p>
 * <ul>
 *   <li>{@link #RELAXED} — email, SSN, credit card</li>
 *   <li>{@link #MODERATE} — + phone, IP address</li>
 *   <li>{@link #STRICT} — + street address (identifier filters only; no remote NER)</li>
 * </ul>
 */
public enum PiiLevel {
    RELAXED,
    MODERATE,
    STRICT;

    /** Whether this level is at least as sensitive as {@code required}. */
    public boolean includes(PiiLevel required) {
        return ordinal() >= required.ordinal();
    }
}
