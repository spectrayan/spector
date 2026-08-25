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
package com.spectrayan.spector.cli.client;

/**
 * Thrown when the CLI client cannot connect to a Spector server instance.
 */
public class SpectorConnectionException extends SpectorClientException {

    private final String host;
    private final int port;

    public SpectorConnectionException(String host, int port, Throwable cause) {
        super("Cannot connect to Spector server at " + host + ":" + port, cause);
        this.host = host;
        this.port = port;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }
}
