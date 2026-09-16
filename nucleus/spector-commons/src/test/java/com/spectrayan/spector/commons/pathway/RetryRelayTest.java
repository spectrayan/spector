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

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryRelay")
class RetryRelayTest {

    static class Signal extends AbstractSignal {}

    @Test
    @DisplayName("Retries on TRANSIENT failure and succeeds on subsequent attempt")
    void retriesAndSucceeds() throws Exception {
        var attempts = new AtomicInteger(0);
        SynapticRelay<Signal> delegate = s -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException("temporary network glitch");
            }
            return true;
        };

        var policy = RetryPolicy.of(3, Duration.ofMillis(1), 0.0, FaultKind.TRANSIENT);
        var relay = new RetryRelay<>(delegate, policy, "flaky-relay");

        boolean continued = relay.transmit(new Signal());

        assertThat(continued).isTrue();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Does not retry on non-retryable failure like IllegalArgumentException (VALIDATION/CONTRACT)")
    void doesNotRetryNonRetryable() {
        var attempts = new AtomicInteger(0);
        SynapticRelay<Signal> delegate = s -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("invalid argument");
        };

        var policy = RetryPolicy.of(3, Duration.ofMillis(1), 0.0, FaultKind.TRANSIENT);
        var relay = new RetryRelay<>(delegate, policy, "validation-relay");

        // RetryRelay propagates the delegate's own exception unchanged; this delegate
        // throws a raw IllegalArgumentException, so that is what surfaces. The point
        // of the test is the attempt count, not the type.
        assertThatThrownBy(() -> relay.transmit(new Signal()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Throws last exception when all attempts exhausted")
    void throwsLastExceptionWhenExhausted() {
        var attempts = new AtomicInteger(0);
        SynapticRelay<Signal> delegate = s -> {
            attempts.incrementAndGet();
            throw new IOException("persistent error");
        };

        var policy = RetryPolicy.of(3, Duration.ofMillis(1), 0.0, FaultKind.TRANSIENT);
        var relay = new RetryRelay<>(delegate, policy, "failing-relay");

        assertThatThrownBy(() -> relay.transmit(new Signal()))
                .isInstanceOf(IOException.class)
                .hasMessage("persistent error");

        assertThat(attempts.get()).isEqualTo(3);
    }
}
