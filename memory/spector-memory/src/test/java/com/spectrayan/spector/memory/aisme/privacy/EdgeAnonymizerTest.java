/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.aisme.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Unit tests for {@link EdgeAnonymizer}.
 */
class EdgeAnonymizerTest {

    private EdgeAnonymizer anonymizer;

    @BeforeEach
    void setUp() {
        anonymizer = new EdgeAnonymizer("spector-test-salt-2026");
    }

    @Test
    @DisplayName("anonymize replaces email with deterministic pseudonym")
    void anonymize_replacesEmailWithPseudonym() {
        String input = "User alice.smith@example.org reported a memory error.";
        String result = anonymizer.anonymize(input);

        assertThat(result).doesNotContain("alice.smith@example.org");
        assertThat(result).contains("[EMAIL_");
    }

    @Test
    @DisplayName("anonymize is deterministic across multiple calls with same salt")
    void anonymize_isDeterministic() {
        String email = "john.doe@company.com";
        String pseudo1 = anonymizer.pseudonymize(email, "EMAIL");
        String pseudo2 = anonymizer.pseudonymize(email, "EMAIL");

        assertThat(pseudo1).isEqualTo(pseudo2);

        EdgeAnonymizer otherAnonymizer = new EdgeAnonymizer("different-salt");
        String otherPseudo = otherAnonymizer.pseudonymize(email, "EMAIL");

        assertThat(pseudo1).isNotEqualTo(otherPseudo);
    }

    @Test
    @DisplayName("anonymize redacts phone numbers, SSNs, credit cards, and API keys")
    void anonymize_redactsVariousPii() {
        String text = "Contact +1-555-234-5678 or SSN 123-45-6789 with card 4111 2222 3333 4444 and api_key='sk-1234567890abcdef1234567890'";
        String result = anonymizer.anonymize(text);

        assertThat(result).doesNotContain("555-234-5678");
        assertThat(result).contains("[PHONE_");

        assertThat(result).doesNotContain("123-45-6789");
        assertThat(result).contains("[SSN_");

        assertThat(result).doesNotContain("4111 2222 3333 4444");
        assertThat(result).contains("[REDACTED_CARD]");

        assertThat(result).doesNotContain("sk-1234567890abcdef1234567890");
        assertThat(result).contains("[REDACTED_SECRET]");
    }

    @Test
    @DisplayName("anonymize redacts AWS keys and PEM private keys")
    void anonymize_redactsCloudKeysAndPem() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0...\n-----END RSA PRIVATE KEY-----";
        String aws = "AWS access key: AKIAIOSFODNN7EXAMPLE";

        String resPem = anonymizer.anonymize(pem);
        assertThat(resPem).isEqualTo("[REDACTED_PRIVATE_KEY]");

        String resAws = anonymizer.anonymize(aws);
        assertThat(resAws).contains("[REDACTED_AWS_KEY]");
        assertThat(resAws).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    @DisplayName("anonymizeTags sanitizes set of tags")
    void anonymizeTags_sanitizesTagCollection() {
        Set<String> tags = Set.of("user:bob@example.com", "public-tag");
        Set<String> sanitized = anonymizer.anonymizeTags(tags);

        assertThat(sanitized).hasSize(2);
        assertThat(sanitized).anyMatch(t -> t.contains("[EMAIL_"));
        assertThat(sanitized).contains("public-tag");
    }

    @Test
    @DisplayName("EdgeAnonymizer rejects null or blank salt")
    void constructor_rejectsBlankSalt() {
        assertThatThrownBy(() -> new EdgeAnonymizer(null))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> new EdgeAnonymizer("   "))
                .isInstanceOf(SpectorValidationException.class);
    }
}
