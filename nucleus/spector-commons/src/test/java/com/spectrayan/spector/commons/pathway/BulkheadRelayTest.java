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

import com.spectrayan.spector.commons.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BulkheadRelay")
class BulkheadRelayTest {

    static class Signal extends AbstractSignal {}

    @Test
    @DisplayName("Acquires and releases permit during successful transmission")
    void acquiresAndReleasesPermit() throws Exception {
        var sem = new Semaphore(1);
        var config = BulkheadConfig.of(1);
        var executed = new AtomicBoolean(false);

        SynapticRelay<Signal> delegate = s -> {
            executed.set(true);
            assertThat(sem.availablePermits()).isEqualTo(0);
            return true;
        };

        var relay = new BulkheadRelay<>(delegate, sem, config, "test-stage", "test-bulkhead");
        boolean continued = relay.transmit(new Signal());

        assertThat(continued).isTrue();
        assertThat(executed.get()).isTrue();
        assertThat(sem.availablePermits()).isEqualTo(1);
    }

    @Test
    @DisplayName("Throws PATHWAY_BULKHEAD on rejection when onReject is FAIL")
    void throwsOnRejectionWhenFail() {
        var sem = new Semaphore(0); // no permits available
        var config = BulkheadConfig.of(1, Duration.ZERO, OnReject.FAIL);

        SynapticRelay<Signal> delegate = s -> true;
        var relay = new BulkheadRelay<>(delegate, sem, config, "test-stage", "test-bulkhead");

        assertThatThrownBy(() -> relay.transmit(new Signal()))
                .isInstanceOf(CognitivePathwayException.class)
                .satisfies(e -> {
                    var cpe = (CognitivePathwayException) e;
                    assertThat(cpe.errorCode()).isEqualTo(ErrorCode.PATHWAY_BULKHEAD);
                    assertThat(cpe.kind()).isEqualTo(FaultKind.TRANSIENT);
                });
    }

    @Test
    @DisplayName("Marks outcome bypassed and continues when onReject is BYPASS")
    void marksBypassedWhenBypass() throws Exception {
        var sem = new Semaphore(0); // no permits available
        var config = BulkheadConfig.of(1, Duration.ZERO, OnReject.BYPASS);

        SynapticRelay<Signal> delegate = s -> false; // should not be called
        var relay = new BulkheadRelay<>(delegate, sem, config, "test-stage", "test-bulkhead");

        var ctx = DefaultPathwayContext.builder().build();
        var signal = new Signal();
        signal.bind(ctx);

        boolean continued = relay.transmit(signal);

        assertThat(continued).isTrue();
        assertThat(ctx.outcome().bypassedMarks())
                .anySatisfy(m -> assertThat(m.message()).contains("bulkhead:test-bulkhead"));
    }
}
