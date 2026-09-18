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
package com.spectrayan.spector.memory.aisme.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.lifespan.LifespanRetentionController;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LifespanAdaptivePruningRelay}.
 */
class LifespanAdaptivePruningRelayTest {

    private LifespanAdaptivePruningRelay relay;
    private LifespanRetentionController controller;

    @BeforeEach
    void setUp() {
        relay = new LifespanAdaptivePruningRelay();
        controller = new LifespanRetentionController(AismeProperties.defaultConfig());
    }

    @Test
    void transmit_nullSignal_returnsTrue() {
        assertThat(relay.transmit(null)).isTrue();
    }

    @Test
    void transmit_signalWithoutController_returnsTrue() {
        ReflectSignal signal = ReflectSignal.builder().build();
        assertThat(relay.transmit(signal)).isTrue();
    }

    @Test
    void transmit_withController_advancesEpochAndSetsTau() {
        ReflectSignal signal = ReflectSignal.builder()
                .lifespanController(controller)
                .build();

        long epochBefore = controller.getEpoch();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
        assertThat(controller.getEpoch()).isEqualTo(epochBefore + 1);
        assertThat(signal.effectiveLifespanTau()).isGreaterThan(0.0f);
    }
}
