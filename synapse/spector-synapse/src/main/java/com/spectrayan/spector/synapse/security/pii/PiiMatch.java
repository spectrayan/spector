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
