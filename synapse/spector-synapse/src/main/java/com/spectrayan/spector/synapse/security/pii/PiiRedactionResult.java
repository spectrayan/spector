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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome of a PII redaction pass.
 *
 * @param originalText source text before redaction
 * @param redactedText text with PII replaced by tokens
 * @param matches      detected spans (values must not be logged)
 * @param session      token map used for later rehydration
 */
public record PiiRedactionResult(
        String originalText,
        String redactedText,
        List<PiiMatch> matches,
        PiiRedactionSession session) {

    public PiiRedactionResult {
        Objects.requireNonNull(redactedText, "redactedText");
        matches = matches == null ? List.of() : List.copyOf(matches);
        Objects.requireNonNull(session, "session");
    }

    public boolean hadPii() {
        return !matches.isEmpty();
    }

    /** Safe-to-log counts by type. */
    public Map<PiiType, Integer> typeCounts() {
        return session.typeCounts();
    }
}
