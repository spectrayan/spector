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
package com.spectrayan.spector.synapse.dr;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;

/**
 * Thrown when restored snapshot artifacts fail integrity verification (preamble magic, layout ID,
 * or SHA-256 checksums).
 *
 * <p>Invariant V4: A restored namespace is either verifiably complete or strictly refuses to serve
 * (ADR-0034 §11.2, Req R3.2, R3.4, V4).</p>
 */
public class IntegrityVerificationException extends SpectorException {

    private final String namespaceId;
    private final String artifact;
    private final String failureMode;

    public IntegrityVerificationException(String namespaceId, String artifact, String failureMode, String message) {
        super(ErrorCode.RECORD_CRC_CORRUPTED, String.format(
                "Integrity verification failure on namespace '%s', artifact '%s' (failureMode=%s): %s",
                namespaceId, artifact, failureMode, message
        ));
        this.namespaceId = namespaceId;
        this.artifact = artifact;
        this.failureMode = failureMode;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getFailureMode() {
        return failureMode;
    }
}
