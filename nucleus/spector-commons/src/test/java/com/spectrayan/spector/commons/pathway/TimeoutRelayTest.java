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
package com.spectrayan.spector.commons.pathway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimeoutRelay")
class TimeoutRelayTest {

    static class Signal extends AbstractSignal {
        boolean executed = false;
    }

    @Test
    @DisplayName("Executes delegate successfully within budget")
    void completesWithinBudget() throws Exception {
        SynapticRelay<Signal> delegate = s -> {
            s.executed = true;
            return true;
        };

        var relay = new TimeoutRelay<>(delegate, Duration.ofMillis(500), "test-timeout");
        var signal = new Signal();

        boolean continued = relay.transmit(signal);

        assertThat(continued).isTrue();
        assertThat(signal.executed).isTrue();
    }

    @Test
    @DisplayName("Throws CognitivePathwayException with FaultKind.TRANSIENT on timeout")
    void throwsOnTimeout() {
        SynapticRelay<Signal> delegate = s -> {
            Thread.sleep(500);
            return true;
        };

        var relay = new TimeoutRelay<>(delegate, Duration.ofMillis(50), "slow-relay");
        var signal = new Signal();

        assertThatThrownBy(() -> relay.transmit(signal))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.kind()).isEqualTo(FaultKind.TRANSIENT);
                    assertThat(cpe.relayName()).isEqualTo("slow-relay");
                });
    }
}
