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
package com.spectrayan.spector.commons.error;

/**
 * Exception for connector engine, route lifecycle, template management,
 * and data exchange execution errors ({@code SPE-610-xxx}).
 *
 * <p>Thrown when connector engine initialization, template loading/validation,
 * route activation/stopping, pre-flight probing, or exchange execution fails.</p>
 *
 * @see ErrorCode#CONNECTOR_INIT_FAILED
 * @see ErrorCode#CONNECTOR_TEMPLATE_NOT_FOUND
 * @see ErrorCode#CONNECTOR_TEMPLATE_INVALID
 * @see ErrorCode#CONNECTOR_ROUTE_START_FAILED
 * @see ErrorCode#CONNECTOR_ROUTE_STOP_FAILED
 * @see ErrorCode#CONNECTOR_ROUTE_NOT_FOUND
 * @see ErrorCode#CONNECTOR_PROBE_FAILED
 * @see ErrorCode#CONNECTOR_INVOCATION_FAILED
 * @see ErrorCode#CONNECTOR_EXECUTION_FAILED
 * @see ErrorCode#CONNECTOR_CREDENTIAL_MISSING
 */
public class SpectorConnectorException extends SpectorException {

    public SpectorConnectorException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public SpectorConnectorException(ErrorCode errorCode, String preformattedMessage, boolean isPreformatted) {
        super(errorCode, preformattedMessage, isPreformatted);
    }

    public SpectorConnectorException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
