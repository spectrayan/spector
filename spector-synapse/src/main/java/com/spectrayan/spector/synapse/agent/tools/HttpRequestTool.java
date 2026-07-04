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
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.synapse.agent.AgentTool;
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
public class HttpRequestTool implements AgentTool {

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
    public Map<String, Object> parameterSchema() {
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
    public String execute(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        String method = (String) arguments.getOrDefault("method", "GET");
        String body = (String) arguments.get("body");

        if (url == null || url.isBlank()) {
            return "Error: 'url' argument is required";
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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
                    method, url, response.statusCode(), response.body().length());
            return response.body();
        } catch (Exception e) {
            log.warn("[HttpRequest] Failed {} {}: {}", method, url, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
