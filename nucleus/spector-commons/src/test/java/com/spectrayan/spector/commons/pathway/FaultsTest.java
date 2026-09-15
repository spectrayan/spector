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

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Faults Classification")
class FaultsTest {

    @Test
    @DisplayName("null throwable maps to INTERNAL")
    void nullThrowable() {
        assertThat(Faults.kindOf(null)).isEqualTo(FaultKind.INTERNAL);
    }

    @Test
    @DisplayName("Validation exception maps to VALIDATION")
    void validationException() {
        var ex = new SpectorValidationException(ErrorCode.DIMENSIONS_INVALID, "invalid input");
        assertThat(Faults.kindOf(ex)).isEqualTo(FaultKind.VALIDATION);
    }

    @Test
    @DisplayName("Timeout and I/O exceptions map to TRANSIENT")
    void timeoutAndIoExceptions() {
        assertThat(Faults.kindOf(new TimeoutException())).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindOf(new HttpTimeoutException("http timeout"))).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindOf(new IOException("connection reset"))).isEqualTo(FaultKind.TRANSIENT);
    }

    @Test
    @DisplayName("InterruptedException maps to INTERRUPTED")
    void interruptedException() {
        assertThat(Faults.kindOf(new InterruptedException())).isEqualTo(FaultKind.INTERRUPTED);
    }

    @Test
    @DisplayName("Unwraps ConcurrentExecutionException and classifies root cause")
    void unwrapsConcurrentExecutionException() {
        var cause = new IOException("socket closed");
        var cee = new ConcurrentExecutionException("Concurrent failure", cause);
        assertThat(Faults.kindOf(cee)).isEqualTo(FaultKind.TRANSIENT);

        var valCause = new SpectorValidationException(ErrorCode.DIMENSIONS_INVALID, "bad data");
        var ceeVal = new ConcurrentExecutionException("Validation failure", valCause);
        assertThat(Faults.kindOf(ceeVal)).isEqualTo(FaultKind.VALIDATION);
    }

    @Test
    @DisplayName("Unwraps ExecutionException and CompletionException")
    void unwrapsExecutionAndCompletionExceptions() {
        var ee = new ExecutionException("execution failed", new TimeoutException());
        assertThat(Faults.kindOf(ee)).isEqualTo(FaultKind.TRANSIENT);

        var ce = new CompletionException("completion failed", new InterruptedException());
        assertThat(Faults.kindOf(ce)).isEqualTo(FaultKind.INTERRUPTED);
    }

    @Test
    @DisplayName("CognitivePathwayException preserves kind")
    void preservesCognitivePathwayExceptionKind() {
        var cpe = new CognitivePathwayException("test-pathway", "test-relay", FaultKind.CONTRACT, false, null);
        assertThat(Faults.kindOf(cpe)).isEqualTo(FaultKind.CONTRACT);
    }

    @Test
    @DisplayName("Maps pathway ErrorCodes correctly")
    void mapsPathwayErrorCodes() {
        assertThat(Faults.kindFrom(ErrorCode.PATHWAY_TIMEOUT)).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindFrom(ErrorCode.PATHWAY_CIRCUIT_OPEN)).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindFrom(ErrorCode.PATHWAY_BULKHEAD)).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindFrom(ErrorCode.PATHWAY_CYCLE)).isEqualTo(FaultKind.CONTRACT);
        assertThat(Faults.kindFrom(ErrorCode.EMBEDDING_UNAVAILABLE)).isEqualTo(FaultKind.TRANSIENT);
        assertThat(Faults.kindFrom(ErrorCode.INTERNAL_ERROR)).isEqualTo(FaultKind.INTERNAL);
        assertThat(Faults.kindFrom(null)).isEqualTo(FaultKind.INTERNAL);
    }
}
