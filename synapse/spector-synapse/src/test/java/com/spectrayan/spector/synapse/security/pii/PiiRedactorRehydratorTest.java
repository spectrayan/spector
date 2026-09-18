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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiRedactor + PiiRehydrator")
class PiiRedactorRehydratorTest {

    private final PiiRedactor redactor = new PiiRedactor();
    private final PiiRehydrator rehydrator = new PiiRehydrator();

    @Test
    @DisplayName("replaces emails with indexed tokens")
    void redactsEmails() {
        PiiRedactionSession session = new PiiRedactionSession();
        PiiRedactionResult result = redactor.redact(
                "Email alice@example.com and bob@example.com",
                PiiLevel.RELAXED,
                session);

        assertThat(result.redactedText()).contains("[EMAIL_1]", "[EMAIL_2]");
        assertThat(result.redactedText()).doesNotContain("alice@example.com");
        assertThat(result.redactedText()).doesNotContain("bob@example.com");
        assertThat(result.hadPii()).isTrue();
    }

    @Test
    @DisplayName("rehydrates Spector tokens built from Phileas spans")
    void rehydratesTokens() {
        PiiRedactionSession session = new PiiRedactionSession();
        PiiRedactionResult outbound = redactor.redact(
                "Call (512) 555-1234 or john@example.com about invoice #12345",
                PiiLevel.MODERATE,
                session);

        assertThat(outbound.redactedText()).contains("[EMAIL_1]");
        assertThat(outbound.redactedText()).contains("[PHONE_1]");
        assertThat(outbound.redactedText()).doesNotContain("{{{REDACTED-");

        String synthetic = "I will follow up with " + outbound.redactedText();
        String restored = rehydrator.rehydrate(synthetic, session);
        assertThat(restored).contains("john@example.com");
        assertThat(restored).contains("(512) 555-1234");
        assertThat(restored).doesNotContain("[EMAIL_1]");
        assertThat(restored).doesNotContain("[PHONE_1]");
    }

    @Test
    @DisplayName("same value maps to the same token")
    void deduplicatesTokens() {
        PiiRedactionSession session = new PiiRedactionSession();
        PiiRedactionResult result = redactor.redact(
                "a@x.com then a@x.com again",
                PiiLevel.RELAXED,
                session);

        assertThat(result.redactedText()).isEqualTo("[EMAIL_1] then [EMAIL_1] again");
        assertThat(session.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("rehydration prefers longer tokens ([EMAIL_10] vs [EMAIL_1])")
    void longerTokensFirst() {
        PiiRedactionSession session = new PiiRedactionSession();
        for (int i = 1; i <= 10; i++) {
            session.tokenize(PiiType.EMAIL, "user" + i + "@example.com");
        }
        String text = "Send to [EMAIL_10] not [EMAIL_1]";
        String out = rehydrator.rehydrate(text, session);
        assertThat(out).isEqualTo("Send to user10@example.com not user1@example.com");
    }

    @Test
    @DisplayName("session context id is stable for Phileas scoping")
    void sessionContextIdStable() {
        PiiRedactionSession session = new PiiRedactionSession();
        assertThat(session.contextId()).isNotBlank();
        assertThat(session.contextId()).isEqualTo(session.contextId());
    }
}
