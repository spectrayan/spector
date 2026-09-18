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
