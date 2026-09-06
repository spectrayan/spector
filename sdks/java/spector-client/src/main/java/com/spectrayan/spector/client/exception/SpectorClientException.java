/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.client.exception;

/**
 * Base runtime exception for all errors encountered by the Spector Client SDK.
 */
public class SpectorClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public SpectorClientException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public SpectorClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public SpectorClientException(int statusCode, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * HTTP status code returned by the server, or -1 if the error occurred prior to receiving a response.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Raw HTTP response body returned by the server, or {@code null} if none was received.
     */
    public String getResponseBody() {
        return responseBody;
    }
}
