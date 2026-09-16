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
import com.spectrayan.spector.commons.error.ErrorCategory;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Classification utility for mapping exceptions and error codes to {@link FaultKind}.
 */
public final class Faults {

    private Faults() {
        // utility class
    }

    /**
     * Classifies a throwable into its corresponding {@link FaultKind}.
     *
     * <p>Unwraps wrapper exceptions such as {@link ConcurrentExecutionException},
     * {@link ExecutionException}, and {@link CompletionException} to classify
     * the underlying root cause.</p>
     *
     * @param t the throwable to classify
     * @return the classified fault kind, never null
     */
    public static FaultKind kindOf(Throwable t) {
        if (t == null) {
            return FaultKind.INTERNAL;
        }
        if (t instanceof ConcurrentExecutionException cee && cee.getCause() != null) {
            return kindOf(cee.getCause());
        }
        if ((t instanceof ExecutionException || t instanceof CompletionException) && t.getCause() != null) {
            return kindOf(t.getCause());
        }
        if (t instanceof SpectorValidationException) {
            return FaultKind.VALIDATION;
        }
        if (t instanceof CognitivePathwayException cpe && cpe.kind() != null) {
            return cpe.kind();
        }
        if (t instanceof TimeoutException || t instanceof HttpTimeoutException || t instanceof IOException) {
            return FaultKind.TRANSIENT;
        }
        if (t instanceof InterruptedException) {
            return FaultKind.INTERRUPTED;
        }
        if (t instanceof SpectorException se) {
            return kindFrom(se.errorCode());
        }
        return FaultKind.INTERNAL;
    }

    /**
     * Maps an {@link ErrorCode} to its corresponding {@link FaultKind}.
     *
     * @param ec the error code
     * @return the classified fault kind
     */
    public static FaultKind kindFrom(ErrorCode ec) {
        if (ec == null) {
            return FaultKind.INTERNAL;
        }
        if (ec == ErrorCode.PATHWAY_TIMEOUT
                || ec == ErrorCode.PATHWAY_CIRCUIT_OPEN
                || ec == ErrorCode.PATHWAY_BULKHEAD) {
            return FaultKind.TRANSIENT;
        }
        if (ec == ErrorCode.PATHWAY_CYCLE) {
            return FaultKind.CONTRACT;
        }
        if (ec.category() == ErrorCategory.VALIDATION) {
            return FaultKind.VALIDATION;
        }
        if (ec.category() == ErrorCategory.CONFIG) {
            return FaultKind.CONTRACT;
        }
        if (ec == ErrorCode.EMBEDDING_UNAVAILABLE
                || ec == ErrorCode.EMBEDDING_TIMEOUT
                || ec == ErrorCode.EMBEDDING_REQUEST_FAILED) {
            return FaultKind.TRANSIENT;
        }
        if (ec == ErrorCode.API_SERVICE_UNAVAILABLE
                || ec == ErrorCode.CLIENT_TIMEOUT
                || ec == ErrorCode.CLIENT_CONNECTION_FAILED) {
            return FaultKind.TRANSIENT;
        }
        return FaultKind.INTERNAL;
    }
}
