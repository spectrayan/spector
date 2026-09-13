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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiDetector")
class PiiDetectorTest {

    private final PiiDetector detector = new PiiDetector();

    @Test
    @DisplayName("detects and classifies email addresses at all levels")
    void detectsEmails() {
        String text = "Contact john.doe@example.com for details.";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.RELAXED);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().type()).isEqualTo(PiiType.EMAIL);
        assertThat(matches.getFirst().value()).isEqualTo("john.doe@example.com");
    }

    @Test
    @DisplayName("RELAXED detects SSN and Luhn-valid credit cards")
    void relaxedHighConfidence() {
        String text = "SSN 123-45-6789 card 4111111111111111";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.RELAXED);

        assertThat(matches).extracting(PiiMatch::type)
                .containsExactlyInAnyOrder(PiiType.SSN, PiiType.CREDIT_CARD);
    }

    @Test
    @DisplayName("RELAXED ignores phone numbers")
    void relaxedIgnoresPhone() {
        String text = "Call me at (512) 555-1234 please.";
        assertThat(detector.detect(text, PiiLevel.RELAXED)).isEmpty();
    }

    @Test
    @DisplayName("MODERATE detects phone and IPv4")
    void moderatePhoneAndIp() {
        String text = "Reach (512) 555-1234 from 192.168.1.10";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.MODERATE);

        assertThat(matches).extracting(PiiMatch::type)
                .contains(PiiType.PHONE, PiiType.IP_ADDRESS);
    }

    @Test
    @DisplayName("STRICT detects CapWord person names via heuristic NER stub")
    void strictDetectsPersonNames() {
        String text = "Please ask John Smith about the invoice.";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.STRICT);

        assertThat(matches).anyMatch(m -> m.type() == PiiType.PERSON
                && m.value().contains("John Smith"));
    }

    @Test
    @DisplayName("MODERATE detects honorific person names but not bare CapWords")
    void moderateHonorificOnly() {
        assertThat(detector.detect("Ask Mr. John Smith tomorrow.", PiiLevel.MODERATE))
                .anyMatch(m -> m.type() == PiiType.PERSON);

        assertThat(detector.detect("Please ask John Smith tomorrow.", PiiLevel.MODERATE))
                .noneMatch(m -> m.type() == PiiType.PERSON);
    }

    @Test
    @DisplayName("rejects credit cards that fail Luhn")
    void rejectsInvalidCards() {
        String text = "Card 4111111111111112 is invalid.";
        assertThat(detector.detect(text, PiiLevel.RELAXED))
                .noneMatch(m -> m.type() == PiiType.CREDIT_CARD);
    }

    @Test
    @DisplayName("loads patterns from classpath YAML")
    void loadsYamlPatterns() {
        assertThat(detector.patterns()).isNotEmpty();
        assertThat(detector.patterns()).anyMatch(p -> p.id().equals("email"));
    }

    @Test
    @DisplayName("passesLuhn accepts known Visa test number")
    void luhnAcceptsVisaTest() {
        assertThat(PiiDetector.passesLuhn("4111-1111-1111-1111")).isTrue();
        assertThat(PiiDetector.passesLuhn("4111111111111112")).isFalse();
    }
}
