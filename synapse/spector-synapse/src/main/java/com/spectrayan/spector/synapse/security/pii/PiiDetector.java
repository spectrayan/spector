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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spector PII detection facade. Delegates to an embedded Phileas engine behind
 * this API — Phileas types never appear on public Synapse / OpenAPI surfaces.
 *
 * <p>Policies are classpath PhiSQL resources under {@code /security/}, loaded
 * once and cached. Mapping:</p>
 * <ul>
 *   <li>{@link PiiLevel#RELAXED} — email, SSN, credit card</li>
 *   <li>{@link PiiLevel#MODERATE} — + phone, IP</li>
 *   <li>{@link PiiLevel#STRICT} — + street address and ZIP_CODE</li>
 * </ul>
 *
 * <p>Identifier filters only by default (no remote Ph-Eye NER). Fail-closed if
 * a policy cannot be loaded or filtering throws.</p>
 */
public final class PiiDetector {

    private static final Logger log = LoggerFactory.getLogger(PiiDetector.class);

    private final PhileasPiiEngine engine;

    /** Production constructor: loads and caches all three PhiSQL policies. */
    public PiiDetector() {
        this(new PhileasPiiEngine());
    }

    /** Visible for tests. */
    PiiDetector(PhileasPiiEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Detects PII spans at the given sensitivity level using a default context id.
     *
     * @param text  content to scan (null/blank → empty)
     * @param level sensitivity gate (selects cached policy)
     * @return non-overlapping matches sorted by start index
     */
    public List<PiiMatch> detect(String text, PiiLevel level) {
        return detect(text, level, "spector");
    }

    /**
     * Detects PII spans using a request-scoped Phileas context id (consistent
     * replacements within one protect/session). Spector still builds its own
     * {@code [EMAIL_N]} tokens from returned spans — Phileas replacement strings
     * are not used for rehydration.
     *
     * @param text      content to scan (null/blank → empty)
     * @param level     sensitivity gate
     * @param contextId Phileas context scope (typically {@link PiiRedactionSession#contextId()})
     * @return non-overlapping matches sorted by start index
     */
    public List<PiiMatch> detect(String text, PiiLevel level, String contextId) {
        if (text == null || text.isBlank() || level == null) {
            return List.of();
        }
        List<PiiMatch> resolved = engine.detect(text, level, contextId);
        if (!resolved.isEmpty()) {
            Map<PiiType, Integer> counts = new EnumMap<>(PiiType.class);
            for (PiiMatch match : resolved) {
                counts.merge(match.type(), 1, Integer::sum);
            }
            // Log type + count only — never actual PII values
            log.info("[PiiDetector] Detected PII types={} total={}", counts, resolved.size());
        }
        return resolved;
    }

    /** Whether the given level's PhiSQL policy is cached (always true after successful construct). */
    public boolean hasPolicy(PiiLevel level) {
        return level != null && engine.policies().containsKey(level);
    }
}
