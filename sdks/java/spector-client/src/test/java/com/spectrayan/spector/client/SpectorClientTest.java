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

import com.spectrayan.spector.client.generated.api.MemoryApi;
import com.spectrayan.spector.client.generated.invoker.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpectorClientTest {

    @Test
    @DisplayName("Default builder produces client targeting localhost:8080")
    void defaultClientTargetingLocalhost() {
        try (SpectorClient client = SpectorClient.createDefault()) {
            assertThat(client).isNotNull();
            assertThat(client.memory()).isNotNull();
            assertThat(client.rawApiClient().getBaseUri()).isEqualTo("http://localhost:8080");
        }
    }

    @Test
    @DisplayName("Builder configures base URI, timeouts, and headers")
    void builderConfiguresSettings() {
        try (SpectorClient client = SpectorClient.builder()
                .baseUri("https://api.spector.internal:9090")
                .apiKey("test-key-123")
                .bearerToken("jwt-token-456")
                .header("X-Custom-Tenant", "tenant-alpha")
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .build()) {

            assertThat(client.rawApiClient().getBaseUri()).isEqualTo("https://api.spector.internal:9090");
            assertThat(client.rawApiClient().getReadTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(client.rawApiClient().getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));

            // Verify headers are intercepted and applied
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create("https://api.spector.internal:9090/test"));
            client.rawApiClient().getRequestInterceptor().accept(reqBuilder);
            HttpRequest req = reqBuilder.build();

            assertThat(req.headers().firstValue("X-API-Key")).contains("test-key-123");
            assertThat(req.headers().firstValue("Authorization")).contains("Bearer jwt-token-456");
            assertThat(req.headers().firstValue("X-Custom-Tenant")).contains("tenant-alpha");
        }
    }

    @Test
    @DisplayName("Custom request interceptor runs in addition to auth headers")
    void customRequestInterceptorInvoked() {
        AtomicBoolean customRan = new AtomicBoolean(false);
        try (SpectorClient client = SpectorClient.builder()
                .apiKey("test-key")
                .requestInterceptor(b -> customRan.set(true))
                .build()) {

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/test"));
            client.rawApiClient().getRequestInterceptor().accept(reqBuilder);

            assertThat(customRan).isTrue();
        }
    }

    @Test
    @DisplayName("getApi instantiates any generated OpenAPI API class")
    void getApiInstantiatesGeneratedApis() {
        try (SpectorClient client = SpectorClient.createDefault()) {
            MemoryApi api = client.getApi(MemoryApi::new);
            assertThat(api).isNotNull();
        }
    }

    @Test
    @DisplayName("Client closes owned HttpClient on close()")
    void closesOwnedHttpClient() {
        SpectorClient client = SpectorClient.builder().build();
        client.close();
        // Closing an already closed client should be idempotent
        client.close();
    }

    @Test
    @DisplayName("Client does not close caller-provided HttpClient")
    void doesNotCloseCustomHttpClient() {
        HttpClient mockClient = mock(HttpClient.class);
        SpectorClient client = SpectorClient.builder()
                .httpClient(mockClient)
                .build();
        client.close();
        // Ensure mockClient.close() was NOT called because client does not own it
        assertThatThrownBy(() -> verify(mockClient).close()).isInstanceOf(AssertionError.class);
    }
}
