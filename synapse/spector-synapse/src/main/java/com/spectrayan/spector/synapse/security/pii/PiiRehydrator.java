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

import java.util.Map;
import java.util.Objects;

/**
 * Restores original PII values from redaction tokens in LLM responses.
 *
 * <p>Tokens are replaced longest-first so {@code [EMAIL_10]} is not partially
 * matched by {@code [EMAIL_1]}.</p>
 */
public final class PiiRehydrator {

    private static final Logger log = LoggerFactory.getLogger(PiiRehydrator.class);

    /**
     * Replaces tokens from {@code session} with their original values.
     *
     * @param text    LLM (or other) text that may contain tokens
     * @param session session that produced the tokens
     * @return rehydrated text, or {@code text} unchanged when session is empty
     */
    public String rehydrate(String text, PiiRedactionSession session) {
        Objects.requireNonNull(session, "session");
        if (text == null || text.isEmpty() || session.isEmpty()) {
            return text == null ? "" : text;
        }

        Map<String, String> map = session.tokenMap();
        String result = text;
        // Longer tokens first to avoid [EMAIL_1] eating into [EMAIL_10]
        String[] tokens = map.keySet().stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);

        int replacements = 0;
        for (String token : tokens) {
            String original = map.get(token);
            if (original == null) {
                continue;
            }
            if (result.contains(token)) {
                result = result.replace(token, original);
                replacements++;
            }
        }

        if (replacements > 0) {
            log.debug("[PiiRehydrator] Rehydrated {} token(s)", replacements);
        }
        return result;
    }
}
