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
package com.spectrayan.spector.client.exception;

/**
 * Exception thrown when a request fails validation or contains invalid parameters (HTTP 400/422).
 */
public class SpectorValidationException extends SpectorClientException {

    public SpectorValidationException(int statusCode, String message, String responseBody, Throwable cause) {
        super(statusCode, message, responseBody, cause);
    }
}
