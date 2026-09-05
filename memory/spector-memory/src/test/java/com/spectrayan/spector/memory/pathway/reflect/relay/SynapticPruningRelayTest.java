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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SynapticPruningRelay: NREM Deep Sleep Pruning Tests")
class SynapticPruningRelayTest {

    @Test
    @DisplayName("transmit returns true")
    void testPruningRelayTransmit() {
        ReflectSignal signal = ReflectSignal.builder()
                .policy(CircadianPolicy.builder().decayPruneThreshold(0.05f).build())
                .build();

        SynapticPruningRelay relay = new SynapticPruningRelay();
        boolean success = relay.transmit(signal);

        assertThat(success).isTrue();
    }
}
