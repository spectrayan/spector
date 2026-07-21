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
package com.spectrayan.spector.synapse.agent.tools;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP request tool — makes HTTP GET/POST requests to URLs.
 */
@Component
public class HttpRequestTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestTool.class);
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() { return "http_request"; }

    @Override
    public String description() {
        return "Make an HTTP request to a URL. Supports GET and POST methods.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of("type", "string", "description", "The URL to request"),
                        "method", Map.of("type", "string", "description", "HTTP method (GET or POST)", "default", "GET"),
                        "body", Map.of("type", "string", "description", "Request body (for POST)")
                ),
                "required", java.util.List.of("url")
        );
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.NETWORK;
    }

    @Override
    public io.modelcontextprotocol.spec.McpSchema.CallToolResult execute(com.spectrayan.spector.runtime.SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        return textResult(executeInternal(args));
    }

    private String executeInternal(Map<String, Object> arguments) throws Exception {
        String url = (String) arguments.get("url");
        String method = (String) arguments.getOrDefault("method", "GET");
        String body = (String) arguments.get("body");

        if (url == null || url.isBlank()) {
            return "Error: 'url' argument is required";
        }

        try {
            URI validatedUri = validateAndSanitizeUrl(url);
            // codeql[java/ssrf] - URL is validated against loopback, private, and internal IP ranges in validateAndSanitizeUrl
            HttpRequest.Builder builder = HttpRequest.newBuilder(validatedUri)
                    .timeout(Duration.ofSeconds(30));

            if ("POST".equalsIgnoreCase(method) && body != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body))
                       .header("Content-Type", "application/json");
            } else {
                builder.GET();
            }

            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            log.debug("[HttpRequest] {} {} → {} ({} bytes)",
                    method, validatedUri, response.statusCode(), response.body().length());
            return response.body();
        } catch (Exception e) {
            log.warn("[HttpRequest] Failed {} {}: {}", method, url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private URI validateAndSanitizeUrl(String urlString) throws Exception {
        URI uri = new URI(urlString);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only HTTP and HTTPS protocols are supported");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid host in URL");
        }

        // Resolve host to IPs to prevent SSRF against internal/loopback networks
        java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
        for (java.net.InetAddress addr : addresses) {
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new SecurityException("Access to local/private IP address is blocked: " + addr.getHostAddress());
            }
        }
        return uri;
    }
}
