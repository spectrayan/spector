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
package com.spectrayan.spector.memory.aisme.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.privacy.EdgeAnonymizer;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EdgeAnonymizationRelay}.
 */
class EdgeAnonymizationRelayTest {

    @Test
    @DisplayName("transmit sanitizes text and tags when privacy is enabled")
    void transmit_sanitizesSignal() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyAnonymizePii(true)
                .privacyPseudonymizationSalt("test-salt")
                .build();

        EdgeAnonymizer anonymizer = new EdgeAnonymizer("test-salt");
        EdgeAnonymizationRelay relay = new EdgeAnonymizationRelay(config, anonymizer);

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Met with john.doe@company.com at +1-555-123-4567",
                new float[]{0.1f, 0.2f},
                MemoryType.EPISODIC,
                new String[]{"author:john.doe@company.com", "public"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );

        boolean passed = relay.transmit(signal);

        assertThat(passed).isTrue();
        assertThat(signal.text()).contains("[EMAIL_");
        assertThat(signal.text()).contains("[PHONE_");
        assertThat(signal.text()).doesNotContain("john.doe@company.com");
        assertThat(signal.text()).doesNotContain("555-123-4567");

        assertThat(signal.tags()).hasSize(2);
        assertThat(signal.tags()[0]).contains("[EMAIL_");
        assertThat(signal.tags()[1]).isEqualTo("public");
    }

    @Test
    @DisplayName("transmit preserves original text when privacy is disabled")
    void transmit_preservesOriginalWhenDisabled() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(false)
                .build();

        EdgeAnonymizer anonymizer = new EdgeAnonymizer("test-salt");
        EdgeAnonymizationRelay relay = new EdgeAnonymizationRelay(config, anonymizer);

        String originalText = "Meeting with ceo@spectrayan.com";
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-2",
                originalText,
                new float[]{0.1f, 0.2f},
                MemoryType.EPISODIC,
                new String[]{"ceo"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );

        relay.transmit(signal);

        assertThat(signal.text()).isEqualTo(originalText);
        assertThat(signal.sanitizedText()).isNull();
    }
}
