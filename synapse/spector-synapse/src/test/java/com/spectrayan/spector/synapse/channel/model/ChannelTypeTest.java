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
package com.spectrayan.spector.synapse.channel.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelTypeTest {

    @Test
    void allExpectedChannelsAreDefined() {
        assertThat(ChannelType.values()).containsExactly(
                ChannelType.WEBCHAT,
                ChannelType.SLACK,
                ChannelType.TELEGRAM,
                ChannelType.WHATSAPP,
                ChannelType.DISCORD,
                ChannelType.EMAIL,
                ChannelType.MSTEAMS,
                ChannelType.GOOGLE_CHAT,
                ChannelType.SIGNAL,
                ChannelType.SMS
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"slack", "SLACK", "Slack", "  slack  "})
    void fromIdResolvesCaseInsensitively(String input) {
        var channel = ChannelType.fromId(input);
        assertThat(channel).isPresent().contains(ChannelType.SLACK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"telegram", "whatsapp", "discord", "email", "msteams", "googlechat", "signal", "sms", "webchat"})
    void fromIdResolvesAllSupportedIds(String id) {
        var channel = ChannelType.fromId(id);
        assertThat(channel).isPresent();
        assertThat(channel.get().id()).isEqualTo(id);
    }

    @Test
    void fromIdReturnsEmptyForInvalidOrNull() {
        assertThat(ChannelType.fromId(null)).isEmpty();
        assertThat(ChannelType.fromId("")).isEmpty();
        assertThat(ChannelType.fromId("   ")).isEmpty();
        assertThat(ChannelType.fromId("unknown_channel")).isEmpty();
    }

    @Test
    void metadataPropertiesAreConsistent() {
        for (ChannelType type : ChannelType.values()) {
            assertThat(type.id()).isNotBlank();
            assertThat(type.displayName()).isNotBlank();
            assertThat(type.isBidirectional()).isTrue();
        }
    }
}
