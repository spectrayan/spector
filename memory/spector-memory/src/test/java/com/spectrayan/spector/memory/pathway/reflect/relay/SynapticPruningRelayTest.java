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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.config.properties.CircadianProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SynapticPruningRelay: NREM Deep Sleep Pruning Tests")
class SynapticPruningRelayTest {

    @Test
    @DisplayName("transmit returns true")
    void testPruningRelayTransmit() {
        ReflectSignal signal = ReflectSignal.builder()
                .policy(CircadianProperties.builder().decayPruneThreshold(0.05f).build())
                .build();

        SynapticPruningRelay relay = new SynapticPruningRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
    }
}
