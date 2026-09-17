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

import com.spectrayan.spector.cluster.routing.RoutingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingKeyExtractorTest {

    private final RoutingKeyExtractor extractor = new RoutingKeyExtractor();

    @Test
    @DisplayName("Extracts namespace and tenant from explicit headers")
    void testExtractFromHeaders() {
        Map<String, String> headers = Map.of(
                GatewayForwarder.HEADER_NAMESPACE, "ns-header",
                GatewayForwarder.HEADER_TENANT, "tenant-header"
        );

        RoutingKey key = extractor.extract("cell-1", headers::get, k -> null, "/api/v1/memory");
        assertThat(key.cellId()).isEqualTo("cell-1");
        assertThat(key.namespaceId()).isEqualTo("ns-header");
        assertThat(key.tenantId()).isEqualTo("tenant-header");
    }

    @Test
    @DisplayName("Extracts namespace and tenant from query parameters when headers absent")
    void testExtractFromQueryParams() {
        Map<String, String> params = Map.of(
                "namespace", "ns-query",
                "tenant", "tenant-query"
        );

        RoutingKey key = extractor.extract("cell-1", k -> null, params::get, "/api/v1/memory");
        assertThat(key.namespaceId()).isEqualTo("ns-query");
        assertThat(key.tenantId()).isEqualTo("tenant-query");
    }

    @Test
    @DisplayName("Extracts namespace from URI path regex pattern")
    void testExtractFromPathPattern() {
        RoutingKey key = extractor.extract("cell-1", k -> null, k -> null, "/api/v1/namespaces/team-alpha/reset");
        assertThat(key.namespaceId()).isEqualTo("team-alpha");
        assertThat(key.tenantId()).isNull();
    }

    @Test
    @DisplayName("Extracts sub and tenant_id from JWT token payload when not explicitly provided")
    void testExtractFromJwtToken() {
        String payloadJson = "{\"sub\":\"user-jwt\",\"tenant_id\":\"org-jwt\",\"exp\":1999999999}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String dummyJwt = "eyJhbGciOiJIUzI1NiJ9." + encodedPayload + ".signature";

        Map<String, String> headers = Map.of("Authorization", "Bearer " + dummyJwt);

        RoutingKey key = extractor.extract("cell-1", headers::get, k -> null, "/api/v1/memory");
        assertThat(key.namespaceId()).isEqualTo("user-jwt");
        assertThat(key.tenantId()).isEqualTo("org-jwt");
    }

    @Test
    @DisplayName("Falls back to default namespace when no identifiers are present")
    void testFallbackToDefault() {
        RoutingKey key = extractor.extract("cell-1", k -> null, k -> null, "/api/v1/memory");
        assertThat(key.namespaceId()).isEqualTo("default");
        assertThat(key.tenantId()).isNull();
    }
}
