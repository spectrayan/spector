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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PiiDetector (Phileas facade)")
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
    @DisplayName("STRICT detects street addresses via Phileas identifier filter")
    void strictDetectsStreetAddress() {
        String text = "Mail invoices to 100 Main Street please.";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.STRICT);

        assertThat(matches).anyMatch(m -> m.type() == PiiType.ADDRESS
                && m.value().contains("100 Main Street"));
    }

    @Test
    @DisplayName("STRICT detects US ZIP codes via Phileas ZIP_CODE filter")
    void strictDetectsZipCode() {
        String text = "Ship to zip 90210 please.";
        List<PiiMatch> matches = detector.detect(text, PiiLevel.STRICT);

        assertThat(matches).anyMatch(m -> m.type() == PiiType.ADDRESS
                && m.value().contains("90210"));
    }

    @Test
    @DisplayName("STRICT does not invent PERSON spans without Ph-Eye NER")
    void strictDoesNotDetectBarePersonNames() {
        String text = "Please ask John Smith about the invoice.";
        assertThat(detector.detect(text, PiiLevel.STRICT))
                .noneMatch(m -> m.type() == PiiType.PERSON);
    }

    @Test
    @DisplayName("rejects credit cards that fail Luhn (Phileas validation)")
    void rejectsInvalidCards() {
        String text = "Card 4111111111111112 is invalid.";
        assertThat(detector.detect(text, PiiLevel.RELAXED))
                .noneMatch(m -> m.type() == PiiType.CREDIT_CARD);
    }

    @Test
    @DisplayName("loads PhiSQL policies for all three levels")
    void loadsClasspathPolicies() {
        assertThat(detector.hasPolicy(PiiLevel.RELAXED)).isTrue();
        assertThat(detector.hasPolicy(PiiLevel.MODERATE)).isTrue();
        assertThat(detector.hasPolicy(PiiLevel.STRICT)).isTrue();
    }

    @Test
    @DisplayName("fail-closed when a required PhiSQL policy is missing")
    void failClosedMissingPolicy() {
        assertThatThrownBy(() -> PhileasPiiEngine.loadPolicy("/security/does-not-exist.phisql"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fail-closed");
    }

    @Test
    @DisplayName("maps Phileas filter types onto Spector PiiType without leaking NER")
    void mapsFilterTypes() {
        assertThat(PhileasPiiEngine.mapType(ai.philterd.phileas.model.filtering.FilterType.EMAIL_ADDRESS))
                .isEqualTo(PiiType.EMAIL);
        assertThat(PhileasPiiEngine.mapType(ai.philterd.phileas.model.filtering.FilterType.ZIP_CODE))
                .isEqualTo(PiiType.ADDRESS);
        assertThat(PhileasPiiEngine.mapType(ai.philterd.phileas.model.filtering.FilterType.PH_EYE))
                .isNull();
        assertThat(PhileasPiiEngine.mapType(ai.philterd.phileas.model.filtering.FilterType.PERSON))
                .isNull();
    }
}
