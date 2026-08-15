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
package com.spectrayan.spector.connector.model;

/**
 * Result of a connector connectivity test.
 *
 * @param success     whether the connection test passed
 * @param message     human-readable result message
 * @param latencyMs   round-trip latency in milliseconds
 */
public record ConnectionTestResult(boolean success, String message, long latencyMs) {

    public static ConnectionTestResult success(String message, long latencyMs) {
        return new ConnectionTestResult(true, message, latencyMs);
    }

    public static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message, -1);
    }
}
