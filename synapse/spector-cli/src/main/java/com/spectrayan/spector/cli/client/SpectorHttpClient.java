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

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * Lightweight HTTP client used by spectorctl CLI to interact with remote Spector server instances.
 */
public class SpectorHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SpectorHttpClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private SpectorHttpClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.requestTimeout = builder.requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(builder.connectTimeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public StatusResponse status() {
        return get("/api/v1/status", StatusResponse.class);
    }

    public IngestResponse ingest(IngestRequest request) {
        return post("/api/v1/documents", request, IngestResponse.class);
    }

    public SearchResponse search(SearchRequest request) {
        return post("/api/v1/search", request, SearchResponse.class);
    }

    public String remember(Map<String, Object> request) {
        return post("/api/v1/memory/remember", request, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> recall(Map<String, Object> request) {
        return post("/api/v1/memory/recall", request, Map.class);
    }

    public String forgetMemory(String id) {
        return delete("/api/v1/memory/" + id, String.class);
    }

    public String reinforceMemory(String id, byte valence) {
        String path = "/api/v1/memory/" + id + "/reinforce";
        return post(path, Map.of("valence", valence), String.class);
    }

    public String suppressMemory(String id, String action, String reason) {
        String path = "/api/v1/memory/" + id + "/suppress";
        return post(path, Map.of("action", action, "reason", reason), String.class);
    }

    public String resolveMemory(String id, boolean resolved) {
        String path = "/api/v1/memory/" + id + "/resolve";
        return post(path, Map.of("resolved", resolved), String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> memoryStatus() {
        return get("/api/v1/memory/status", Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> introspect(String topic) {
        return post("/api/v1/memory/introspect", Map.of("topic", topic), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> scheduleReminder(Map<String, Object> request) {
        return post("/api/v1/memory/reminder", request, Map.class);
    }

    public String scratchpad(String text) {
        return post("/api/v1/memory/scratchpad", Map.of("text", text), String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> whyNot(Map<String, Object> request) {
        return post("/api/v1/memory/why-not", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> reflect() {
        return post("/api/v1/memory/reflect", Map.of(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSalienceProfile() {
        return get("/api/v1/memory/salience", Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setSalienceProfile(Map<String, Object> profile) {
        return post("/api/v1/memory/salience", profile, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> computeSalienceBoost(Map<String, Object> request) {
        return post("/api/v1/memory/salience/compute", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> addInterest(Map<String, Object> request) {
        return post("/api/v1/memory/salience/interest", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> addDisinterest(Map<String, Object> request) {
        return post("/api/v1/memory/salience/disinterest", request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setPersonaContext(Map<String, Object> request) {
        return post("/api/v1/memory/salience/persona", request, Map.class);
    }

    @Override
    public void close() {
        log.debug("SpectorHttpClient closed for {}", baseUrl);
    }

    // ─────────────── Internal HTTP ───────────────

    private <T> T get(String path, Class<T> responseType) {
        return executeRequest(buildRequest("GET", path, null), path, responseType);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return executeRequest(buildRequest("POST", path, body), path, responseType);
    }

    private <T> T delete(String path, Class<T> responseType) {
        return executeRequest(buildRequest("DELETE", path, null), path, responseType);
    }

    private HttpRequest buildRequest(String method, String path, Object body) {
        var uri = URI.create(baseUrl + path);
        var reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("X-API-Key", apiKey);
        }

        if (body != null) {
            try {
                byte[] jsonBytes = objectMapper.writeValueAsBytes(body);
                reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(jsonBytes));
            } catch (Exception e) {
                throw new SpectorClientException("Failed to serialize request body: " + e.getMessage(), e);
            }
        } else {
            reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return reqBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private <T> T executeRequest(HttpRequest request, String path, Class<T> responseType) {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                String errorMessage = extractErrorMessage(response.body());
                throw new SpectorHttpException(statusCode, errorMessage, baseUrl + path);
            }

            if (responseType == String.class) {
                return (T) new String(response.body(), StandardCharsets.UTF_8);
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (SpectorClientException e) {
            throw e;
        } catch (ConnectException e) {
            throw new SpectorConnectionException(extractHost(), extractPort(), e);
        } catch (IOException e) {
            if (e.getCause() instanceof ConnectException ce) {
                throw new SpectorConnectionException(extractHost(), extractPort(), ce);
            }
            throw new SpectorConnectionException(extractHost(), extractPort(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectorClientException("Request interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SpectorClientException("Unexpected error during HTTP request: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return "No response body";
        }
        try {
            Map<?, ?> map = objectMapper.readValue(responseBody, Map.class);
            Object msg = map.get("message");
            if (msg != null) return msg.toString();
            Object err = map.get("error");
            if (err != null) return err.toString();
        } catch (Exception ignored) {
        }
        return new String(responseBody, StandardCharsets.UTF_8);
    }

    private String extractHost() {
        try {
            return URI.create(baseUrl).getHost();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private int extractPort() {
        try {
            int port = URI.create(baseUrl).getPort();
            return port > 0 ? port : 8080;
        } catch (Exception e) {
            return 8080;
        }
    }

    public static class Builder {
        private String host = "localhost";
        private int port = 8080;
        private String baseUrl;
        private String apiKey;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public SpectorHttpClient build() {
            if (baseUrl == null) {
                baseUrl = "http://" + host + ":" + port;
            }
            return new SpectorHttpClient(this);
        }
    }
}
