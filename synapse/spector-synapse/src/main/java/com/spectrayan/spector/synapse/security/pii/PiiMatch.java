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
 * A single PII span found in text.
 *
 * <p>The raw {@code value} is kept only for redaction/rehydration within a
 * session; it must never be written to logs.</p>
 *
 * @param type  PII category
 * @param value original substring (session-scoped only — do not log)
 * @param start inclusive start index in the source text
 * @param end   exclusive end index in the source text
 */
public record PiiMatch(PiiType type, String value, int start, int end) {
    public int length() {
        return end - start;
    }
}
