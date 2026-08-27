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
package com.spectrayan.spector.memory.decide.relay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRelayTest {

    @Test
    void testExperimentRelayTransmit() {
        ExperimentRelay relay = new ExperimentRelay();
        assertThat(relay.relayName()).isEqualTo("experiment_relay");

        boolean result = relay.transmit(null);
        assertThat(result).isTrue();
    }
}
