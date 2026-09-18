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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Replaces detected PII spans with indexed tokens such as {@code [EMAIL_1]}.
 *
 * <p>Logs type counts only — never the original values.</p>
 */
public final class PiiRedactor {

    private static final Logger log = LoggerFactory.getLogger(PiiRedactor.class);

    private final PiiDetector detector;

    public PiiRedactor(PiiDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    public PiiRedactor() {
        this(new PiiDetector());
    }

    /**
     * Redacts PII in {@code text} at the given level into {@code session}.
     */
    public PiiRedactionResult redact(String text, PiiLevel level, PiiRedactionSession session) {
        Objects.requireNonNull(session, "session");
        if (text == null || text.isEmpty()) {
            return new PiiRedactionResult(text, text == null ? "" : text, List.of(), session);
        }
        PiiLevel effective = level != null ? level : PiiLevel.MODERATE;
        List<PiiMatch> matches = detector.detect(text, effective, session.contextId());
        if (matches.isEmpty()) {
            return new PiiRedactionResult(text, text, List.of(), session);
        }

        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;
        for (PiiMatch match : matches) {
            if (match.start() > cursor) {
                sb.append(text, cursor, match.start());
            }
            String token = session.tokenize(match.type(), match.value());
            sb.append(token);
            cursor = match.end();
        }
        if (cursor < text.length()) {
            sb.append(text, cursor, text.length());
        }

        String redacted = sb.toString();
        log.info("[PiiRedactor] Redacted PII typeCounts={} total={}",
                session.typeCounts(), matches.size());
        return new PiiRedactionResult(text, redacted, matches, session);
    }
}
