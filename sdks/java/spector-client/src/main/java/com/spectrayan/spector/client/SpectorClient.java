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
package com.spectrayan.spector.client;

import com.spectrayan.spector.client.api.MemoryClient;
import com.spectrayan.spector.client.generated.invoker.ApiClient;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Main entry point and lifecycle manager for the Spector Client SDK.
 *
 * <p>Construct instances using the fluent builder:</p>
 * <pre>{@code
 * try (SpectorClient client = SpectorClient.builder()
 *         .baseUri("http://localhost:8080")
 *         .apiKey("secret-api-key")
 *         .build()) {
 *     var response = client.memory().store("Important knowledge", List.of("ai", "spector"));
 * }
 * }</pre>
 */
public final class SpectorClient implements AutoCloseable {

    private final ApiClient apiClient;
    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final MemoryClient memoryClient;

    private SpectorClient(ApiClient apiClient, HttpClient httpClient, boolean ownsHttpClient) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.httpClient = httpClient;
        this.ownsHttpClient = ownsHttpClient;
        this.memoryClient = new MemoryClient(apiClient);
    }

    /**
     * Start configuring a new {@link SpectorClient} using the builder pattern.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a client configured with defaults targeting {@code http://localhost:8080}.
     */
    public static SpectorClient createDefault() {
        return builder().build();
    }

    /**
     * Access the ergonomic Memory API client.
     */
    public MemoryClient memory() {
        return memoryClient;
    }

    /**
     * Obtains an instance of any generated OpenAPI API class using its constructor reference.
     *
     * <pre>{@code
     * ChatControllerApi chatApi = client.getApi(ChatControllerApi::new);
     * }</pre>
     *
     * @param apiConstructor constructor accepting an {@link ApiClient}
     * @param <T>            the API type
     * @return initialized API instance configured with this client's credentials and base URI
     */
    public <T> T getApi(Function<ApiClient, T> apiConstructor) {
        Objects.requireNonNull(apiConstructor, "apiConstructor must not be null");
        return apiConstructor.apply(apiClient);
    }

    /**
     * Access the underlying low-level {@link ApiClient}.
     */
    public ApiClient rawApiClient() {
        return apiClient;
    }

    @Override
    public void close() {
        if (ownsHttpClient && httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * Fluent builder for {@link SpectorClient}.
     */
    public static final class Builder {

        private String baseUri = "http://localhost:8080";
        private String apiKey;
        private String bearerToken;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private HttpClient customHttpClient;
        private final Map<String, String> customHeaders = new LinkedHashMap<>();
        private Consumer<HttpRequest.Builder> customInterceptor;

        private Builder() {
        }

        /**
         * Sets the base URI of the Spector server (e.g. {@code http://localhost:8080} or {@code https://spector.example.com}).
         */
        public Builder baseUri(String baseUri) {
            this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
            return this;
        }

        /**
         * Sets an API Key for authentication via the {@code X-API-Key} header.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets a Bearer JWT token for authentication via the {@code Authorization: Bearer <token>} header.
         */
        public Builder bearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        /**
         * Adds a custom HTTP header to all outbound requests.
         */
        public Builder header(String name, String value) {
            Objects.requireNonNull(name, "header name must not be null");
            Objects.requireNonNull(value, "header value must not be null");
            this.customHeaders.put(name, value);
            return this;
        }

        /**
         * Sets connection establishment timeout.
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
            return this;
        }

        /**
         * Sets HTTP read timeout for requests.
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout must not be null");
            return this;
        }

        /**
         * Provides a custom {@link HttpClient}. If provided, the client will not be automatically closed.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.customHttpClient = httpClient;
            return this;
        }

        /**
         * Sets a custom request interceptor callback.
         */
        public Builder requestInterceptor(Consumer<HttpRequest.Builder> interceptor) {
            this.customInterceptor = interceptor;
            return this;
        }

        /**
         * Builds the configured {@link SpectorClient}.
         */
        public SpectorClient build() {
            boolean ownsHttpClient = (this.customHttpClient == null);
            HttpClient.Builder httpBuilder;
            HttpClient finalHttpClient;

            if (this.customHttpClient != null) {
                finalHttpClient = this.customHttpClient;
                httpBuilder = HttpClient.newBuilder();
            } else {
                httpBuilder = HttpClient.newBuilder();
                if (connectTimeout != null) {
                    httpBuilder.connectTimeout(connectTimeout);
                }
                finalHttpClient = httpBuilder.build();
            }

            ApiClient apiClient = new ApiClient(
                    HttpClient.newBuilder().connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(10)),
                    ApiClient.createDefaultObjectMapper(),
                    baseUri
            );

            // If customHttpClient was provided, configure ApiClient with builder wrapper
            if (customHttpClient != null) {
                apiClient.setHttpClientBuilder(HttpClient.newBuilder());
            }

            if (readTimeout != null) {
                apiClient.setReadTimeout(readTimeout);
            }
            if (connectTimeout != null) {
                apiClient.setConnectTimeout(connectTimeout);
            }

            final Map<String, String> headersSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(customHeaders));
            final String finalApiKey = this.apiKey;
            final String finalBearerToken = this.bearerToken;
            final Consumer<HttpRequest.Builder> userInterceptor = this.customInterceptor;

            apiClient.setRequestInterceptor(reqBuilder -> {
                if (finalApiKey != null && !finalApiKey.isBlank()) {
                    reqBuilder.header("X-API-Key", finalApiKey);
                }
                if (finalBearerToken != null && !finalBearerToken.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + finalBearerToken);
                }
                for (Map.Entry<String, String> entry : headersSnapshot.entrySet()) {
                    reqBuilder.header(entry.getKey(), entry.getValue());
                }
                if (userInterceptor != null) {
                    userInterceptor.accept(reqBuilder);
                }
            });

            return new SpectorClient(apiClient, finalHttpClient, ownsHttpClient);
        }
    }
}
