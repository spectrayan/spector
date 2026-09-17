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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Transport abstraction for forwarding HTTP requests from gateway to owner nodes with streaming I/O
 * (ADR-0034 §8, ADR-0081 §8, Req R7.1, R7.2).
 */
@FunctionalInterface
public interface GatewayHttpTransport {

    /**
     * Dispatches an HTTP request to the designated owner node.
     *
     * @param targetNodeId   target node identifier
     * @param targetUrl      target base or full URL
     * @param request        forward request details
     * @param routingHeaders routing metadata headers to attach (X-Spector-*)
     * @return forwarded response
     * @throws Exception if transport-level network error occurs
     */
    ForwardResponse send(
            String targetNodeId,
            String targetUrl,
            ForwardRequest request,
            Map<String, String> routingHeaders
    ) throws Exception;

    /**
     * Default transport using JDK's standard {@link HttpClient} with streaming body publishers and handlers.
     *
     * @param timeout connection and request timeout
     * @return default JDK HttpClient transport
     */
    static GatewayHttpTransport defaultJdkTransport(Duration timeout) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        return (targetNodeId, targetUrl, req, routingHeaders) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + req.uriPath()))
                    .timeout(timeout != null ? timeout : Duration.ofSeconds(10));

            if (req.contentLength() > 0 || (req.contentLength() < 0 && req.bodySupplier() != null)) {
                builder.method(req.method(), HttpRequest.BodyPublishers.ofInputStream(req.bodySupplier()));
            } else if (("POST".equalsIgnoreCase(req.method()) || "PUT".equalsIgnoreCase(req.method()) || "PATCH".equalsIgnoreCase(req.method())) && req.contentLength() == 0) {
                builder.method(req.method(), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(req.method(), HttpRequest.BodyPublishers.noBody());
            }

            // Copy incoming headers, filtering hop-by-hop headers
            for (Map.Entry<String, List<String>> entry : req.headers().entrySet()) {
                String name = entry.getKey();
                if (!"host".equalsIgnoreCase(name)
                        && !"content-length".equalsIgnoreCase(name)
                        && !"connection".equalsIgnoreCase(name)
                        && !"transfer-encoding".equalsIgnoreCase(name)) {
                    for (String val : entry.getValue()) {
                        builder.header(name, val);
                    }
                }
            }

            // Attach authoritative routing headers
            for (Map.Entry<String, String> entry : routingHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            // Attach client idempotency key if present (Invariant K7)
            if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
                builder.header("Idempotency-Key", req.idempotencyKey());
            }

            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            return new ForwardResponse(
                    response.statusCode(),
                    response.headers().map(),
                    response.body(),
                    targetNodeId,
                    1
            );
        };
    }
}
