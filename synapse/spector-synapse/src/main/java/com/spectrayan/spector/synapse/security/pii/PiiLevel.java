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
package com.spectrayan.spector.synapse.security.pii;

/**
 * Sensitivity level for PII detection ({@code spector.security.pii.level}).
 *
 * <p>Each level selects a classpath Phileas PhiSQL policy under {@code /security/}:</p>
 * <ul>
 *   <li>{@link #RELAXED} — email, SSN, credit card</li>
 *   <li>{@link #MODERATE} — + phone, IP address</li>
 *   <li>{@link #STRICT} — + street address and ZIP_CODE (identifier filters only; no remote NER)</li>
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
