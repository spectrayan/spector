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

import com.spectrayan.spector.synapse.security.config.SecurityProperties.PiiProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiInterceptor")
class PiiInterceptorTest {

    private PiiInterceptor interceptor(PiiLevel level, boolean enabled) {
        PiiProperties props = new PiiProperties();
        props.setEnabled(enabled);
        props.setLevel(level);
        return new PiiInterceptor(props);
    }

    @Test
    @DisplayName("protect redacts outbound and rehydrates inbound")
    void protectRoundTrip() {
        PiiInterceptor interceptor = interceptor(PiiLevel.RELAXED, true);

        String result = interceptor.protect(
                "Reach me at jane@example.com",
                redacted -> {
                    assertThat(redacted).contains("[EMAIL_1]");
                    assertThat(redacted).doesNotContain("jane@example.com");
                    return "Got it, emailing [EMAIL_1] now.";
                });

        assertThat(result).isEqualTo("Got it, emailing jane@example.com now.");
    }

    @Test
    @DisplayName("disabled interceptor is a no-op")
    void disabledNoOp() {
        PiiInterceptor interceptor = interceptor(PiiLevel.STRICT, false);
        String input = "SSN 123-45-6789";

        String result = interceptor.protect(input, msg -> {
            assertThat(msg).isEqualTo(input);
            return "ack " + msg;
        });

        assertThat(result).isEqualTo("ack " + input);
    }

    @Test
    @DisplayName("RELAXED does not redact phones")
    void relaxedSkipsPhone() {
        PiiInterceptor interceptor = interceptor(PiiLevel.RELAXED, true);

        String result = interceptor.protect(
                "Call (512) 555-9999",
                redacted -> {
                    assertThat(redacted).contains("(512) 555-9999");
                    return redacted;
                });

        assertThat(result).contains("(512) 555-9999");
    }

    @Test
    @DisplayName("active session shares tokens with nested redactUsingActiveSession")
    void activeSessionShared() {
        PiiInterceptor interceptor = interceptor(PiiLevel.RELAXED, true);

        String result = interceptor.protect(
                "User alice@example.com",
                redacted -> {
                    String tool = interceptor.redactUsingActiveSession("Also bob@example.com");
                    assertThat(tool).contains("[EMAIL_2]");
                    return "Users [EMAIL_1] and [EMAIL_2]";
                });

        assertThat(result).isEqualTo("Users alice@example.com and bob@example.com");
    }

    @Test
    @DisplayName("typeCounts expose counts without values")
    void typeCountsSafe() {
        PiiInterceptor interceptor = interceptor(PiiLevel.RELAXED, true);
        PiiRedactionResult result = interceptor.redact("a@b.com and 123-45-6789");

        assertThat(result.typeCounts()).containsKeys(PiiType.EMAIL, PiiType.SSN);
        assertThat(result.typeCounts().toString()).doesNotContain("a@b.com");
        assertThat(result.typeCounts().toString()).doesNotContain("123-45-6789");
    }
}
