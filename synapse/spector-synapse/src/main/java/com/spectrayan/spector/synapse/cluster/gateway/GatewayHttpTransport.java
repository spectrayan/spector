/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.cluster.gateway;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport abstraction for forwarding HTTP requests from gateway to owner nodes (Req R7.1, R7.2).
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
     * Default transport using JDK's standard {@link HttpClient}.
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
                    .timeout(timeout != null ? timeout : Duration.ofSeconds(10))
                    .method(req.method(), req.body().length > 0
                            ? HttpRequest.BodyPublishers.ofByteArray(req.body())
                            : HttpRequest.BodyPublishers.noBody());

            // Copy incoming headers
            for (Map.Entry<String, List<String>> entry : req.headers().entrySet()) {
                String name = entry.getKey();
                // Filter out restricted or hop-by-hop headers
                if (!"host".equalsIgnoreCase(name)
                        && !"content-length".equalsIgnoreCase(name)
                        && !"connection".equalsIgnoreCase(name)) {
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

            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

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
