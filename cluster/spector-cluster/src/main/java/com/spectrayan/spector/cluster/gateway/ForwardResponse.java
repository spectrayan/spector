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
package com.spectrayan.spector.cluster.gateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable response representation from forwarded gateway execution with streaming body support
 * (ADR-0034 §8, ADR-0081 §8, Req R7.4).
 *
 * @param statusCode   HTTP response status code
 * @param headers      response headers
 * @param bodyStream   streaming response body
 * @param finalOwnerId node identifier that handled the final execution attempt
 * @param attempts     total forwarding attempts made (including bounded retries)
 */
public record ForwardResponse(
        int statusCode,
        Map<String, List<String>> headers,
        InputStream bodyStream,
        String finalOwnerId,
        int attempts
) implements AutoCloseable {

    public ForwardResponse {
        headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
        bodyStream = bodyStream != null ? bodyStream : InputStream.nullInputStream();
    }

    /**
     * Convenience constructor creating a response backed by an in-memory byte array.
     */
    public ForwardResponse(
            int statusCode,
            Map<String, List<String>> headers,
            byte[] body,
            String finalOwnerId,
            int attempts
    ) {
        this(
                statusCode,
                headers,
                new ByteArrayInputStream(body != null ? body : new byte[0]),
                finalOwnerId,
                attempts
        );
    }

    /**
     * Reads all bytes from the underlying body stream.
     * Note: Once read, the stream cannot be re-read unless backed by a reusable stream.
     */
    public byte[] body() {
        if (bodyStream == null) {
            return new byte[0];
        }
        try {
            return bodyStream.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    @Override
    public void close() throws IOException {
        if (bodyStream != null) {
            bodyStream.close();
        }
    }
}
